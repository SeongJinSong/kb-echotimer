package com.kb.timer.repository;

import com.kb.timer.model.entity.TimestampEntry;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * 타임스탬프 엔트리 Repository
 * MongoDB를 사용한 리액티브 데이터 접근
 */
@Repository
public interface TimestampEntryRepository extends ReactiveMongoRepository<TimestampEntry, String> {
    
    /**
     * 특정 타이머의 타임스탬프 조회 (현재 시각 기준 오름차순)
     * @param timerId 타이머 ID
     * @return 타임스탬프 엔트리 목록
     */
    Flux<TimestampEntry> findByTimerIdOrderByCreatedAtAsc(String timerId);
    
    /**
     * 특정 타이머와 사용자의 타임스탬프 조회 (현재 시각 기준 오름차순)
     * @param timerId 타이머 ID
     * @param userId 사용자 ID
     * @return 타임스탬프 엔트리 목록
     */
    Flux<TimestampEntry> findByTimerIdAndUserIdOrderByCreatedAtAsc(String timerId, String userId);
}
