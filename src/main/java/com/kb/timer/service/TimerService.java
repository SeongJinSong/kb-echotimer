package com.kb.timer.service;

import com.kb.timer.model.dto.TimerResponse;
import com.kb.timer.model.entity.Timer;
import com.kb.timer.model.entity.TimestampEntry;
import com.kb.timer.model.event.*;
import com.kb.timer.repository.TimerRepository;
import com.kb.timer.repository.TimestampEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 타이머 핵심 서비스
 * 타이머 생성, 관리, 타임스탬프 저장 등의 핵심 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimerService {

    private final TimerRepository timerRepository;
    private final TimestampEntryRepository timestampRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final RedisConnectionManager connectionManager;
    private final SimpMessagingTemplate messagingTemplate;
    
    @Value("${server.instance.id}")
    private String serverId;

    /**
     * 새로운 타이머 생성
     * @param targetTimeSeconds 목표 시간 (초)
     * @param ownerId 소유자 ID
     * @return 타이머 정보
     */
    public Mono<TimerResponse> createTimer(long targetTimeSeconds, String ownerId) {
        String timerId = UUID.randomUUID().toString();
        String shareToken = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant targetTime = now.plusSeconds(targetTimeSeconds);

        log.info("새 타이머 생성: {} (목표: {}초, 소유자: {})", timerId, targetTimeSeconds, ownerId);

        Timer timer = Timer.builder()
                .id(timerId)
                .ownerId(ownerId)
                .targetTime(targetTime)
                .createdAt(now)
                .updatedAt(now)
                .completed(false)
                .shareToken(shareToken)
                .build();

        return timerRepository.save(timer)
                .map(savedTimer -> {
                    // 남은 시간 계산 (음수가 되지 않도록 처리)
                    Duration remainingTime = Duration.between(now, savedTimer.getTargetTime());
                    if (remainingTime.isNegative()) {
                        remainingTime = Duration.ZERO;
                    }
                    
                    return TimerResponse.builder()
                            .timerId(savedTimer.getId())
                            .targetTime(savedTimer.getTargetTime())
                            .serverTime(now)
                            .remainingTime(remainingTime)
                            .completed(savedTimer.isCompleted() || remainingTime.isZero())
                            .ownerId(savedTimer.getOwnerId())
                            .onlineUserCount(0) // 새로 생성된 타이머는 아직 접속자 없음
                            .shareToken(generateShareUrl(savedTimer.getShareToken()))
                            .userRole("OWNER")
                            .build();
                });
    }

    /**
     * 특정 타이머의 정보를 조회합니다.
     * @param timerId 타이머 ID
     * @param userId 요청 사용자 ID (권한 확인용)
     * @return 타이머 정보
     */
    public Mono<TimerResponse> getTimerInfo(String timerId, String userId) {
        log.debug("타이머 정보 조회: {}", timerId);
        Instant now = Instant.now();
        
        return timerRepository.findById(timerId)
                .switchIfEmpty(Mono.error(new RuntimeException("타이머를 찾을 수 없습니다: " + timerId)))
                .flatMap(timer -> {
                    // 온라인 사용자 수 조회 (Redis)
                    return connectionManager.getOnlineUserCount(timerId)
                            .defaultIfEmpty(0L)
                            .map(onlineCount -> {
                                // 남은 시간 계산 (음수가 되지 않도록 처리)
                                Duration remainingTime = Duration.between(now, timer.getTargetTime());
                                boolean isCompleted = timer.isCompleted() || remainingTime.isNegative() || remainingTime.isZero();
                                if (remainingTime.isNegative()) {
                                    remainingTime = Duration.ZERO;
                                }
                                
                                return TimerResponse.builder()
                                        .timerId(timer.getId())
                                        .userId(userId)
                                        .targetTime(timer.getTargetTime())
                                        .serverTime(now)
                                        .remainingTime(remainingTime)
                                        .completed(isCompleted)
                                        .ownerId(timer.getOwnerId())
                                        .onlineUserCount(onlineCount.intValue())
                                        .shareToken(generateShareUrl(timer.getShareToken()))
                                        .userRole(timer.getOwnerId().equals(userId) ? "OWNER" : "VIEWER")
                                        .build();
                            });
                });
    }

    /**
     * 타이머의 목표 시간을 변경합니다.
     * @param timerId 타이머 ID
     * @param newTargetTime 새로운 목표 시간
     * @param changedBy 변경한 사용자 ID
     * @return 변경된 타이머 정보
     */
    public Mono<TimerResponse> changeTargetTime(String timerId, Instant newTargetTime, String changedBy) {
        log.info("타이머 목표 시간 변경: {} - 새로운 목표: {} (변경자: {})",
                timerId, newTargetTime, changedBy);

        return timerRepository.findById(timerId)
                .switchIfEmpty(Mono.error(new RuntimeException("타이머를 찾을 수 없습니다: " + timerId)))
                .flatMap(timer -> {
                    // 권한 확인 (소유자만 변경 가능)
                    if (!timer.getOwnerId().equals(changedBy)) {
                        return Mono.error(new RuntimeException("타이머 소유자만 목표 시간을 변경할 수 있습니다."));
                    }

                    Instant oldTargetTime = timer.getTargetTime();
                    Instant now = Instant.now();
                    
                    // 타이머 업데이트
                    timer.setTargetTime(newTargetTime);
                    timer.setUpdatedAt(now);
                    
                    return timerRepository.save(timer)
                            .flatMap(updatedTimer -> {
                                // 이벤트 발행
                                TargetTimeChangedEvent event = TargetTimeChangedEvent.builder()
                                        .eventId(UUID.randomUUID().toString())
                                        .timerId(timerId)
                                        .newTargetTime(newTargetTime)
                                        .oldTargetTime(oldTargetTime)
                                        .changedBy(changedBy)
                                        .originServerId(serverId)
                                        .timestamp(now)
                                        .build();

                                return kafkaEventPublisher.publishTimerEvent(event)
                                        .then(connectionManager.getOnlineUserCount(timerId))
                                        .defaultIfEmpty(0L)
                                        .map(onlineCount -> {
                                            // 남은 시간 계산 (음수가 되지 않도록 처리)
                                            Duration remainingTime = Duration.between(now, updatedTimer.getTargetTime());
                                            boolean isCompleted = updatedTimer.isCompleted() || remainingTime.isNegative() || remainingTime.isZero();
                                            if (remainingTime.isNegative()) {
                                                remainingTime = Duration.ZERO;
                                            }
                                            
                                            return TimerResponse.builder()
                                                    .timerId(updatedTimer.getId())
                                                    .targetTime(updatedTimer.getTargetTime())
                                                    .serverTime(now)
                                                    .remainingTime(remainingTime)
                                                    .completed(isCompleted)
                                                    .ownerId(updatedTimer.getOwnerId())
                                                    .onlineUserCount(onlineCount.intValue())
                                                    .shareToken(generateShareUrl(updatedTimer.getShareToken()))
                                                    .userRole("OWNER")
                                                    .build();
                                        });
                            });
                });
    }

    /**
     * 공유 토큰으로 타이머 정보를 조회합니다.
     * @param shareToken 공유 토큰
     * @param userId 요청 사용자 ID
     * @return 타이머 정보
     */
    public Mono<TimerResponse> getTimerInfoByShareToken(String shareToken, String userId) {
        log.debug("공유 토큰으로 타이머 정보 조회: {}", shareToken);
        Instant now = Instant.now();
        
        return timerRepository.findByShareToken(shareToken)
                .switchIfEmpty(Mono.error(new RuntimeException("유효하지 않은 공유 링크입니다: " + shareToken)))
                .flatMap(timer -> {
                    // 온라인 사용자 수 조회 (Redis)
                    return connectionManager.getOnlineUserCount(timer.getId())
                            .defaultIfEmpty(0L)
                            .map(onlineCount -> {
                                // 남은 시간 계산 (음수가 되지 않도록 처리)
                                Duration remainingTime = Duration.between(now, timer.getTargetTime());
                                boolean isCompleted = timer.isCompleted() || remainingTime.isNegative() || remainingTime.isZero();
                                if (remainingTime.isNegative()) {
                                    remainingTime = Duration.ZERO;
                                }
                                
                                return TimerResponse.builder()
                                        .timerId(timer.getId())
                                        .userId(userId)
                                        .targetTime(timer.getTargetTime())
                                        .serverTime(now)
                                        .remainingTime(remainingTime)
                                        .completed(isCompleted)
                                        .ownerId(timer.getOwnerId())
                                        .onlineUserCount(onlineCount.intValue())
                                        .shareToken(generateShareUrl(timer.getShareToken()))
                                        .userRole(timer.getOwnerId().equals(userId) ? "OWNER" : "VIEWER")
                                        .build();
                            });
                });
    }

    /**
     * 특정 타이머의 모든 타임스탬프 기록을 조회합니다. (현재 시각 기준 오름차순)
     * @param timerId 타이머 ID
     * @return 타임스탬프 엔트리 목록
     */
    public Flux<TimestampEntry> getTimerHistory(String timerId) {
        log.debug("타임스탬프 목록 조회: {}", timerId);
        return timestampRepository.findByTimerIdOrderByCreatedAtAsc(timerId)
                .doOnNext(entry -> log.debug("타임스탬프 조회: {} - 사용자: {}, 시간: {}", 
                        entry.getTimerId(), entry.getUserId(), entry.getTargetTime()))
                .doOnError(e -> log.error("타임스탬프 목록 조회 실패: timerId={}. Error: {}", timerId, e.getMessage(), e));
    }

    /**
     * 공유 URL 생성
     * @param shareToken 공유 토큰
     * @return 공유 URL
     */
    private String generateShareUrl(String shareToken) {
        return "/timer/" + shareToken;
    }
    
    /**
     * 새로운 타임스탬프를 저장합니다. (매번 새로운 엔트리 생성)
     *
     * @param timerId     타이머 ID
     * @param userId      사용자 ID
     * @param targetTime  목표 시간 (Instant)
     * @param metadata    추가 메타데이터
     * @return 저장된 TimestampEntry
     */
    public Mono<TimestampEntry> saveTimestamp(String timerId, String userId, Instant targetTime, Map<String, Object> metadata) {
        Instant now = Instant.now();
        // 밀리초 단위로 남은 시간 계산
        long remainingMillis = targetTime.toEpochMilli() - now.toEpochMilli();
        Duration remainingTime = Duration.ofMillis(Math.max(0, remainingMillis)); // 음수 방지

        // 매번 새로운 타임스탬프 엔트리 생성
        TimestampEntry newEntry = TimestampEntry.builder()
                .id(UUID.randomUUID().toString())
                .timerId(timerId)
                .userId(userId)
                .savedAt(now)
                .remainingTime(remainingTime)
                .targetTime(targetTime)
                .metadata(metadata)
                .createdAt(now)
                .build();

        return timestampRepository.save(newEntry)
                .flatMap(savedEntry -> {
                    // Kafka 이벤트 발행
                    TimestampSavedEvent event = TimestampSavedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .timerId(timerId)
                            .userId(userId)
                            .savedAt(now)
                            .remainingTime(remainingTime)
                            .targetTime(targetTime)
                            .metadata(metadata)
                            .originServerId(serverId)
                            .timestamp(now)
                            .build();
                    return kafkaEventPublisher.publishTimerEvent(event)
                            .thenReturn(savedEntry);
                })
                .doOnSuccess(entry -> log.info("New timestamp saved for timerId: {}, userId: {}, entryId: {}, remainingTime: {}ms", 
                        timerId, userId, entry.getId(), remainingTime.toMillis()))
                .doOnError(e -> log.error("Failed to save timestamp for timerId: {}, userId: {}. Error: {}", timerId, userId, e.getMessage(), e));
    }

    /**
     * 사용자가 타이머에 참여했음을 알리는 이벤트를 발행합니다.
     *
     * @param timerId 타이머 ID
     * @param userId  사용자 ID
     * @return 발행 결과
     */
    public Mono<Void> publishUserJoinedEvent(String timerId, String userId) {
        UserJoinedEvent event = UserJoinedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(timerId)
                .userId(userId)
                .originServerId(serverId)
                .timestamp(Instant.now())
                .build();
        
        return kafkaEventPublisher.publishUserActionEvent(event)
                .doOnSuccess(result -> log.info("UserJoinedEvent published for timerId: {}, userId: {}", timerId, userId))
                .doOnError(e -> log.error("Failed to publish UserJoinedEvent for timerId: {}, userId: {}. Error: {}", timerId, userId, e.getMessage(), e))
                .then(broadcastOnlineUserCountUpdate(timerId)); // 온라인 사용자 수 업데이트 브로드캐스트
    }

    /**
     * 온라인 사용자 수 업데이트를 모든 클라이언트에게 브로드캐스트
     * 
     * @param timerId 타이머 ID
     * @return 브로드캐스트 결과
     */
    private Mono<Void> broadcastOnlineUserCountUpdate(String timerId) {
        return connectionManager.getOnlineUserCount(timerId)
                .defaultIfEmpty(0L)
                .flatMap(onlineCount -> {
                    log.info("온라인 사용자 수 브로드캐스트: timerId={}, count={}", timerId, onlineCount);
                    
                    // WebSocket을 통해 모든 구독자에게 온라인 사용자 수 업데이트 전송
                    Map<String, Object> updateMessage = Map.of(
                        "eventType", "ONLINE_USER_COUNT_UPDATED",
                        "timerId", timerId,
                        "onlineUserCount", onlineCount.intValue(),
                        "timestamp", Instant.now().toString()
                    );
                    
                    try {
                        messagingTemplate.convertAndSend("/topic/timer/" + timerId, updateMessage);
                        log.info("✅ 온라인 사용자 수 브로드캐스트 전송 완료: /topic/timer/{}, message={}", timerId, updateMessage);
                    } catch (Exception e) {
                        log.error("❌ 온라인 사용자 수 브로드캐스트 전송 실패: timerId={}, error={}", timerId, e.getMessage(), e);
                    }
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 사용자가 타이머에서 나갔음을 알리는 이벤트를 발행합니다.
     *
     * @param timerId 타이머 ID
     * @param userId  사용자 ID
     * @return 발행 결과
     */
    public Mono<Void> publishUserLeftEvent(String timerId, String userId) {
        UserLeftEvent event = UserLeftEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(timerId)
                .userId(userId)
                .originServerId(serverId)
                .timestamp(Instant.now())
                .build();
        return kafkaEventPublisher.publishUserActionEvent(event)
                .doOnSuccess(result -> log.info("UserLeftEvent published for timerId: {}, userId: {}", timerId, userId))
                .doOnError(e -> log.error("Failed to publish UserLeftEvent for timerId: {}, userId: {}. Error: {}", timerId, userId, e.getMessage(), e))
                .then(broadcastOnlineUserCountUpdate(timerId)); // 온라인 사용자 수 업데이트 브로드캐스트
    }

    /**
     * 목표 시간 변경 이벤트를 발행합니다.
     *
     * @param event 목표 시간 변경 이벤트
     * @return 발행 결과
     */
    public Mono<Void> publishTargetTimeChangedEvent(TargetTimeChangedEvent event) {
        return kafkaEventPublisher.publishTimerEvent(event)
                .doOnSuccess(result -> log.info("TargetTimeChangedEvent published for timerId: {}", event.getTimerId()))
                .doOnError(e -> log.error("Failed to publish TargetTimeChangedEvent for timerId: {}. Error: {}", event.getTimerId(), e.getMessage(), e))
                .then();
    }

    /**
     * 타이머 완료 이벤트를 발행합니다.
     *
     * @param timerId 타이머 ID
     * @return 발행 결과
     */
    public Mono<Void> publishTimerCompletedEvent(String timerId) {
        return timerRepository.findById(timerId)
                .switchIfEmpty(Mono.error(new RuntimeException("타이머를 찾을 수 없습니다: " + timerId)))
                .flatMap(timer -> {
                    // 타이머를 완료 상태로 업데이트
                    timer.setCompleted(true);
                    timer.setUpdatedAt(Instant.now());
                    
                    return timerRepository.save(timer)
                            .flatMap(savedTimer -> {
                                // 온라인 사용자 수 조회
                                return connectionManager.getOnlineUserCount(timerId)
                                        .defaultIfEmpty(0L)
                                        .flatMap(onlineCount -> {
                                            // 완료 이벤트 생성 및 발행
                                            TimerCompletedEvent event = TimerCompletedEvent.builder()
                                                    .eventId(UUID.randomUUID().toString())
                                                    .timerId(timerId)
                                                    .originServerId(serverId)
                                                    .timestamp(Instant.now())
                                                    .completedTargetTime(savedTimer.getTargetTime())
                                                    .ownerId(savedTimer.getOwnerId())
                                                    .onlineUserCount(onlineCount.intValue())
                                                    .build();
                                            
                                            return kafkaEventPublisher.publishTimerEvent(event)
                                                    .doOnSuccess(result -> log.info("TimerCompletedEvent published for timerId: {}", timerId))
                                                    .doOnError(e -> log.error("Failed to publish TimerCompletedEvent for timerId: {}. Error: {}", timerId, e.getMessage(), e));
                                        });
                            });
                })
                .then();
    }
}