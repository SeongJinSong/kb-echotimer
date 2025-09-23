package com.kb.timer.model.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 타이머 엔티티
 * 기준 시각과 소유자 정보를 저장
 */
@Data
@Builder
@Document(collection = "timers")
public class Timer {
    @Id
    private String id;

    /**
     * 타이머 소유자 ID
     */
    @Indexed
    private String ownerId;

    /**
     * 기준 시각 (목표 시각)
     */
    private Instant targetTime;

    /**
     * 타이머 생성 시각
     */
    private Instant createdAt;

    /**
     * 마지막 수정 시각
     */
    private Instant updatedAt;

    /**
     * 타이머 완료 여부
     */
    private boolean completed;

    /**
     * 완료 시각
     */
    private Instant completedAt;

    /**
     * 공유 토큰 (URL 생성용)
     */
    private String shareToken;

    /**
     * TTL 인덱스용 (30일 후 자동 삭제)
     */
    @Indexed(expireAfterSeconds = 2592000) // 30일
    @Builder.Default
    private Instant expiresAt = Instant.now().plusSeconds(2592000);
}
