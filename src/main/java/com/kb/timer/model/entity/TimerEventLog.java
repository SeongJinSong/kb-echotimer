package com.kb.timer.model.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * 타이머 이벤트 로그 - MongoDB 저장용
 * 모든 타이머 관련 이벤트의 감사 로그
 */
@Document(collection = "timer_events")
@CompoundIndex(def = "{'timerId': 1, 'timestamp': -1}")
@CompoundIndex(def = "{'eventType': 1, 'timestamp': -1}")
@Data
@Builder
public class TimerEventLog {
    
    /**
     * MongoDB 문서 ID
     */
    @Id
    private String id;
    
    /**
     * 이벤트 고유 ID
     */
    @Indexed
    private String eventId;
    
    /**
     * 타이머 ID
     */
    @Indexed
    private String timerId;
    
    /**
     * 이벤트 타입
     */
    @Indexed
    private String eventType;
    
    /**
     * 이벤트 발생 시각
     */
    private Instant timestamp;
    
    /**
     * 이벤트 관련 사용자 ID
     */
    private String userId;
    
    /**
     * 이벤트를 발생시킨 서버 ID
     */
    private String originServerId;
    
    /**
     * 이벤트 우선순위
     */
    private String priority;
    
    /**
     * 이벤트별 상세 데이터
     */
    private Map<String, Object> eventData;
    
    /**
     * 문서 생성 시각 (TTL 인덱스용)
     */
    @Indexed(expireAfterSeconds = 31536000) // 1년 후 자동 삭제
    private Instant createdAt = Instant.now();
}
