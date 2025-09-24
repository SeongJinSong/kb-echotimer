package com.kb.timer.repository;

import com.kb.timer.model.entity.TimerCompletionLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * 타이머 완료 로그 Repository
 */
@Repository
public interface TimerCompletionLogRepository extends ReactiveMongoRepository<TimerCompletionLog, String> {
    
    /**
     * 특정 타이머의 완료 로그 조회
     * 
     * @param timerId 타이머 ID
     * @return 완료 로그 목록
     */
    Flux<TimerCompletionLog> findByTimerId(String timerId);
    
    /**
     * 특정 시간 이후에 처리된 완료 로그 조회
     * 
     * @param after 기준 시간
     * @return 완료 로그 목록
     */
    Flux<TimerCompletionLog> findByProcessingStartedAtAfter(Instant after);
    
    /**
     * 성공적으로 처리된 완료 로그 조회 (특정 시간 이후)
     * 
     * @param after 기준 시간
     * @return 성공한 완료 로그 목록
     */
    Flux<TimerCompletionLog> findBySuccessTrueAndProcessingStartedAtAfter(Instant after);
    
    /**
     * 실패한 처리 로그 조회
     * 
     * @param after 기준 시간 이후
     * @return 실패한 완료 로그 목록
     */
    Flux<TimerCompletionLog> findBySuccessFalseAndCreatedAtAfter(Instant after);
    
    /**
     * 특정 타이머의 성공한 완료 로그가 있는지 확인
     * 
     * @param timerId 타이머 ID
     * @return 성공한 로그 존재 여부
     */
    Mono<Boolean> existsByTimerIdAndSuccessTrue(String timerId);
}
