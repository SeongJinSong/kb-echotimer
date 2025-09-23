package com.kb.timer.repository;

import com.kb.timer.model.entity.TimerEventLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * 타이머 이벤트 로그 Repository
 * MongoDB를 사용한 리액티브 데이터 접근
 */
@Repository
public interface TimerEventLogRepository extends ReactiveMongoRepository<TimerEventLog, String> {
    
    /**
     * 특정 타이머의 이벤트 로그 조회
     * @param timerId 타이머 ID
     * @return 이벤트 로그 목록
     */
    Flux<TimerEventLog> findByTimerIdOrderByCreatedAtDesc(String timerId);
    
    /**
     * 특정 이벤트 타입의 로그 조회
     * @param eventType 이벤트 타입
     * @return 이벤트 로그 목록
     */
    Flux<TimerEventLog> findByEventTypeOrderByCreatedAtDesc(String eventType);
    
    /**
     * 특정 타이머와 이벤트 타입의 로그 조회
     * @param timerId 타이머 ID
     * @param eventType 이벤트 타입
     * @return 이벤트 로그 목록
     */
    Flux<TimerEventLog> findByTimerIdAndEventTypeOrderByCreatedAtDesc(String timerId, String eventType);
}
