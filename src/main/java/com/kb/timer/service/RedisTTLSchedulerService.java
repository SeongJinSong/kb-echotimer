package com.kb.timer.service;

import com.kb.timer.model.entity.Timer;
import com.kb.timer.model.entity.TimerCompletionLog;
import com.kb.timer.model.event.TimerScheduleEvent;
import com.kb.timer.model.event.TimerCompletionEvent;
import com.kb.timer.repository.TimerCompletionLogRepository;
import com.kb.timer.repository.TimerRepository;
import com.kb.timer.util.ServerInstanceIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;

/**
 * Redis TTL + Keyspace Notifications 기반 타이머 스케줄러
 * Redis 키 만료 이벤트를 수신하여 타이머 완료 처리
 */
@Service
@Slf4j
public class RedisTTLSchedulerService extends KeyExpirationEventMessageListener {

    private final ReactiveRedisTemplate<String, String> stringRedisTemplate;
    private final ServerInstanceIdGenerator serverInstanceIdGenerator;
    private final TimerCompletionLogRepository completionLogRepository;
    private final TimerRepository timerRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    // Redis 키 상수
    private static final String TIMER_SCHEDULE_PREFIX = "timer:schedule:";
    private static final String PROCESSING_LOCK_PREFIX = "timer:processing:";
    
    public RedisTTLSchedulerService(RedisMessageListenerContainer listenerContainer,
                                   ReactiveRedisTemplate<String, String> stringRedisTemplate,
                                   ServerInstanceIdGenerator serverInstanceIdGenerator,
                                   TimerCompletionLogRepository completionLogRepository,
                                   TimerRepository timerRepository,
                                   ApplicationEventPublisher eventPublisher) {
        super(listenerContainer);
        this.stringRedisTemplate = stringRedisTemplate;
        this.serverInstanceIdGenerator = serverInstanceIdGenerator;
        this.completionLogRepository = completionLogRepository;
        this.timerRepository = timerRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        log.info("Redis TTL 스케줄러 서비스 시작");
        // KeyExpirationEventMessageListener가 자동으로 만료 이벤트 구독
    }

    /**
     * 타이머 스케줄을 Redis TTL로 등록
     * 
     * @param timer 타이머 정보
     */
    public void scheduleTimer(Timer timer) {
        String timerId = timer.getId();
        Instant targetTime = timer.getTargetTime();
        Instant now = Instant.now();
        
        // 이미 지난 시간이거나 완료된 타이머는 스케줄링하지 않음
        if (targetTime.isBefore(now) || timer.isCompleted()) {
            log.debug("타이머 스케줄링 스킵: timerId={}, targetTime={}, completed={}", 
                    timerId, targetTime, timer.isCompleted());
            return;
        }
        
        Duration ttl = Duration.between(now, targetTime);
        String scheduleKey = TIMER_SCHEDULE_PREFIX + timerId;
        
        // TTL 설정하여 키 등록 (만료 시 자동으로 이벤트 발생)
        stringRedisTemplate.opsForValue()
                .set(scheduleKey, timerId, ttl)
                .doOnNext(success -> {
                    if (success) {
                        log.info("타이머 TTL 스케줄 등록: timerId={}, targetTime={}, ttl={}초", 
                                timerId, targetTime, ttl.getSeconds());
                    } else {
                        log.warn("타이머 TTL 스케줄 등록 실패: timerId={}", timerId);
                    }
                })
                .doOnError(error -> log.error("타이머 TTL 스케줄 등록 오류: timerId={}, error={}", 
                        timerId, error.getMessage(), error))
                .subscribe();
    }

    /**
     * 타이머 스케줄 업데이트
     * 기존 TTL 키를 삭제하고 새로운 TTL로 재등록
     * 
     * @param timer 업데이트된 타이머 정보
     */
    public void updateTimerSchedule(Timer timer) {
        String timerId = timer.getId();
        String scheduleKey = TIMER_SCHEDULE_PREFIX + timerId;
        
        log.info("타이머 TTL 스케줄 업데이트: timerId={}, newTargetTime={}", 
                timerId, timer.getTargetTime());
        
        // 기존 스케줄 삭제 후 새로 등록
        stringRedisTemplate.delete(scheduleKey)
                .doOnNext(deleted -> {
                    if (deleted > 0) {
                        log.debug("기존 TTL 스케줄 삭제: timerId={}", timerId);
                    }
                    // 새로운 스케줄 등록
                    scheduleTimer(timer);
                })
                .subscribe();
    }

    /**
     * 타이머 스케줄 취소
     * TTL 키를 삭제하여 만료 이벤트 방지
     * 
     * @param timerId 타이머 ID
     */
    public void cancelTimerSchedule(String timerId) {
        String scheduleKey = TIMER_SCHEDULE_PREFIX + timerId;
        
        log.info("타이머 TTL 스케줄 취소: timerId={}", timerId);
        
        stringRedisTemplate.delete(scheduleKey)
                .doOnNext(deleted -> {
                    if (deleted > 0) {
                        log.debug("TTL 스케줄 삭제 완료: timerId={}", timerId);
                    } else {
                        log.debug("삭제할 TTL 스케줄이 없음: timerId={}", timerId);
                    }
                })
                .doOnError(error -> log.error("TTL 스케줄 삭제 오류: timerId={}, error={}", 
                        timerId, error.getMessage(), error))
                .subscribe();
    }

    /**
     * Redis 키 만료 이벤트 처리
     * KeyExpirationEventMessageListener에서 자동 호출
     * 
     * @param message 만료된 키 정보
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        
        // 타이머 스케줄 키인지 확인
        if (!expiredKey.startsWith(TIMER_SCHEDULE_PREFIX)) {
            return; // 다른 키의 만료 이벤트는 무시
        }
        
        // 타이머 ID 추출
        String timerId = expiredKey.substring(TIMER_SCHEDULE_PREFIX.length());
        Instant notificationReceivedAt = Instant.now();
        
        log.info("⏰ Redis TTL 만료 이벤트 수신: timerId={}, expiredKey={}", timerId, expiredKey);
        
        // 타이머 정보 조회 후 완료 로그 생성 및 처리
        timerRepository.findById(timerId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("타이머를 찾을 수 없음: timerId={}", timerId);
                    createCompletionLog(timerId, null, notificationReceivedAt, false, 
                            false, "타이머를 찾을 수 없음", null).subscribe();
                    return Mono.empty();
                }))
                .flatMap(timer -> {
                    // 완료 로그 초기 생성
                    TimerCompletionLog initialLog = TimerCompletionLog.builder()
                            .timerId(timerId)
                            .serverId(serverInstanceIdGenerator.getServerInstanceId())
                            .notificationReceivedAt(notificationReceivedAt)
                            .originalTargetTime(timer.getTargetTime())
                            .build();
                    
                    return completionLogRepository.save(initialLog)
                            .flatMap(savedLog -> processTimerCompletionWithLogging(timerId, timer, savedLog));
                })
                .doOnError(error -> log.error("❌ TTL 만료 타이머 처리 실패: timerId={}, error={}", 
                        timerId, error.getMessage(), error))
                .subscribe();
    }

    /**
     * 로깅과 함께 타이머 완료 처리
     * 
     * @param timerId 타이머 ID
     * @param timer 타이머 정보
     * @param completionLog 완료 로그
     * @return 처리 결과
     */
    private Mono<Void> processTimerCompletionWithLogging(String timerId, Timer timer, TimerCompletionLog completionLog) {
        String lockKey = PROCESSING_LOCK_PREFIX + timerId;
        String serverId = serverInstanceIdGenerator.getServerInstanceId();
        Instant processingStartedAt = Instant.now();
        
        // 분산 락 획득 시도 (5분 TTL)
        return stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, serverId, Duration.ofMinutes(5))
                .flatMap(lockAcquired -> {
                    // 로그 업데이트: 처리 시작 및 락 획득 상태
                    completionLog.setProcessingStartedAt(processingStartedAt);
                    completionLog.setLockAcquired(lockAcquired);
                    
                    if (timer.getTargetTime() != null) {
                        long delayMs = Duration.between(timer.getTargetTime(), processingStartedAt).toMillis();
                        completionLog.setProcessingDelayMs(delayMs);
                    }
                    
                    if (lockAcquired) {
                        log.info("✅ TTL 만료 타이머 처리 시작 (락 획득): timerId={}, serverId={}", timerId, serverId);
                        
                        // 타이머 완료 이벤트 발행 (동일 서버 내 TimerService에서 처리)
                        eventPublisher.publishEvent(new TimerCompletionEvent(this, timerId));
                        log.info("✅ TTL 만료 타이머 완료 이벤트 발행: timerId={}", timerId);
                        return Mono.<Void>empty()
                                .doOnSuccess(v -> {
                                    // 성공 로그 업데이트
                                    completionLog.setProcessingCompletedAt(Instant.now());
                                    completionLog.setSuccess(true);
                                    completionLogRepository.save(completionLog).subscribe();
                                    log.info("✅ TTL 만료 타이머 완료 처리 성공: timerId={}", timerId);
                                })
                                .doOnError(error -> {
                                    // 실패 로그 업데이트
                                    completionLog.setProcessingCompletedAt(Instant.now());
                                    completionLog.setSuccess(false);
                                    completionLog.setErrorMessage(error.getMessage());
                                    completionLogRepository.save(completionLog).subscribe();
                                    log.error("❌ TTL 만료 타이머 완료 처리 실패: timerId={}, error={}", timerId, error.getMessage());
                                })
                                .doFinally(signalType -> {
                                    // 처리 완료 후 락 해제
                                    stringRedisTemplate.delete(lockKey)
                                            .doOnNext(deleted -> log.debug("처리 락 해제: timerId={}, deleted={}", timerId, deleted))
                                            .subscribe();
                                });
                    } else {
                        log.debug("TTL 만료 타이머 처리 스킵 (다른 서버에서 처리 중): timerId={}", timerId);
                        
                        // 락 획득 실패 로그 업데이트
                        completionLog.setProcessingCompletedAt(Instant.now());
                        completionLog.setSuccess(false);
                        completionLog.setErrorMessage("분산 락 획득 실패 - 다른 서버에서 처리 중");
                        
                        return completionLogRepository.save(completionLog).then();
                    }
                });
    }

    /**
     * 완료 로그 생성 헬퍼 메서드
     */
    private Mono<Void> createCompletionLog(String timerId, Timer timer, Instant notificationReceivedAt, 
                                          boolean lockAcquired, boolean success, String errorMessage, 
                                          Instant processingCompletedAt) {
        TimerCompletionLog log = TimerCompletionLog.builder()
                .timerId(timerId)
                .serverId(serverInstanceIdGenerator.getServerInstanceId())
                .notificationReceivedAt(notificationReceivedAt)
                .processingStartedAt(Instant.now())
                .processingCompletedAt(processingCompletedAt != null ? processingCompletedAt : Instant.now())
                .lockAcquired(lockAcquired)
                .success(success)
                .errorMessage(errorMessage)
                .originalTargetTime(timer != null ? timer.getTargetTime() : null)
                .build();
        
        if (timer != null && timer.getTargetTime() != null) {
            long delayMs = Duration.between(timer.getTargetTime(), log.getProcessingStartedAt()).toMillis();
            log.setProcessingDelayMs(delayMs);
        }
        
        return completionLogRepository.save(log).then();
    }

    /**
     * 분산 락을 사용하여 타이머 완료 처리 (기존 메서드 - 호환성 유지)
     * 
     * @param timerId 타이머 ID
     * @return 처리 결과
     */
    private Mono<Void> processTimerCompletionWithLock(String timerId) {
        String lockKey = PROCESSING_LOCK_PREFIX + timerId;
        String serverId = serverInstanceIdGenerator.getServerInstanceId();
        
        // 분산 락 획득 시도 (5분 TTL)
        return stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, serverId, Duration.ofMinutes(5))
                .flatMap(lockAcquired -> {
                    if (lockAcquired) {
                        log.info("✅ TTL 만료 타이머 처리 시작 (락 획득): timerId={}, serverId={}", timerId, serverId);
                        
                        // 타이머 완료 이벤트 발행 (동일 서버 내 TimerService에서 처리)
                        eventPublisher.publishEvent(new TimerCompletionEvent(this, timerId));
                        log.info("✅ TTL 만료 타이머 완료 이벤트 발행: timerId={}", timerId);
                        return Mono.<Void>empty()
                                .doOnSuccess(v -> log.info("✅ TTL 만료 타이머 완료 처리 성공: timerId={}", timerId))
                                .doFinally(signalType -> {
                                    // 처리 완료 후 락 해제
                                    stringRedisTemplate.delete(lockKey)
                                            .doOnNext(deleted -> log.debug("처리 락 해제: timerId={}, deleted={}", timerId, deleted))
                                            .subscribe();
                                });
                    } else {
                        log.debug("TTL 만료 타이머 처리 스킵 (다른 서버에서 처리 중): timerId={}", timerId);
                        return Mono.empty();
                    }
                });
    }

    /**
     * 특정 타이머가 스케줄되어 있는지 확인
     * 
     * @param timerId 타이머 ID
     * @return 스케줄 여부
     */
    public Mono<Boolean> isTimerScheduled(String timerId) {
        String scheduleKey = TIMER_SCHEDULE_PREFIX + timerId;
        return stringRedisTemplate.hasKey(scheduleKey)
                .doOnNext(exists -> log.debug("타이머 TTL 스케줄 존재 여부: timerId={}, exists={}", timerId, exists));
    }

    /**
     * 현재 스케줄된 타이머 수 조회
     * 
     * @return 스케줄된 타이머 수
     */
    public Mono<Long> getScheduledTimerCount() {
        String pattern = TIMER_SCHEDULE_PREFIX + "*";
        return stringRedisTemplate.keys(pattern)
                .count()
                .doOnNext(count -> log.debug("현재 TTL 스케줄된 타이머 수: {}", count));
    }

    /**
     * 타이머 스케줄 이벤트 리스너
     * TimerService에서 발행하는 스케줄 관련 이벤트를 처리
     */
    @EventListener
    public void handleTimerScheduleEvent(TimerScheduleEvent event) {
        switch (event.getType()) {
            case SCHEDULE:
                if (event.getTimer() != null) {
                    scheduleTimer(event.getTimer());
                }
                break;
            case UPDATE:
                if (event.getTimer() != null) {
                    updateTimerSchedule(event.getTimer());
                }
                break;
            case CANCEL:
                cancelTimerSchedule(event.getTimerId());
                break;
        }
    }

    /**
     * 서비스 종료 시 정리
     */
    @PreDestroy
    public void shutdown() {
        log.info("Redis TTL 스케줄러 서비스 종료");
        // TTL 기반이므로 특별한 정리 작업 불필요
        // Redis가 자동으로 만료된 키들을 정리함
    }
}
