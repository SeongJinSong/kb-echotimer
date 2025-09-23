package com.kb.timer.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * WebSocket 세션 정보
 * Redis에 저장되는 세션 상태 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    
    /**
     * WebSocket 세션 ID
     */
    private String sessionId;
    
    /**
     * 타이머 ID
     */
    private String timerId;
    
    /**
     * 사용자 ID
     */
    private String userId;
    
    /**
     * 연결된 서버 ID
     */
    private String serverId;
    
    /**
     * 연결 시각
     */
    private Instant connectedAt = Instant.now();
    
    /**
     * 마지막 하트비트 시각
     */
    private Instant lastHeartbeat = Instant.now();
}
