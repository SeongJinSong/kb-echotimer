package com.kb.timer.service;

import com.kb.timer.model.event.*;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KafkaEventPublisher 테스트
 * 
 * 테스트 범위:
 * - 타이머 이벤트 발행
 * - 사용자 액션 이벤트 발행
 * - Kafka 발행 성공/실패 처리
 * - 올바른 토픽으로 발행 확인
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventPublisher 이벤트 발행 테스트")
class KafkaEventPublisherTest {

    @Mock
    private ReactiveKafkaProducerTemplate<String, TimerEvent> kafkaProducerTemplate;

    @InjectMocks
    private KafkaEventPublisher kafkaEventPublisher;

    private final String TIMER_EVENTS_TOPIC = "timer-events";
    private final String USER_ACTIONS_TOPIC = "user-actions";
    private final String TEST_TIMER_ID = "timer-123";
    private final String TEST_USER_ID = "user-456";

    @BeforeEach
    void setUp() {
        // 토픽 이름 설정
        ReflectionTestUtils.setField(kafkaEventPublisher, "timerEventsTopic", TIMER_EVENTS_TOPIC);
        ReflectionTestUtils.setField(kafkaEventPublisher, "userActionsTopic", USER_ACTIONS_TOPIC);
    }

    @Test
    @DisplayName("타이머 완료 이벤트 발행 - 성공적으로 발행되어야 함")
    void publishTimerEvent_TimerCompletedEvent_Success() {
        // Given
        TimerCompletedEvent event = TimerCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(TEST_TIMER_ID)
                .completedAt(Instant.now())
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 성공 Mock
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TIMER_EVENTS_TOPIC, 0), 
                0L, 0, 0, 0, 0);
        SenderResult<Void> senderResult = mock(SenderResult.class);
        when(senderResult.recordMetadata()).thenReturn(metadata);

        when(kafkaProducerTemplate.send(TIMER_EVENTS_TOPIC, TEST_TIMER_ID, event))
                .thenReturn(Mono.just(senderResult));

        // When & Then
        StepVerifier.create(kafkaEventPublisher.publishTimerEvent(event))
                .verifyComplete();

        // Kafka 발행 호출 확인
        verify(kafkaProducerTemplate).send(TIMER_EVENTS_TOPIC, TEST_TIMER_ID, event);
    }

    @Test
    @DisplayName("목표 시간 변경 이벤트 발행 - 성공적으로 발행되어야 함")
    void publishTimerEvent_TargetTimeChangedEvent_Success() {
        // Given
        TargetTimeChangedEvent event = TargetTimeChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(TEST_TIMER_ID)
                .newTargetTime(Instant.now().plusSeconds(600))
                .oldTargetTime(Instant.now().plusSeconds(300))
                .changedBy(TEST_USER_ID)
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 성공 Mock
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TIMER_EVENTS_TOPIC, 1), 
                0L, 0, 0, 0, 0);
        SenderResult<Void> senderResult = mock(SenderResult.class);
        when(senderResult.recordMetadata()).thenReturn(metadata);

        when(kafkaProducerTemplate.send(TIMER_EVENTS_TOPIC, TEST_TIMER_ID, event))
                .thenReturn(Mono.just(senderResult));

        // When & Then
        StepVerifier.create(kafkaEventPublisher.publishTimerEvent(event))
                .verifyComplete();

        verify(kafkaProducerTemplate).send(TIMER_EVENTS_TOPIC, TEST_TIMER_ID, event);
    }

    @Test
    @DisplayName("사용자 참여 이벤트 발행 - 성공적으로 발행되어야 함")
    void publishUserActionEvent_UserJoinedEvent_Success() {
        // Given
        UserJoinedEvent event = UserJoinedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(TEST_TIMER_ID)
                .userId(TEST_USER_ID)
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 성공 Mock
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(USER_ACTIONS_TOPIC, 0), 
                0L, 0, 0, 0, 0);
        SenderResult<Void> senderResult = mock(SenderResult.class);
        when(senderResult.recordMetadata()).thenReturn(metadata);

        when(kafkaProducerTemplate.send(USER_ACTIONS_TOPIC, TEST_TIMER_ID, event))
                .thenReturn(Mono.just(senderResult));

        // When & Then
        StepVerifier.create(kafkaEventPublisher.publishUserActionEvent(event))
                .verifyComplete();

        verify(kafkaProducerTemplate).send(USER_ACTIONS_TOPIC, TEST_TIMER_ID, event);
    }

    @Test
    @DisplayName("타임스탬프 저장 이벤트 발행 - 성공적으로 발행되어야 함")
    void publishUserActionEvent_TimestampSavedEvent_Success() {
        // Given
        TimestampSavedEvent event = TimestampSavedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(TEST_TIMER_ID)
                .userId(TEST_USER_ID)
                .savedAt(Instant.now())
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 성공 Mock
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(USER_ACTIONS_TOPIC, 1), 
                0L, 0, 0, 0, 0);
        SenderResult<Void> senderResult = mock(SenderResult.class);
        when(senderResult.recordMetadata()).thenReturn(metadata);

        when(kafkaProducerTemplate.send(USER_ACTIONS_TOPIC, TEST_TIMER_ID, event))
                .thenReturn(Mono.just(senderResult));

        // When & Then
        StepVerifier.create(kafkaEventPublisher.publishUserActionEvent(event))
                .verifyComplete();

        verify(kafkaProducerTemplate).send(USER_ACTIONS_TOPIC, TEST_TIMER_ID, event);
    }

    @Test
    @DisplayName("타이머 이벤트 발행 실패 - 에러 처리 확인")
    void publishTimerEvent_KafkaFailure_HandlesError() {
        // Given
        TimerCompletedEvent event = TimerCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(TEST_TIMER_ID)
                .completedAt(Instant.now())
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 실패 Mock
        when(kafkaProducerTemplate.send(TIMER_EVENTS_TOPIC, TEST_TIMER_ID, event))
                .thenReturn(Mono.error(new RuntimeException("Kafka 연결 실패")));

        // When & Then
        StepVerifier.create(kafkaEventPublisher.publishTimerEvent(event))
                .expectError(RuntimeException.class)
                .verify();

        verify(kafkaProducerTemplate).send(TIMER_EVENTS_TOPIC, TEST_TIMER_ID, event);
    }

    @Test
    @DisplayName("사용자 액션 이벤트 발행 실패 - 에러 처리 확인")
    void publishUserActionEvent_KafkaFailure_HandlesError() {
        // Given
        UserLeftEvent event = UserLeftEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(TEST_TIMER_ID)
                .userId(TEST_USER_ID)
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 실패 Mock
        when(kafkaProducerTemplate.send(USER_ACTIONS_TOPIC, TEST_TIMER_ID, event))
                .thenReturn(Mono.error(new RuntimeException("네트워크 오류")));

        // When & Then
        StepVerifier.create(kafkaEventPublisher.publishUserActionEvent(event))
                .expectError(RuntimeException.class)
                .verify();

        verify(kafkaProducerTemplate).send(USER_ACTIONS_TOPIC, TEST_TIMER_ID, event);
    }

    @Test
    @DisplayName("공유 타이머 접속 이벤트 발행 - 성공적으로 발행되어야 함")
    void publishTimerEvent_SharedTimerAccessedEvent_Success() {
        // Given
        SharedTimerAccessedEvent event = SharedTimerAccessedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(TEST_TIMER_ID)
                .accessedUserId(TEST_USER_ID)
                .ownerId("owner-789")
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 성공 Mock
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TIMER_EVENTS_TOPIC, 2), 
                0L, 0, 0, 0, 0);
        SenderResult<Void> senderResult = mock(SenderResult.class);
        when(senderResult.recordMetadata()).thenReturn(metadata);

        when(kafkaProducerTemplate.send(TIMER_EVENTS_TOPIC, TEST_TIMER_ID, event))
                .thenReturn(Mono.just(senderResult));

        // When & Then
        StepVerifier.create(kafkaEventPublisher.publishTimerEvent(event))
                .verifyComplete();

        verify(kafkaProducerTemplate).send(TIMER_EVENTS_TOPIC, TEST_TIMER_ID, event);
    }

    @Test
    @DisplayName("이벤트 자동 라우팅 - 사용자 액션 이벤트는 user-actions 토픽으로")
    void publishEvent_UserActionEvent_RoutesToUserActionsTopic() {
        // Given
        UserJoinedEvent event = UserJoinedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(TEST_TIMER_ID)
                .userId(TEST_USER_ID)
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 성공 Mock
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(USER_ACTIONS_TOPIC, 0), 
                0L, 0, 0, 0, 0);
        SenderResult<Void> senderResult = mock(SenderResult.class);
        when(senderResult.recordMetadata()).thenReturn(metadata);

        when(kafkaProducerTemplate.send(USER_ACTIONS_TOPIC, TEST_TIMER_ID, event))
                .thenReturn(Mono.just(senderResult));

        // When
        StepVerifier.create(kafkaEventPublisher.publishEvent(event))
                .verifyComplete();

        // Then - user-actions 토픽으로 발행되었는지 확인
        verify(kafkaProducerTemplate).send(eq(USER_ACTIONS_TOPIC), eq(TEST_TIMER_ID), eq(event));
        verify(kafkaProducerTemplate, never()).send(eq(TIMER_EVENTS_TOPIC), anyString(), any());
    }

    @Test
    @DisplayName("이벤트 자동 라우팅 - 타이머 이벤트는 timer-events 토픽으로")
    void publishEvent_TimerEvent_RoutesToTimerEventsTopic() {
        // Given
        TimerCompletedEvent event = TimerCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(TEST_TIMER_ID)
                .completedAt(Instant.now())
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 성공 Mock
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TIMER_EVENTS_TOPIC, 0), 
                0L, 0, 0, 0, 0);
        SenderResult<Void> senderResult = mock(SenderResult.class);
        when(senderResult.recordMetadata()).thenReturn(metadata);

        when(kafkaProducerTemplate.send(TIMER_EVENTS_TOPIC, TEST_TIMER_ID, event))
                .thenReturn(Mono.just(senderResult));

        // When
        StepVerifier.create(kafkaEventPublisher.publishEvent(event))
                .verifyComplete();

        // Then - timer-events 토픽으로 발행되었는지 확인
        verify(kafkaProducerTemplate).send(eq(TIMER_EVENTS_TOPIC), eq(TEST_TIMER_ID), eq(event));
        verify(kafkaProducerTemplate, never()).send(eq(USER_ACTIONS_TOPIC), anyString(), any());
    }

    @Test
    @DisplayName("이벤트 키 검증 - 타이머 ID가 올바른 키로 사용되는지 확인")
    void publishEvent_UsesCorrectKey() {
        // Given
        String customTimerId = "custom-timer-999";
        TimerCompletedEvent event = TimerCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timerId(customTimerId)
                .completedAt(Instant.now())
                .originServerId("server-123")
                .timestamp(Instant.now())
                .build();

        // Kafka 발행 성공 Mock
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TIMER_EVENTS_TOPIC, 0), 
                0L, 0, 0, 0, 0);
        SenderResult<Void> senderResult = mock(SenderResult.class);
        when(senderResult.recordMetadata()).thenReturn(metadata);

        when(kafkaProducerTemplate.send(TIMER_EVENTS_TOPIC, customTimerId, event))
                .thenReturn(Mono.just(senderResult));

        // When
        StepVerifier.create(kafkaEventPublisher.publishTimerEvent(event))
                .verifyComplete();

        // Then - 올바른 키(타이머 ID)로 발행되었는지 확인
        verify(kafkaProducerTemplate).send(TIMER_EVENTS_TOPIC, customTimerId, event);
    }
}