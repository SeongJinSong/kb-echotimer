package com.kb.timer.model.event;

import com.kb.timer.model.entity.Timer;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 타이머 스케줄링 관련 이벤트
 */
@Getter
public class TimerScheduleEvent extends ApplicationEvent {
    
    public enum Type {
        SCHEDULE,    // 타이머 스케줄 등록
        UPDATE,      // 타이머 스케줄 업데이트  
        CANCEL       // 타이머 스케줄 취소
    }
    
    private final Type type;
    private final Timer timer;
    private final String timerId;
    
    // 타이머 스케줄 등록/업데이트용 생성자
    public TimerScheduleEvent(Object source, Type type, Timer timer) {
        super(source);
        this.type = type;
        this.timer = timer;
        this.timerId = timer.getId();
    }
    
    // 타이머 스케줄 취소용 생성자 (Timer 객체 없이 ID만으로)
    public TimerScheduleEvent(Object source, Type type, String timerId) {
        super(source);
        this.type = type;
        this.timer = null;
        this.timerId = timerId;
    }
}
