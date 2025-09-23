package com.kb.timer.service;

import com.kb.timer.model.entity.TimerEventLog;
import com.kb.timer.model.event.TimerEvent;
import com.kb.timer.repository.TimerEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.Instant;

/**
 * Kafka 이벤트 소비 서비스
 * Kafka에서 이벤트를 소비하고 WebSocket으로 브로드캐스트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventConsumer {
    
    private final ReactiveKafkaConsumerTemplate<String, TimerEvent> kafkaConsumerTemplate;
    private final RedisConnectionManager connectionManager;
    private final TimerEventLogRepository eventLogRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    @Value("${server.instance.id}")
    private String serverId;
    
    /**
     * Kafka 이벤트 소비 시작
     */
    @PostConstruct
    public void startConsuming() {
        kafkaConsumerTemplate
            .receiveAutoAck()
            .doOnNext(record -> {
                log.debug("Kafka 이벤트 수신: {} - {}", 
                    record.value().getEventType(), record.value().getTimerId());
            })
            .flatMap(record -> processEvent(record.value()))
            .doOnError(error -> log.error("Kafka 이벤트 처리 중 오류 발생", error))
            .retry() // 오류 발생 시 재시도
            .subscribe();
        
        log.info("Kafka 이벤트 소비 시작됨 - 서버 ID: {}", serverId);
    }
    
    /**
     * 이벤트 처리
     * @param event 처리할 이벤트
     * @return 처리 결과
     */
    private Mono<Void> processEvent(TimerEvent event) {
        return connectionManager.isServerRelevantForTimer(event.getTimerId())
            .flatMap(isRelevant -> {
                if (!isRelevant) {
                    // 현재 서버와 관련없는 이벤트는 무시
                    log.debug("서버와 관련없는 이벤트 무시: {} - {}", 
                        event.getEventType(), event.getTimerId());
                    return Mono.empty();
                }
                
                log.info("이벤트 처리 시작: {} - {}", event.getEventType(), event.getTimerId());
                
                return Mono.when(
                    // 1. 이벤트 로그 저장
                    saveEventLog(event),
                    // 2. WebSocket으로 브로드캐스트
                    broadcastToWebSocket(event)
                );
            })
            .doOnSuccess(result -> 
                log.debug("이벤트 처리 완료: {} - {}", event.getEventType(), event.getTimerId())
            )
            .doOnError(error -> 
                log.error("이벤트 처리 실패: {} - {}", event.getEventType(), event.getTimerId(), error)
            );
    }
    
    /**
     * 이벤트 로그 저장
     * @param event 저장할 이벤트
     * @return 저장 결과
     */
    private Mono<Void> saveEventLog(TimerEvent event) {
        TimerEventLog eventLog = TimerEventLog.builder()
            .timerId(event.getTimerId())
            .eventType(event.getEventType())
            .timestamp(event.getTimestamp())
            .userId(extractUserId(event))
            .eventData(event)
            .createdAt(Instant.now())
            .build();
        
        return eventLogRepository.save(eventLog)
            .doOnSuccess(saved -> 
                log.debug("이벤트 로그 저장 완료: {}", saved.getId())
            )
            .then();
    }
    
    /**
     * WebSocket으로 이벤트 브로드캐스트
     * @param event 브로드캐스트할 이벤트
     * @return 브로드캐스트 결과
     */
    private Mono<Void> broadcastToWebSocket(TimerEvent event) {
        return Mono.fromRunnable(() -> {
            String destination = "/topic/timer/" + event.getTimerId();
            messagingTemplate.convertAndSend(destination, event);
            log.debug("WebSocket 브로드캐스트 완료: {} -> {}", event.getEventType(), destination);
        });
    }
    
    /**
     * 이벤트에서 사용자 ID 추출
     * @param event 이벤트
     * @return 사용자 ID (없으면 null)
     */
    private String extractUserId(TimerEvent event) {
        // 이벤트 타입에 따라 사용자 ID 추출 로직이 다를 수 있음
        // 여기서는 간단히 null 반환 (실제 구현에서는 이벤트별로 처리)
        return null;
    }
}
