package com.kb.timer.model.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

/**
 * 타이머 완료 처리 로그
 * Keyspace Notification을 통한 타이머 완료 처리 기록을 저장
 */
@Data
@Builder
@Document(collection = "timer_completion_logs")
public class TimerCompletionLog {
    
    @Id
    private String id;
    
    /**
     * 타이머 ID
     */
    @Indexed
    private String timerId;
    
    /**
     * 처리한 서버 ID
     */
    @Indexed
    private String serverId;
    
    /**
     * Keyspace Notification 수신 시간
     */
    private Instant notificationReceivedAt;
    
    /**
     * 타이머 완료 처리 시작 시간
     */
    private Instant processingStartedAt;
    
    /**
     * 타이머 완료 처리 완료 시간
     */
    private Instant processingCompletedAt;
    
    /**
     * 처리 성공 여부
     */
    private boolean success;
    
    /**
     * 처리 실패 시 오류 메시지
     */
    private String errorMessage;
    
    /**
     * 분산 락 획득 여부
     */
    private boolean lockAcquired;
    
    /**
     * 타이머의 원래 목표 시간
     */
    private Instant originalTargetTime;
    
    /**
     * 실제 처리 지연 시간 (밀리초)
     * (처리 시작 시간 - 원래 목표 시간)
     */
    private Long processingDelayMs;
    
    /**
     * 로그 생성 시간
     */
    @Builder.Default
    private Instant createdAt = Instant.now();
}
