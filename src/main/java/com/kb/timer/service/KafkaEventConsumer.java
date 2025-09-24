package com.kb.timer.service;

import com.kb.timer.model.entity.TimerEventLog;
import com.kb.timer.model.event.SharedTimerAccessedEvent;
import com.kb.timer.model.event.TimerEvent;
import com.kb.timer.repository.TimerEventLogRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import java.time.Instant;

/**
 * Kafka 이벤트 소비 서비스
 * Kafka에서 이벤트를 소비하고 WebSocket으로 브로드캐스트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventConsumer {
    
    private final ReceiverOptions<String, TimerEvent> timerEventsConsumerOptions;
    private final ReceiverOptions<String, TimerEvent> userActionsConsumerOptions;
    private final RedisConnectionManager connectionManager;
    private final TimerEventLogRepository eventLogRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    @Value("${server.instance.id}")
    private String serverId;
    
    private reactor.core.Disposable timerEventsDisposable;
    private reactor.core.Disposable userActionsDisposable;
    
    /**
     * Kafka 이벤트 소비 시작
     */
    @PostConstruct
    public void startConsuming() {
        // Timer Events 토픽 구독
        timerEventsDisposable = KafkaReceiver.create(timerEventsConsumerOptions)
            .receive()
            .doOnNext(record -> {
                log.debug("Timer Event 수신: key={}, type={}, timerId={}", 
                    record.key(), record.value().getEventType(), record.value().getTimerId());
            })
            .flatMap(record -> processEvent(record.value())
                .doOnSuccess(v -> record.receiverOffset().acknowledge())
                .doOnError(e -> log.error("Timer Event 처리 실패: {}", e.getMessage(), e))
                .onErrorComplete()) // 오류 발생 시에도 계속 진행
            .doOnError(error -> log.error("Timer Event Consumer 오류", error))
            .onErrorContinue((error, obj) -> log.error("Timer Event Consumer 복구 시도: {}", obj, error))
            .subscribe();
        
        // User Actions 토픽 구독
        userActionsDisposable = KafkaReceiver.create(userActionsConsumerOptions)
            .receive()
            .doOnNext(record -> {
                log.debug("User Action Event 수신: key={}, type={}, timerId={}", 
                    record.key(), record.value().getEventType(), record.value().getTimerId());
            })
            .flatMap(record -> processEvent(record.value())
                .doOnSuccess(v -> record.receiverOffset().acknowledge())
                .doOnError(e -> log.error("User Action Event 처리 실패: {}", e.getMessage(), e))
                .onErrorComplete()) // 오류 발생 시에도 계속 진행
            .doOnError(error -> log.error("User Action Event Consumer 오류", error))
            .onErrorContinue((error, obj) -> log.error("User Action Event Consumer 복구 시도: {}", obj, error))
            .subscribe();
        
        log.info("Kafka Consumer 시작: serverId={}", serverId);
    }
    
    /**
     * 애플리케이션 종료 시 Consumer 정리
     */
    @PreDestroy
    public void cleanup() {
        if (timerEventsDisposable != null && !timerEventsDisposable.isDisposed()) {
            timerEventsDisposable.dispose();
            log.info("Timer Events Consumer 종료됨");
        }
        if (userActionsDisposable != null && !userActionsDisposable.isDisposed()) {
            userActionsDisposable.dispose();
            log.info("User Actions Consumer 종료됨");
        }
    }
    
    /**
     * 이벤트 처리
     * @param event 처리할 이벤트
     * @return 처리 결과
     */
    private Mono<Void> processEvent(TimerEvent event) {
        // 특정 이벤트는 항상 처리 (필터링 제외)
        if (shouldAlwaysProcess(event)) {
            log.info("중요 이벤트 처리 시작: {} - {}", event.getEventType(), event.getTimerId());
            return Mono.when(
                // 1. 이벤트 로그 저장
                saveEventLog(event),
                // 2. WebSocket으로 브로드캐스트
                broadcastToWebSocket(event)
            );
        }
        
        return connectionManager.isServerRelevantForTimer(event.getTimerId(), serverId)
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
            .eventData(null) // 이벤트 데이터는 별도 처리
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
            // 모든 이벤트는 타이머 토픽으로 브로드캐스트
            // 프론트엔드에서 필터링하여 적절한 사용자에게만 표시
            String destination = "/topic/timer/" + event.getTimerId();
            messagingTemplate.convertAndSend(destination, event);
            
            if (event instanceof SharedTimerAccessedEvent) {
                SharedTimerAccessedEvent accessEvent = (SharedTimerAccessedEvent) event;
                log.info("🔔 공유 타이머 접속 이벤트 브로드캐스트: ownerId={}, accessedUserId={}", 
                        accessEvent.getOwnerId(), accessEvent.getAccessedUserId());
            } else {
                log.debug("WebSocket 브로드캐스트 완료: {} -> {}", event.getEventType(), destination);
            }
        });
    }
    
    /**
     * 항상 처리해야 하는 이벤트인지 확인
     * @param event 이벤트
     * @return 항상 처리 여부
     */
    private boolean shouldAlwaysProcess(TimerEvent event) {
        // 모든 사용자에게 전달되어야 하는 중요한 이벤트들
        return "TARGET_TIME_CHANGED".equals(event.getEventType()) ||
               "TIMER_COMPLETED".equals(event.getEventType()) ||
               "SHARED_TIMER_ACCESSED".equals(event.getEventType());
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
