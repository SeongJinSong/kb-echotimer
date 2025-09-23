package com.kb.timer.service;

import com.kb.timer.model.dto.TimerResponse;
import com.kb.timer.model.entity.TimestampEntry;
import com.kb.timer.model.event.*;
import com.kb.timer.repository.TimestampEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    
    private final TimestampEntryRepository timestampRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final RedisConnectionManager connectionManager;
    
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
        Instant now = Instant.now();
        Instant targetTime = now.plusSeconds(targetTimeSeconds);
        
        log.info("새 타이머 생성: {} (목표: {}초, 소유자: {})", timerId, targetTimeSeconds, ownerId);
        
        TimerResponse response = TimerResponse.builder()
                .timerId(timerId)
                .targetTime(targetTime)
                .serverTime(now)
                .remainingTime(Duration.ofSeconds(targetTimeSeconds))
                .completed(false)
                .ownerId(ownerId)
                .onlineUserCount(0)
                .shareToken(generateShareUrl(timerId))
                .userRole("OWNER")
                .build();
        
        return Mono.just(response);
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
        // TODO: 실제 타이머 정보 (예: 목표 시간, 남은 시간, 온라인 사용자 수 등)를 MongoDB 또는 Redis에서 조회하여 반환
        // 현재는 더미 데이터 반환
        return Mono.just(TimerResponse.builder()
                .timerId(timerId)
                .userId(userId)
                .targetTime(now.plusSeconds(60)) // 예시: 60초 남음
                .serverTime(now)
                .remainingTime(Duration.ofSeconds(60))
                .completed(false)
                .ownerId("owner123")
                .onlineUserCount(1)
                .shareToken(generateShareUrl(timerId))
                .userRole("VIEWER")
                .build());
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
        
        TargetTimeChangedEvent event = TargetTimeChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(timerId)
                .newTargetTime(newTargetTime)
                .oldTargetTime(Instant.now()) // TODO: 실제 이전 목표 시간 조회
                .changedBy(changedBy)
                .originServerId(serverId)
                .timestamp(Instant.now())
                .build();
        
        return kafkaEventPublisher.publishTimerEvent(event)
                .thenReturn(TimerResponse.builder()
                        .timerId(timerId)
                        .targetTime(newTargetTime)
                        .serverTime(Instant.now())
                        .remainingTime(Duration.between(Instant.now(), newTargetTime))
                        .ownerId(changedBy) // 임시
                        .completed(false)
                        .build());
    }

    /**
     * 특정 타이머의 모든 타임스탬프 기록을 조회합니다.
     * @param timerId 타이머 ID
     * @return 타임스탬프 엔트리 목록
     */
    public Flux<TimestampEntry> getTimerHistory(String timerId) {
        log.debug("타임스탬프 목록 조회: {}", timerId);
        return timestampRepository.findByTimerIdOrderByCreatedAtDesc(timerId)
                .doOnNext(entry -> log.debug("타임스탬프 조회: {} - 사용자: {}, 시간: {}", 
                        entry.getTimerId(), entry.getUserId(), entry.getTargetTime()))
                .doOnError(e -> log.error("타임스탬프 목록 조회 실패: timerId={}. Error: {}", timerId, e.getMessage(), e));
    }

    /**
     * 공유 URL 생성
     * @param timerId 타이머 ID
     * @return 공유 URL
     */
    private String generateShareUrl(String timerId) {
        return "/timer/" + timerId;
    }
    
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