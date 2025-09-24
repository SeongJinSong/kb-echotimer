package com.kb.timer.repository;

import com.kb.timer.model.entity.TimerEventLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 타이머 이벤트 로그 Repository
 * MongoDB를 사용한 리액티브 데이터 접근
 */
@Repository
public interface TimerEventLogRepository extends ReactiveMongoRepository<TimerEventLog, String> {
}
