package com.kb.timer.service;

import com.kb.timer.model.dto.TimerResponse;
import com.kb.timer.model.entity.TimestampEntry;
import com.kb.timer.model.event.*;
import com.kb.timer.repository.TimestampEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
    
    private final TimestampEntryRepository timestampRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final RedisConnectionManager connectionManager;
    
    @Value("${server.instance.id}")
    private String serverId;
    
    /**
     * 새로운 타이머를 생성하거나 기존 타이머의 타임스탬프를 저장합니다.
     *
     * @param timerId     타이머 ID
     * @param userId      사용자 ID
     * @param targetTime  목표 시간 (Instant)
     * @param metadata    추가 메타데이터
     * @return 저장된 TimestampEntry
     */
    public Mono<TimestampEntry> saveTimestamp(String timerId, String userId, Instant targetTime, Map<String, Object> metadata) {
        return timestampRepository.findByTimerIdAndUserIdOrderByCreatedAtDesc(timerId, userId)
                .next() // 가장 최근 것만 가져오기
                .defaultIfEmpty(TimestampEntry.builder()
                        .id(UUID.randomUUID().toString())
                        .timerId(timerId)
                        .userId(userId)
                        .build())
                .flatMap(existingEntry -> {
                    Instant now = Instant.now();
                    long remainingSeconds = targetTime.getEpochSecond() - now.getEpochSecond();

                    existingEntry.setSavedAt(now);
                    existingEntry.setRemainingTime(Duration.ofSeconds(remainingSeconds));
                    existingEntry.setTargetTime(targetTime);
                    existingEntry.setMetadata(metadata);
                    existingEntry.setCreatedAt(now); // 항상 최신 저장 시간으로 업데이트

                    return timestampRepository.save(existingEntry)
                            .flatMap(savedEntry -> {
                                // Kafka 이벤트 발행
                                TimestampSavedEvent event = TimestampSavedEvent.builder()
                                        .eventId(UUID.randomUUID().toString())
                                        .timerId(timerId)
                                        .userId(userId)
                                        .savedAt(now)
                                        .remainingTime(Duration.ofSeconds(remainingSeconds))
                                        .targetTime(targetTime)
                                        .metadata(metadata)
                                        .originServerId(serverId)
                                        .timestamp(now)
                                        .build();
                                return kafkaEventPublisher.publishTimerEvent(event)
                                        .thenReturn(savedEntry);
                            });
                })
                .doOnSuccess(entry -> log.info("Timestamp saved/updated for timerId: {}, userId: {}", timerId, userId))
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
                .then();
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
        TimerCompletedEvent event = TimerCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(timerId)
                .originServerId(serverId)
                .timestamp(Instant.now())
                .build();
        return kafkaEventPublisher.publishTimerEvent(event)
                .doOnSuccess(result -> log.info("TimerCompletedEvent published for timerId: {}", timerId))
                .doOnError(e -> log.error("Failed to publish TimerCompletedEvent for timerId: {}. Error: {}", timerId, e.getMessage(), e))
                .then();
    }
}