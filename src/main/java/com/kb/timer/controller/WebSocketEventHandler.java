package com.kb.timer.controller;

import com.kb.timer.service.RedisConnectionManager;
import com.kb.timer.service.TimerService;
import com.kb.timer.util.ServerInstanceIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * WebSocket 이벤트 핸들러
 * 연결, 해제, 구독, 구독 해제 이벤트 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventHandler {

    private final RedisConnectionManager redisConnectionManager;
    private final TimerService timerService;
    private final ServerInstanceIdGenerator serverInstanceIdGenerator;

    /**
     * WebSocket 연결 이벤트 처리
     * 
     * @param event 연결 이벤트
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("WebSocket 연결됨: sessionId={}", sessionId);
        
        // 연결 시점에는 특별한 처리 없음 (구독 시점에서 처리)
    }

    /**
     * WebSocket 연결 해제 이벤트 처리
     * 
     * @param event 연결 해제 이벤트
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("WebSocket 연결 해제됨: sessionId={}", sessionId);
        
        // Redis에서 연결 정보 제거
        redisConnectionManager.removeUserConnection(sessionId)
                .doOnSuccess(result -> log.info("연결 해제 처리 완료: sessionId={}", sessionId))
                .doOnError(error -> log.error("연결 해제 처리 실패: sessionId={}, error={}", 
                          sessionId, error.getMessage(), error))
                .subscribe(); // 비동기 처리
    }

    /**
     * 구독 이벤트 처리
     * 
     * @param event 구독 이벤트
     */
    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        
        log.debug("구독 이벤트: sessionId={}, destination={}", sessionId, destination);
        
        // 타이머 토픽 구독인지 확인
        if (destination != null && destination.startsWith("/topic/timer/")) {
            String timerId = extractTimerIdFromDestination(destination);
            if (timerId != null) {
                log.info("타이머 토픽 구독: sessionId={}, timerId={}", sessionId, timerId);
                
                // Redis에 사용자 연결 정보 저장
                String userId = extractUserIdFromHeaders(headerAccessor);
                String serverId = serverInstanceIdGenerator.getServerInstanceId();
                
                log.info("타이머 구독 요청: timerId={}, userId={}, sessionId={}", timerId, userId, sessionId);
                
                redisConnectionManager.recordUserConnection(timerId, userId, serverId, sessionId)
                    .then(timerService.publishUserJoinedEvent(timerId, userId))
                    .doOnSuccess(ignored -> log.info("타이머 구독 완료: timerId={}, userId={}", timerId, userId))
                    .doOnError(error -> log.error("타이머 구독 실패: timerId={}, userId={}, error={}", 
                              timerId, userId, error.getMessage(), error))
                    .subscribe(); // 비동기 처리
            }
        }
    }

    /**
     * 구독 해제 이벤트 처리
     * 
     * @param event 구독 해제 이벤트
     */
    @EventListener
    public void handleUnsubscribeEvent(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String subscriptionId = headerAccessor.getSubscriptionId();
        
        log.debug("구독 해제 이벤트: sessionId={}, subscriptionId={}", sessionId, subscriptionId);
        
        // 구독 해제 시 특별한 처리는 없음 (연결 해제 시점에서 일괄 처리)
    }

    /**
     * destination에서 타이머 ID 추출
     * 
     * @param destination STOMP destination
     * @return 타이머 ID
     */
    private String extractTimerIdFromDestination(String destination) {
        // "/topic/timer/{timerId}" 형식에서 timerId 추출
        if (destination.startsWith("/topic/timer/")) {
            return destination.substring("/topic/timer/".length());
        }
        return null;
    }
    
    /**
     * WebSocket 헤더에서 사용자 ID 추출
     * 
     * @param headerAccessor WebSocket 헤더 접근자
     * @return 사용자 ID
     */
    private String extractUserIdFromHeaders(StompHeaderAccessor headerAccessor) {
        // 헤더에서 userId 추출 시도
        String userId = headerAccessor.getFirstNativeHeader("userId");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        
        // 헤더에 없으면 세션 ID 기반으로 생성 (fallback)
        String sessionId = headerAccessor.getSessionId();
        return "user-" + sessionId.substring(0, 8);
    }
}
