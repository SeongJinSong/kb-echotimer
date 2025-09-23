package com.kb.timer.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 타이머 응답 DTO
 * 클라이언트에게 전송되는 타이머 상태 정보
 */
@Data
@Builder
public class TimerResponse {
    
    /**
     * 타이머 ID
     */
    private String timerId;
    
    /**
     * 사용자 ID
     */
    private String userId;
    
    /**
     * 목표 시간 (UTC)
     */
    private Instant targetTime;
    
    /**
     * 서버 현재 시각 (UTC) - 클라이언트 동기화용
     */
    private Instant serverTime;
    
    /**
     * 남은 시간
     */
    private Duration remainingTime;
    
    /**
     * 타이머 완료 여부
     */
    private boolean completed;
    
    /**
     * 타이머 소유자 ID
     */
    private String ownerId;
    
    /**
     * 현재 온라인 사용자 수
     */
    private int onlineUserCount;
    
    /**
     * 온라인 사용자 목록
     */
    private List<String> onlineUsers;
    
    /**
     * 공유 토큰 (소유자에게만 제공)
     */
    private String shareToken;
    
    /**
     * 사용자 권한 (OWNER, VIEWER)
     */
    private String userRole;
    
    /**
     * 저장된 시각
     */
    private Instant savedAt;
    
    /**
     * 추가 메타데이터
     */
    private Map<String, Object> metadata;
}
