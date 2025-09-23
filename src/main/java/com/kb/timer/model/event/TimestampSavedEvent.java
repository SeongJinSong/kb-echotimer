package com.kb.timer.model.event;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 타임스탬프 저장 이벤트
 * 사용자가 현재 시점의 타임스탬프를 저장했을 때 발생
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class TimestampSavedEvent extends TimerEvent {
    
    /**
     * 타임스탬프를 저장한 사용자 ID
     */
    private String userId;
    
    /**
     * 저장된 시각
     */
    @Builder.Default
    private Instant savedAt = Instant.now();
    
    /**
     * 남은 시간
     */
    private Duration remainingTime;
    
    /**
     * 목표 시간
     */
    private Instant targetTime;
    
    /**
     * 추가 메타데이터
     */
    private Map<String, Object> metadata;
    
    @Override
    public String getEventType() {
        return "TIMESTAMP_SAVED";
    }
    
    @Override
    public EventPriority getPriority() {
        return EventPriority.IMPORTANT;
    }
}
