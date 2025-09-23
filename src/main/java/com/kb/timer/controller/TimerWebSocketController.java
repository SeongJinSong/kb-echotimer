package com.kb.timer.controller;

import com.kb.timer.model.dto.TimerResponse;
import com.kb.timer.model.event.TargetTimeChangedEvent;
import com.kb.timer.model.event.TimestampSavedEvent;
import com.kb.timer.service.RedisConnectionManager;
import com.kb.timer.service.TimerService;
import com.kb.timer.util.ServerInstanceIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 타이머 WebSocket 컨트롤러
 * STOMP 프로토콜을 사용한 실시간 타이머 통신 처리
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TimerWebSocketController {

    private final TimerService timerService;
    private final RedisConnectionManager redisConnectionManager;
    private final ServerInstanceIdGenerator serverInstanceIdGenerator;

    /**
     * 타이머 구독 처리
     * 클라이언트가 특정 타이머를 구독할 때 호출됨
     * 
     * @param timerId 타이머 ID
     * @param headerAccessor WebSocket 세션 정보
     * @return 현재 타이머 상태
     */
    @SubscribeMapping("/topic/timer/{timerId}")
    public Mono<TimerResponse> subscribeTimer(@DestinationVariable String timerId,
                                            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String userId = extractUserId(headerAccessor);
        String serverId = serverInstanceIdGenerator.getServerInstanceId();
        
        log.info("타이머 구독 요청: timerId={}, userId={}, sessionId={}", timerId, userId, sessionId);
        
        // Redis에 연결 정보 기록
        return redisConnectionManager.recordUserConnection(timerId, userId, sessionId, serverId)
                .then(timerService.publishUserJoinedEvent(timerId, userId))
                .then(getCurrentTimerState(timerId, userId))
                .doOnSuccess(response -> log.info("타이머 구독 완료: timerId={}, userId={}", timerId, userId))
                .doOnError(error -> log.error("타이머 구독 실패: timerId={}, userId={}, error={}", 
                          timerId, userId, error.getMessage(), error));
    }

    /**
     * 타임스탬프 저장 요청 처리
     * 클라이언트가 현재 시점의 타임스탬프를 저장할 때 호출됨
     * 
     * @param timerId 타이머 ID
     * @param payload 저장 요청 데이터
     * @param headerAccessor WebSocket 세션 정보
     * @return 저장 결과
     */
    @MessageMapping("/timer/{timerId}/save")
    public Mono<TimerResponse> saveTimestamp(@DestinationVariable String timerId,
                                           @Payload SaveTimestampRequest payload,
                                           SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        
        log.info("타임스탬프 저장 요청: timerId={}, userId={}, targetTime={}", 
                timerId, userId, payload.getTargetTime());
        
        return timerService.saveTimestamp(
                timerId, 
                userId, 
                payload.getTargetTime(), 
                payload.getMetadata()
        ).map(timestampEntry -> TimerResponse.builder()
                .timerId(timerId)
                .userId(userId)
                .targetTime(timestampEntry.getTargetTime())
                .remainingTime(timestampEntry.getRemainingTime())
                .savedAt(timestampEntry.getSavedAt())
                .shareToken(generateShareToken(timerId))
                .metadata(timestampEntry.getMetadata())
                .build())
        .doOnSuccess(response -> log.info("타임스탬프 저장 완료: timerId={}, userId={}", timerId, userId))
        .doOnError(error -> log.error("타임스탬프 저장 실패: timerId={}, userId={}, error={}", 
                  timerId, userId, error.getMessage(), error));
    }

    /**
     * 목표 시간 변경 요청 처리 (Owner만 가능)
     * 
     * @param timerId 타이머 ID
     * @param payload 변경 요청 데이터
     * @param headerAccessor WebSocket 세션 정보
     * @return 변경 결과
     */
    @MessageMapping("/timer/{timerId}/change-target")
    public Mono<TimerResponse> changeTargetTime(@DestinationVariable String timerId,
                                              @Payload ChangeTargetTimeRequest payload,
                                              SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        
        log.info("목표 시간 변경 요청: timerId={}, userId={}, newTargetTime={}", 
                timerId, userId, payload.getNewTargetTime());
        
        // TODO: Owner 권한 검증 로직 추가 필요
        
        TargetTimeChangedEvent event = TargetTimeChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(timerId)
                .oldTargetTime(payload.getOldTargetTime())
                .newTargetTime(payload.getNewTargetTime())
                .changedBy(userId)
                .originServerId(serverInstanceIdGenerator.getServerInstanceId())
                .timestamp(Instant.now())
                .build();
        
        return timerService.publishTargetTimeChangedEvent(event)
                .then(getCurrentTimerState(timerId, userId))
                .doOnSuccess(response -> log.info("목표 시간 변경 완료: timerId={}, userId={}", timerId, userId))
                .doOnError(error -> log.error("목표 시간 변경 실패: timerId={}, userId={}, error={}", 
                          timerId, userId, error.getMessage(), error));
    }

    /**
     * 타이머 완료 알림 처리
     * 
     * @param timerId 타이머 ID
     * @param headerAccessor WebSocket 세션 정보
     * @return 완료 처리 결과
     */
    @MessageMapping("/timer/{timerId}/complete")
    public Mono<String> completeTimer(@DestinationVariable String timerId,
                                    SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        
        log.info("타이머 완료 알림: timerId={}, userId={}", timerId, userId);
        
        return timerService.publishTimerCompletedEvent(timerId)
                .then(Mono.just("타이머 완료 처리됨"))
                .doOnSuccess(result -> log.info("타이머 완료 처리 완료: timerId={}, userId={}", timerId, userId))
                .doOnError(error -> log.error("타이머 완료 처리 실패: timerId={}, userId={}, error={}", 
                          timerId, userId, error.getMessage(), error));
    }

    /**
     * 현재 타이머 상태 조회
     * 
     * @param timerId 타이머 ID
     * @param userId 사용자 ID
     * @return 타이머 상태
     */
    private Mono<TimerResponse> getCurrentTimerState(String timerId, String userId) {
        // TODO: 실제 타이머 상태 조회 로직 구현
        // 현재는 기본값 반환
        return Mono.just(TimerResponse.builder()
                .timerId(timerId)
                .userId(userId)
                .targetTime(Instant.now().plus(Duration.ofMinutes(25))) // 기본 25분 포모도로
                .remainingTime(Duration.ofMinutes(25))
                .savedAt(Instant.now())
                .shareToken(generateShareToken(timerId))
                .metadata(Map.of("status", "active"))
                .build());
    }

    /**
     * WebSocket 세션에서 사용자 ID 추출
     * 
     * @param headerAccessor WebSocket 헤더 접근자
     * @return 사용자 ID
     */
    private String extractUserId(SimpMessageHeaderAccessor headerAccessor) {
        // TODO: 실제 인증 시스템과 연동하여 사용자 ID 추출
        // 현재는 세션 ID를 사용자 ID로 사용
        String sessionId = headerAccessor.getSessionId();
        return "user-" + sessionId.substring(0, 8);
    }

    /**
     * 공유 토큰 생성
     * 
     * @param timerId 타이머 ID
     * @return 공유 토큰
     */
    private String generateShareToken(String timerId) {
        // TODO: 실제 토큰 생성 로직 구현
        return "share-" + timerId + "-" + System.currentTimeMillis();
    }

    /**
     * 타임스탬프 저장 요청 DTO
     */
    public static class SaveTimestampRequest {
        private Instant targetTime;
        private Map<String, Object> metadata;

        // Getters and Setters
        public Instant getTargetTime() { return targetTime; }
        public void setTargetTime(Instant targetTime) { this.targetTime = targetTime; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    /**
     * 목표 시간 변경 요청 DTO
     */
    public static class ChangeTargetTimeRequest {
        private Instant oldTargetTime;
        private Instant newTargetTime;

        // Getters and Setters
        public Instant getOldTargetTime() { return oldTargetTime; }
        public void setOldTargetTime(Instant oldTargetTime) { this.oldTargetTime = oldTargetTime; }
        public Instant getNewTargetTime() { return newTargetTime; }
        public void setNewTargetTime(Instant newTargetTime) { this.newTargetTime = newTargetTime; }
    }
}
