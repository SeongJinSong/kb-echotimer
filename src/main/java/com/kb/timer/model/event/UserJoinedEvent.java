package com.kb.timer.model.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 사용자 입장 이벤트
 * 사용자가 타이머 룸에 입장했을 때 발생
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class UserJoinedEvent extends TimerEvent {
    
    /**
     * 입장한 사용자 ID
     */
    private String userId;
    
    /**
     * 연결된 서버 ID
     */
    private String serverId;
    
    /**
     * 현재 온라인 사용자 수
     */
    private int onlineUserCount;
    
    @Override
    public String getEventType() {
        return "USER_JOINED";
    }
    
    @Override
    public EventPriority getPriority() {
        return EventPriority.NORMAL;
    }
}
