package com.kb.timer.model.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * 타이머 이벤트 기본 클래스
 * 모든 타이머 관련 이벤트의 부모 클래스
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TargetTimeChangedEvent.class, name = "TARGET_TIME_CHANGED"),
    @JsonSubTypes.Type(value = TimestampSavedEvent.class, name = "TIMESTAMP_SAVED"),
    @JsonSubTypes.Type(value = UserJoinedEvent.class, name = "USER_JOINED"),
    @JsonSubTypes.Type(value = UserLeftEvent.class, name = "USER_LEFT"),
    @JsonSubTypes.Type(value = TimerCompletedEvent.class, name = "TIMER_COMPLETED"),
    @JsonSubTypes.Type(value = SharedTimerAccessedEvent.class, name = "SHARED_TIMER_ACCESSED")
})
@Data
@SuperBuilder
@NoArgsConstructor
public abstract class TimerEvent {
    
    /**
     * 이벤트 고유 ID
     */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    /**
     * 타이머 ID
     */
    private String timerId;
    
    /**
     * 이벤트 발생 시각
     */
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    /**
     * 이벤트를 발생시킨 서버 ID
     */
    private String originServerId;
    
    /**
     * 이벤트 타입 (하위 클래스에서 구현)
     */
    public abstract String getEventType();
    
    /**
     * 이벤트 우선순위 (기본값: NORMAL)
     */
    public EventPriority getPriority() {
        return EventPriority.NORMAL;
    }
}
