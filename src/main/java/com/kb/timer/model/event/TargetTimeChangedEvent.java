package com.kb.timer.model.event;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 타이머 목표 시간 변경 이벤트
 * Owner가 타이머의 목표 시간을 변경했을 때 발생
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class TargetTimeChangedEvent extends TimerEvent {
    
    /**
     * 변경 전 목표 시간
     */
    private Instant oldTargetTime;
    
    /**
     * 변경 후 목표 시간
     */
    private Instant newTargetTime;
    
    /**
     * 변경한 사용자 ID (Owner)
     */
    private String changedBy;
    
    /**
     * 서버 시각 (클라이언트 동기화용)
     */
    @Builder.Default
    private Instant serverTime = Instant.now();
    
    @Override
    public String getEventType() {
        return "TARGET_TIME_CHANGED";
    }
    
    @Override
    public EventPriority getPriority() {
        return EventPriority.IMPORTANT;
    }
}
