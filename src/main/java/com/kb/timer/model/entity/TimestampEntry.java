package com.kb.timer.model.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 타임스탬프 엔트리 - MongoDB 저장용
 * 사용자가 저장한 특정 시점의 타이머 상태 정보
 */
@Document(collection = "timer_timestamps")
@CompoundIndex(def = "{'timerId': 1, 'savedAt': 1}")
@CompoundIndex(def = "{'userId': 1, 'savedAt': -1}")
@Data
@Builder
public class TimestampEntry {
    
    /**
     * MongoDB 문서 ID
     */
    @Id
    private String id;
    
    /**
     * 타이머 ID
     */
    @Indexed
    private String timerId;
    
    /**
     * 타임스탬프를 저장한 사용자 ID
     */
    @Indexed
    private String userId;
    
    /**
     * 타임스탬프 저장 시각
     */
    private Instant savedAt;
    
    /**
     * 저장 시점의 남은 시간
     */
    private Duration remainingTime;
    
    /**
     * 저장 시점의 목표 시간
     */
    private Instant targetTime;
    
    /**
     * 추가 메타데이터 (IP, User-Agent 등)
     */
    private Map<String, Object> metadata;
    
    /**
     * 문서 생성 시각 (TTL 인덱스용)
     */
    @Indexed(expireAfterSeconds = 7776000) // 90일 후 자동 삭제
    @Builder.Default
    private Instant createdAt = Instant.now();
}
