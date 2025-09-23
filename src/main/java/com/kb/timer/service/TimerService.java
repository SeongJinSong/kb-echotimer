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

import java.time.Instant;
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
    private final KafkaEventPublisher eventPublisher;
    private final RedisConnectionManager connectionManager;
    
    @Value("${server.instance.id}")
    private String serverId;
    
    /**
     * 새로운 타이머 생성
     * @param targetTime 목표 시간 (초)
     * @param ownerId 소유자 ID
     * @return 타이머 정보
     */
    public Mono<TimerResponse> createTimer(long targetTime, String ownerId) {
        String timerId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        
        log.info("새 타이머 생성: {} (목표: {}초, 소유자: {})", timerId, targetTime, ownerId);
        
        TimerResponse response = TimerResponse.builder()
            .timerId(timerId)
            .targetTime(now.plusSeconds(targetTime))
            .serverTime(now)
            .remainingTime(java.time.Duration.ofSeconds(targetTime))
            .ownerId(ownerId)
            .shareToken(generateShareUrl(timerId))
            .build();
        
        return Mono.just(response);
    }
    
    /**
     * 타이머 정보 조회
     * @param timerId 타이머 ID
     * @return 타이머 정보
     */
    public Mono<TimerResponse> getTimer(String timerId) {
        log.debug("타이머 정보 조회: {}", timerId);
        
        // 실제 구현에서는 Redis나 DB에서 타이머 정보를 조회
        // 여기서는 간단히 기본값 반환
        Instant now = Instant.now();
        
        return Mono.just(TimerResponse.builder()
            .timerId(timerId)
            .targetTime(now.plusSeconds(3600L)) // 기본 1시간
            .serverTime(now)
            .remainingTime(java.time.Duration.ofSeconds(3600L))
            .shareToken(generateShareUrl(timerId))
            .build());
    }
    
    /**
     * 사용자 타이머 참여
     * @param timerId 타이머 ID
     * @param userId 사용자 ID
     * @return 참여 결과
     */
    public Mono<Void> joinTimer(String timerId, String userId) {
        log.info("사용자 타이머 참여: {} -> {}", userId, timerId);
        
        return connectionManager.recordUserConnection(timerId, userId, "session-" + userId, serverId)
            .then(publishUserJoinedEvent(timerId, userId))
            .doOnSuccess(result -> 
                log.info("사용자 타이머 참여 완료: {} -> {}", userId, timerId)
            );
    }
    
    /**
     * 사용자 타이머 떠나기
     * @param timerId 타이머 ID
     * @param userId 사용자 ID
     * @return 떠나기 결과
     */
    public Mono<Void> leaveTimer(String timerId, String userId) {
        log.info("사용자 타이머 떠나기: {} -> {}", userId, timerId);
        
        return connectionManager.removeUserConnection("session-" + userId)
            .then(publishUserLeftEvent(timerId, userId))
            .doOnSuccess(result -> 
                log.info("사용자 타이머 떠나기 완료: {} -> {}", userId, timerId)
            );
    }
    
    /**
     * 타임스탬프 저장
     * @param timerId 타이머 ID
     * @param userId 사용자 ID
     * @param remainingTime 남은 시간
     * @param targetTime 목표 시간
     * @return 저장 결과
     */
    public Mono<TimestampEntry> saveTimestamp(String timerId, String userId, 
                                            long remainingTime, long targetTime) {
        log.info("타임스탬프 저장: {} - 사용자: {}, 남은시간: {}초", timerId, userId, remainingTime);
        
        Instant now = Instant.now();
        
        TimestampEntry entry = TimestampEntry.builder()
            .timerId(timerId)
            .userId(userId)
            .savedAt(now)
            .remainingTime(java.time.Duration.ofSeconds(remainingTime))
            .targetTime(now.plusSeconds(targetTime))
            .metadata(java.util.Map.of("status", "저장됨"))
            .createdAt(now)
            .build();
        
        return timestampRepository.save(entry)
            .flatMap(saved -> 
                publishTimestampSavedEvent(saved)
                    .thenReturn(saved)
            )
            .doOnSuccess(saved -> 
                log.info("타임스탬프 저장 완료: {}", saved.getId())
            );
    }
    
    /**
     * 타이머의 타임스탬프 목록 조회
     * @param timerId 타이머 ID
     * @return 타임스탬프 목록
     */
    public Flux<TimestampEntry> getTimestamps(String timerId) {
        log.debug("타임스탬프 목록 조회: {}", timerId);
        
        return timestampRepository.findByTimerIdOrderByCreatedAtDesc(timerId)
            .doOnNext(entry -> 
                log.debug("타임스탬프 조회: {} - 사용자: {}, 시간: {}", 
                    entry.getId(), entry.getUserId(), entry.getRemainingTime())
            );
    }
    
    /**
     * 타이머 목표 시간 변경
     * @param timerId 타이머 ID
     * @param oldTargetTime 기존 목표 시간
     * @param newTargetTime 새로운 목표 시간
     * @param changedBy 변경한 사용자
     * @return 변경 결과
     */
    public Mono<Void> changeTargetTime(String timerId, long oldTargetTime, 
                                     long newTargetTime, String changedBy) {
        log.info("타이머 목표 시간 변경: {} - {}초 -> {}초 (변경자: {})", 
            timerId, oldTargetTime, newTargetTime, changedBy);
        
        return publishTargetTimeChangedEvent(timerId, oldTargetTime, newTargetTime, changedBy);
    }
    
    /**
     * 타이머 완료 처리
     * @param timerId 타이머 ID
     * @return 완료 처리 결과
     */
    public Mono<Void> completeTimer(String timerId) {
        log.info("타이머 완료: {}", timerId);
        
        return publishTimerCompletedEvent(timerId);
    }
    
    // === 이벤트 발행 메서드들 ===
    
    private Mono<Void> publishUserJoinedEvent(String timerId, String userId) {
        UserJoinedEvent event = UserJoinedEvent.builder()
            .timerId(timerId)
            .userId(userId)
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .originServerId(serverId)
            .build();
        
        return eventPublisher.publishEvent(event);
    }
    
    private Mono<Void> publishUserLeftEvent(String timerId, String userId) {
        UserLeftEvent event = UserLeftEvent.builder()
            .timerId(timerId)
            .userId(userId)
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .originServerId(serverId)
            .build();
        
        return eventPublisher.publishEvent(event);
    }
    
    private Mono<Void> publishTimestampSavedEvent(TimestampEntry entry) {
        TimestampSavedEvent event = TimestampSavedEvent.builder()
            .timerId(entry.getTimerId())
            .userId(entry.getUserId())
            .timestampEntry(entry)
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .originServerId(serverId)
            .build();
        
        return eventPublisher.publishEvent(event);
    }
    
    private Mono<Void> publishTargetTimeChangedEvent(String timerId, long oldTargetTime, 
                                                   long newTargetTime, String changedBy) {
        Instant now = Instant.now();
        TargetTimeChangedEvent event = TargetTimeChangedEvent.builder()
            .timerId(timerId)
            .oldTargetTime(now.plusSeconds(oldTargetTime))
            .newTargetTime(now.plusSeconds(newTargetTime))
            .changedBy(changedBy)
            .eventId(UUID.randomUUID().toString())
            .timestamp(now)
            .originServerId(serverId)
            .build();
        
        return eventPublisher.publishEvent(event);
    }
    
    private Mono<Void> publishTimerCompletedEvent(String timerId) {
        TimerCompletedEvent event = TimerCompletedEvent.builder()
            .timerId(timerId)
            .completedAt(Instant.now())
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .originServerId(serverId)
            .build();
        
        return eventPublisher.publishEvent(event);
    }
    
    /**
     * 공유 URL 생성
     * @param timerId 타이머 ID
     * @return 공유 URL
     */
    private String generateShareUrl(String timerId) {
        return "/timer/" + timerId;
    }
}
