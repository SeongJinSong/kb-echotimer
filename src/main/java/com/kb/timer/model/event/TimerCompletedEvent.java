package com.kb.timer.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 타이머 완료 이벤트
 * 타이머가 목표 시간에 도달했을 때 발생하는 중요 이벤트
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class TimerCompletedEvent extends TimerEvent {
    
    /**
     * 완료된 목표 시간
     */
    private Instant completedTargetTime;
    
    /**
     * 완료 감지 시각
     */
    private Instant completedAt = Instant.now();
    
    /**
     * 타이머 소유자 ID
     */
    private String ownerId;
    
    /**
     * 완료 시점의 온라인 사용자 수
     */
    private int onlineUserCount;
    
    @Override
    public String getEventType() {
        return "TIMER_COMPLETED";
    }
    
    @Override
    public EventPriority getPriority() {
        return EventPriority.CRITICAL; // 타이머 완료는 중요 이벤트
    }
}
