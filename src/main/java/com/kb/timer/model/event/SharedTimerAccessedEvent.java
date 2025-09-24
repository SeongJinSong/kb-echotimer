package com.kb.timer.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 공유 타이머 접속 이벤트
 * 다른 사용자가 공유 타이머에 접속했을 때 소유자에게 알림
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class SharedTimerAccessedEvent extends TimerEvent {
    
    /**
     * 접속한 사용자 ID
     */
    private String accessedUserId;
    
    /**
     * 타이머 소유자 ID
     */
    private String ownerId;
    
    @Override
    public String getEventType() {
        return "SHARED_TIMER_ACCESSED";
    }
    
    @Override
    public EventPriority getPriority() {
        return EventPriority.NORMAL;
    }
}
