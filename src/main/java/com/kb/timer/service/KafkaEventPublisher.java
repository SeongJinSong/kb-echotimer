package com.kb.timer.service;

import com.kb.timer.model.event.TimerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Kafka 이벤트 발행 서비스
 * 타이머 관련 이벤트를 Kafka 토픽으로 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher {
    
    private final ReactiveKafkaProducerTemplate<String, TimerEvent> kafkaProducerTemplate;
    
    @Value("${timer.kafka.topics.timer-events}")
    private String timerEventsTopic;
    
    @Value("${timer.kafka.topics.user-actions}")
    private String userActionsTopic;
    
    /**
     * 타이머 이벤트 발행
     * @param event 발행할 이벤트
     * @return 발행 결과
     */
    public Mono<Void> publishTimerEvent(TimerEvent event) {
        log.debug("타이머 이벤트 발행: {} - {}", event.getEventType(), event.getTimerId());
        
        return kafkaProducerTemplate
            .send(timerEventsTopic, event.getTimerId(), event)
            .doOnSuccess(result -> 
                log.info("타이머 이벤트 발행 성공: {} - {} (파티션: {}, 오프셋: {})", 
                    event.getEventType(), event.getTimerId(),
                    result.recordMetadata().partition(), 
                    result.recordMetadata().offset())
            )
            .doOnError(error -> 
                log.error("타이머 이벤트 발행 실패: {} - {}", event.getEventType(), event.getTimerId(), error)
            )
            .then();
    }
    
    /**
     * 사용자 액션 이벤트 발행
     * @param event 발행할 이벤트
     * @return 발행 결과
     */
    public Mono<Void> publishUserActionEvent(TimerEvent event) {
        log.debug("사용자 액션 이벤트 발행: {} - {}", event.getEventType(), event.getTimerId());
        
        return kafkaProducerTemplate
            .send(userActionsTopic, event.getTimerId(), event)
            .doOnSuccess(result -> 
                log.info("사용자 액션 이벤트 발행 성공: {} - {} (파티션: {}, 오프셋: {})", 
                    event.getEventType(), event.getTimerId(),
                    result.recordMetadata().partition(), 
                    result.recordMetadata().offset())
            )
            .doOnError(error -> 
                log.error("사용자 액션 이벤트 발행 실패: {} - {}", event.getEventType(), event.getTimerId(), error)
            )
            .then();
    }
    
    /**
     * 이벤트 타입에 따라 적절한 토픽으로 발행
     * @param event 발행할 이벤트
     * @return 발행 결과
     */
    public Mono<Void> publishEvent(TimerEvent event) {
        // 사용자 액션 관련 이벤트는 별도 토픽으로 발행
        if (isUserActionEvent(event.getEventType())) {
            return publishUserActionEvent(event);
        } else {
            return publishTimerEvent(event);
        }
    }
    
    /**
     * 사용자 액션 이벤트인지 확인
     * @param eventType 이벤트 타입
     * @return 사용자 액션 이벤트 여부
     */
    private boolean isUserActionEvent(String eventType) {
        return eventType.equals("USER_JOINED") || 
               eventType.equals("USER_LEFT") ||
               eventType.equals("TIMESTAMP_SAVED");
    }
}
