package com.kb.timer.model.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 타이머 완료 이벤트
 * Redis TTL 만료 시 발행되어 TimerService에서 처리
 */
@Getter
public class TimerCompletionEvent extends ApplicationEvent {
    
    private final String timerId;
    
    public TimerCompletionEvent(Object source, String timerId) {
        super(source);
        this.timerId = timerId;
    }
}
