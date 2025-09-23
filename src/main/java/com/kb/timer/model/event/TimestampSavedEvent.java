package com.kb.timer.model.event;

import com.kb.timer.model.entity.TimestampEntry;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
     * 저장된 타임스탬프 정보
     */
    private TimestampEntry timestampEntry;
    
    @Override
    public String getEventType() {
        return "TIMESTAMP_SAVED";
    }
    
    @Override
    public EventPriority getPriority() {
        return EventPriority.IMPORTANT;
    }
}
