package com.kb.timer.service;

import com.kb.timer.model.entity.Timer;
import com.kb.timer.util.ServerInstanceIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 분산 환경 대응 타이머 스케줄러 서비스
 * Redis 분산 락을 사용하여 여러 서버 중 하나만 특정 타이머를 스케줄링하도록 함
 */
@Service
@Slf4j
public class DistributedTimerSchedulerService {

    private final TaskScheduler taskScheduler;
    private final TimerService timerService;
    private final ReactiveRedisTemplate<String, String> stringRedisTemplate;
    private final ServerInstanceIdGenerator serverInstanceIdGenerator;
    
    public DistributedTimerSchedulerService(@Qualifier("timerTaskScheduler") TaskScheduler taskScheduler,
                                          TimerService timerService,
                                          ReactiveRedisTemplate<String, String> stringRedisTemplate,
                                          ServerInstanceIdGenerator serverInstanceIdGenerator) {
        this.taskScheduler = taskScheduler;
        this.timerService = timerService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.serverInstanceIdGenerator = serverInstanceIdGenerator;
    }
    
    // 타이머 ID -> ScheduledFuture 매핑 (이 서버에서 스케줄링 중인 타이머들)
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    
    // 분산 락 TTL (타이머 완료 시간 + 여유시간)
    private static final Duration LOCK_TTL = Duration.ofHours(25); // 24시간 + 1시간 여유

    /**
     * 타이머 완료 스케줄 등록 (분산 락 사용)
     * 
     * @param timer 타이머 정보
     */
    public void scheduleTimerCompletion(Timer timer) {
        String timerId = timer.getId();
        Instant targetTime = timer.getTargetTime();
        Instant now = Instant.now();
        
        // 이미 지난 시간이거나 완료된 타이머는 스케줄링하지 않음
        if (targetTime.isBefore(now) || timer.isCompleted()) {
            log.debug("타이머 스케줄링 스킵: timerId={}, targetTime={}, completed={}", 
                    timerId, targetTime, timer.isCompleted());
            return;
        }
        
        // 분산 락 획득 시도
        tryAcquireSchedulingLock(timerId)
                .subscribe(lockAcquired -> {
                    if (lockAcquired) {
                        // 락 획득 성공 → 이 서버에서 스케줄링 담당
                        doScheduleTimerCompletion(timer);
                    } else {
                        // 락 획득 실패 → 다른 서버에서 이미 스케줄링 중
                        log.debug("타이머 스케줄링 락 획득 실패 (다른 서버에서 처리 중): timerId={}", timerId);
                    }
                });
    }

    /**
     * 타이머 완료 스케줄 업데이트
     * 
     * @param timer 업데이트된 타이머 정보
     */
    public void updateTimerSchedule(Timer timer) {
        String timerId = timer.getId();
        
        // 현재 이 서버에서 스케줄링 중인 타이머인지 확인
        if (scheduledTasks.containsKey(timerId)) {
            log.info("타이머 스케줄 업데이트 (이 서버 담당): timerId={}, newTargetTime={}", 
                    timerId, timer.getTargetTime());
            
            // 기존 스케줄 취소 후 새로 등록
            cancelScheduledTask(timerId);
            doScheduleTimerCompletion(timer);
        } else {
            log.debug("타이머 스케줄 업데이트 스킵 (다른 서버 담당): timerId={}", timerId);
        }
    }

    /**
     * 타이머 스케줄 취소
     * 
     * @param timerId 타이머 ID
     */
    public void cancelTimerSchedule(String timerId) {
        if (scheduledTasks.containsKey(timerId)) {
            log.info("타이머 스케줄 취소: timerId={}", timerId);
            cancelScheduledTask(timerId);
            releaseSchedulingLock(timerId);
        }
    }

    /**
     * 분산 락 획득 시도
     * 
     * @param timerId 타이머 ID
     * @return 락 획득 성공 여부
     */
    private Mono<Boolean> tryAcquireSchedulingLock(String timerId) {
        String lockKey = "timer:schedule:lock:" + timerId;
        String serverId = serverInstanceIdGenerator.getServerInstanceId();
        
        return stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, serverId, LOCK_TTL)
                .doOnNext(acquired -> {
                    if (acquired) {
                        log.info("타이머 스케줄링 락 획득 성공: timerId={}, serverId={}", timerId, serverId);
                    } else {
                        log.debug("타이머 스케줄링 락 획득 실패: timerId={}, serverId={}", timerId, serverId);
                    }
                })
                .onErrorReturn(false);
    }

    /**
     * 분산 락 해제
     * 
     * @param timerId 타이머 ID
     */
    private void releaseSchedulingLock(String timerId) {
        String lockKey = "timer:schedule:lock:" + timerId;
        
        stringRedisTemplate.delete(lockKey)
                .doOnNext(deleted -> log.debug("타이머 스케줄링 락 해제: timerId={}, deleted={}", timerId, deleted))
                .doOnError(error -> log.error("타이머 스케줄링 락 해제 실패: timerId={}, error={}", 
                        timerId, error.getMessage(), error))
                .subscribe();
    }

    /**
     * 실제 타이머 완료 스케줄링 수행
     * 
     * @param timer 타이머 정보
     */
    private void doScheduleTimerCompletion(Timer timer) {
        String timerId = timer.getId();
        Instant targetTime = timer.getTargetTime();
        Instant now = Instant.now();
        
        // 기존 스케줄이 있다면 취소
        cancelScheduledTask(timerId);
        
        log.info("타이머 완료 스케줄 등록 (분산 락 획득): timerId={}, targetTime={}, 남은시간={}초", 
                timerId, targetTime, targetTime.getEpochSecond() - now.getEpochSecond());
        
        // 새로운 스케줄 등록
        ScheduledFuture<?> scheduledTask = taskScheduler.schedule(
            () -> handleTimerCompletion(timerId),
            targetTime
        );
        
        scheduledTasks.put(timerId, scheduledTask);
        log.debug("타이머 스케줄 등록 완료: timerId={}, 총 스케줄 수={}", timerId, scheduledTasks.size());
    }

    /**
     * 특정 타이머의 스케줄된 작업 취소
     * 
     * @param timerId 타이머 ID
     */
    private void cancelScheduledTask(String timerId) {
        ScheduledFuture<?> existingTask = scheduledTasks.remove(timerId);
        if (existingTask != null && !existingTask.isDone()) {
            boolean cancelled = existingTask.cancel(false);
            log.debug("기존 타이머 스케줄 취소: timerId={}, cancelled={}", timerId, cancelled);
        }
    }

    /**
     * 타이머 완료 처리 핸들러
     * 
     * @param timerId 타이머 ID
     */
    private void handleTimerCompletion(String timerId) {
        log.info("⏰ 분산 스케줄된 타이머 완료 처리 시작: timerId={}", timerId);
        
        try {
            // 비동기로 타이머 완료 이벤트 발행
            timerService.publishTimerCompletedEvent(timerId)
                    .doOnSuccess(ignored -> {
                        log.info("✅ 분산 스케줄된 타이머 완료 처리 성공: timerId={}", timerId);
                        // 완료된 스케줄 및 락 정리
                        scheduledTasks.remove(timerId);
                        releaseSchedulingLock(timerId);
                    })
                    .doOnError(error -> {
                        log.error("❌ 분산 스케줄된 타이머 완료 처리 실패: timerId={}, error={}", 
                                timerId, error.getMessage(), error);
                        // 실패한 스케줄도 정리
                        scheduledTasks.remove(timerId);
                        releaseSchedulingLock(timerId);
                    })
                    .subscribe();
                    
        } catch (Exception e) {
            log.error("❌ 분산 스케줄된 타이머 완료 처리 중 예외 발생: timerId={}, error={}", 
                    timerId, e.getMessage(), e);
            scheduledTasks.remove(timerId);
            releaseSchedulingLock(timerId);
        }
    }

    /**
     * 현재 이 서버에서 스케줄된 타이머 수 조회
     * 
     * @return 스케줄된 타이머 수
     */
    public int getScheduledTaskCount() {
        return scheduledTasks.size();
    }

    /**
     * 특정 타이머가 이 서버에서 스케줄되어 있는지 확인
     * 
     * @param timerId 타이머 ID
     * @return 스케줄 여부
     */
    public boolean isTimerScheduled(String timerId) {
        ScheduledFuture<?> task = scheduledTasks.get(timerId);
        return task != null && !task.isDone();
    }

    /**
     * 모든 스케줄 및 락 정리 (서버 종료 시)
     */
    public void cancelAllSchedules() {
        log.info("모든 타이머 스케줄 및 락 정리 시작: 총 {}개", scheduledTasks.size());
        
        scheduledTasks.forEach((timerId, task) -> {
            if (!task.isDone()) {
                task.cancel(false);
                log.debug("타이머 스케줄 정리: timerId={}", timerId);
            }
            // 락도 해제
            releaseSchedulingLock(timerId);
        });
        
        scheduledTasks.clear();
        log.info("모든 타이머 스케줄 및 락 정리 완료");
    }
}
