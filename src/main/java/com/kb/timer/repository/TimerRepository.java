package com.kb.timer.repository;

import com.kb.timer.model.entity.Timer;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * 타이머 레포지토리
 * 타이머 엔티티의 데이터베이스 접근을 담당
 */
@Repository
public interface TimerRepository extends ReactiveMongoRepository<Timer, String> {
    
    /**
     * 소유자 ID로 타이머 목록 조회
     * @param ownerId 소유자 ID
     * @return 타이머 목록
     */
    Flux<Timer> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
    
    /**
     * 공유 토큰으로 타이머 조회
     * @param shareToken 공유 토큰
     * @return 타이머
     */
    Mono<Timer> findByShareToken(String shareToken);
    
    /**
     * 완료되지 않은 타이머 목록 조회 (정리용)
     * @param targetTime 기준 시간
     * @return 미완료 타이머 목록
     */
    Flux<Timer> findByCompletedFalseAndTargetTimeBefore(Instant targetTime);
    
    /**
     * 완료된 타이머 목록 조회 (정리용)
     * @param completedAt 완료 시간 기준
     * @return 완료된 타이머 목록
     */
    Flux<Timer> findByCompletedTrueAndCompletedAtBefore(Instant completedAt);
    
    /**
     * 특정 시간 범위의 미완료 타이머 조회 (복구용)
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 미완료 타이머 목록
     */
    Flux<Timer> findByCompletedFalseAndTargetTimeAfterAndTargetTimeBefore(Instant startTime, Instant endTime);
}
