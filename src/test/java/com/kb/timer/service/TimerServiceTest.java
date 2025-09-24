package com.kb.timer.service;

import com.kb.timer.model.entity.Timer;
import com.kb.timer.model.entity.TimestampEntry;
import com.kb.timer.model.dto.TimerResponse;
import com.kb.timer.repository.TimerRepository;
import com.kb.timer.repository.TimestampEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TimerService 핵심 비즈니스 로직 테스트
 * 
 * 테스트 범위:
 * - 타이머 생성 로직
 * - 목표 시간 변경 로직 (권한 검증 포함)
 * - 타임스탬프 저장 로직
 * - 타이머 조회 로직 (남은 시간 계산 포함)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TimerService 핵심 비즈니스 로직 테스트")
class TimerServiceTest {

    @Mock
    private TimerRepository timerRepository;
    
    @Mock
    private TimestampEntryRepository timestampRepository;
    
    @Mock
    private KafkaEventPublisher kafkaEventPublisher;
    
    @Mock
    private RedisConnectionManager connectionManager;
    
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TimerService timerService;

    private final String TEST_SERVER_ID = "test-server-123";
    private final String TEST_OWNER_ID = "owner-123";
    private final String TEST_USER_ID = "user-456";
    private final String TEST_TIMER_ID = "timer-789";

    @BeforeEach
    void setUp() {
        // 서버 ID 설정
        ReflectionTestUtils.setField(timerService, "serverId", TEST_SERVER_ID);
    }

    @Test
    @DisplayName("타이머 생성 - 정상적인 타이머 생성 시 올바른 데이터로 저장되어야 함")
    void createTimer_Success() {
        // Given
        long targetTimeSeconds = 300L; // 5분
        Instant now = Instant.now();
        
        Timer savedTimer = Timer.builder()
                .id(TEST_TIMER_ID)
                .ownerId(TEST_OWNER_ID)
                .targetTime(now.plusSeconds(targetTimeSeconds))
                .createdAt(now)
                .updatedAt(now)
                .completed(false)
                .shareToken("share-token-123")
                .build();

        when(timerRepository.save(any(Timer.class))).thenReturn(Mono.just(savedTimer));

        // When & Then
        StepVerifier.create(timerService.createTimer(targetTimeSeconds, TEST_OWNER_ID))
                .assertNext(timerResponse -> {
                    // 기본 필드 검증
                    assertThat(timerResponse.getTimerId()).isEqualTo(TEST_TIMER_ID);
                    assertThat(timerResponse.getOwnerId()).isEqualTo(TEST_OWNER_ID);
                    assertThat(timerResponse.getUserRole()).isEqualTo("OWNER");
                    assertThat(timerResponse.isCompleted()).isFalse();
                    assertThat(timerResponse.getOnlineUserCount()).isEqualTo(0); // 새로 생성된 타이머는 아직 접속자 없음
                    
                    // 시간 계산 검증
                    assertThat(timerResponse.getTargetTime()).isNotNull();
                    assertThat(timerResponse.getServerTime()).isNotNull();
                    assertThat(timerResponse.getRemainingTime()).isNotNull();
                    
                    // 공유 토큰 검증
                    assertThat(timerResponse.getShareToken()).startsWith("/timer/");
                })
                .verifyComplete();

        // Mock 호출 검증
        verify(timerRepository).save(any(Timer.class));
        verify(eventPublisher).publishEvent(any()); // 스케줄링 이벤트 발행 확인
        // createTimer에서는 Kafka 이벤트를 직접 발행하지 않음
    }

    @Test
    @DisplayName("목표 시간 변경 - 소유자가 변경 시 성공해야 함")
    void changeTargetTime_OwnerCanChange_Success() {
        // Given
        Instant oldTargetTime = Instant.now().plusSeconds(300);
        Instant newTargetTime = Instant.now().plusSeconds(600);
        
        Timer existingTimer = Timer.builder()
                .id(TEST_TIMER_ID)
                .ownerId(TEST_OWNER_ID)
                .targetTime(oldTargetTime)
                .completed(false)
                .build();

        Timer updatedTimer = Timer.builder()
                .id(TEST_TIMER_ID)
                .ownerId(TEST_OWNER_ID)
                .targetTime(newTargetTime)
                .completed(false)
                .build();

        when(timerRepository.findById(TEST_TIMER_ID)).thenReturn(Mono.just(existingTimer));
        when(timerRepository.save(any(Timer.class))).thenReturn(Mono.just(updatedTimer));
        when(kafkaEventPublisher.publishTimerEvent(any())).thenReturn(Mono.empty());
        when(connectionManager.getOnlineUserCount(anyString())).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(timerService.changeTargetTime(TEST_TIMER_ID, newTargetTime, TEST_OWNER_ID))
                .assertNext(timerResponse -> {
                    assertThat(timerResponse.getTimerId()).isEqualTo(TEST_TIMER_ID);
                    assertThat(timerResponse.getTargetTime()).isEqualTo(newTargetTime);
                    assertThat(timerResponse.getUserRole()).isEqualTo("OWNER");
                })
                .verifyComplete();

        // 이벤트 발행 검증
        verify(eventPublisher).publishEvent(any()); // 스케줄 업데이트 이벤트
        verify(kafkaEventPublisher).publishTimerEvent(any()); // 목표시간 변경 이벤트
    }

    @Test
    @DisplayName("목표 시간 변경 - 소유자가 아닌 사용자가 변경 시 실패해야 함")
    void changeTargetTime_NonOwnerCannotChange_Failure() {
        // Given
        Instant newTargetTime = Instant.now().plusSeconds(600);
        
        Timer existingTimer = Timer.builder()
                .id(TEST_TIMER_ID)
                .ownerId(TEST_OWNER_ID) // 다른 소유자
                .targetTime(Instant.now().plusSeconds(300))
                .completed(false)
                .build();

        when(timerRepository.findById(TEST_TIMER_ID)).thenReturn(Mono.just(existingTimer));

        // When & Then
        StepVerifier.create(timerService.changeTargetTime(TEST_TIMER_ID, newTargetTime, TEST_USER_ID))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RuntimeException && 
                    throwable.getMessage().contains("타이머 소유자만 목표 시간을 변경할 수 있습니다"))
                .verify();

        // 저장이 호출되지 않았는지 확인
        verify(timerRepository, never()).save(any(Timer.class));
        verify(kafkaEventPublisher, never()).publishTimerEvent(any());
    }

    @Test
    @DisplayName("목표 시간 변경 - 존재하지 않는 타이머 변경 시 실패해야 함")
    void changeTargetTime_TimerNotFound_Failure() {
        // Given
        Instant newTargetTime = Instant.now().plusSeconds(600);
        when(timerRepository.findById(TEST_TIMER_ID)).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(timerService.changeTargetTime(TEST_TIMER_ID, newTargetTime, TEST_OWNER_ID))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RuntimeException && 
                    throwable.getMessage().contains("타이머를 찾을 수 없습니다"))
                .verify();
    }

    @Test
    @DisplayName("타임스탬프 저장 - 정상적인 저장 시 올바른 데이터로 저장되어야 함")
    void saveTimestamp_Success() {
        // Given
        Instant targetTime = Instant.now().plusSeconds(300);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("note", "중요한 시점");
        
        TimestampEntry savedEntry = TimestampEntry.builder()
                .id("timestamp-123")
                .timerId(TEST_TIMER_ID)
                .userId(TEST_USER_ID)
                .savedAt(Instant.now())
                .remainingTime(Duration.ofSeconds(300))
                .targetTime(targetTime)
                .metadata(metadata)
                .build();

        when(timestampRepository.save(any(TimestampEntry.class))).thenReturn(Mono.just(savedEntry));
        when(kafkaEventPublisher.publishTimerEvent(any())).thenReturn(Mono.empty()); // saveTimestamp는 publishTimerEvent를 호출

        // When & Then
        StepVerifier.create(timerService.saveTimestamp(TEST_TIMER_ID, TEST_USER_ID, targetTime, metadata))
                .assertNext(timestampEntry -> {
                    assertThat(timestampEntry.getTimerId()).isEqualTo(TEST_TIMER_ID);
                    assertThat(timestampEntry.getUserId()).isEqualTo(TEST_USER_ID);
                    assertThat(timestampEntry.getTargetTime()).isEqualTo(targetTime);
                    assertThat(timestampEntry.getMetadata()).containsEntry("note", "중요한 시점");
                    assertThat(timestampEntry.getRemainingTime()).isNotNull();
                    assertThat(timestampEntry.getSavedAt()).isNotNull();
                })
                .verifyComplete();

        // 저장 호출 검증
        verify(timestampRepository).save(any(TimestampEntry.class));
        verify(kafkaEventPublisher).publishTimerEvent(any()); // 타임스탬프 저장 이벤트 발행 확인 (실제로는 publishTimerEvent 호출)
    }

    @Test
    @DisplayName("타이머 조회 - 존재하는 타이머 조회 시 올바른 계산된 데이터 반환")
    void getTimerInfo_ExistingTimer_Success() {
        // Given
        Instant now = Instant.now();
        Instant targetTime = now.plusSeconds(300); // 5분 후
        
        Timer existingTimer = Timer.builder()
                .id(TEST_TIMER_ID)
                .ownerId(TEST_OWNER_ID)
                .targetTime(targetTime)
                .createdAt(now.minusSeconds(60)) // 1분 전 생성
                .completed(false)
                .shareToken("share-token-123")
                .build();

        when(timerRepository.findById(TEST_TIMER_ID)).thenReturn(Mono.just(existingTimer));
        when(connectionManager.getOnlineUserCount(anyString())).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(timerService.getTimerInfo(TEST_TIMER_ID, TEST_OWNER_ID))
                .assertNext(timerResponse -> {
                    // 기본 정보 검증
                    assertThat(timerResponse.getTimerId()).isEqualTo(TEST_TIMER_ID);
                    assertThat(timerResponse.getOwnerId()).isEqualTo(TEST_OWNER_ID);
                    assertThat(timerResponse.getUserRole()).isEqualTo("OWNER");
                    assertThat(timerResponse.isCompleted()).isFalse();
                    
                    // 시간 계산 검증
                    assertThat(timerResponse.getRemainingTime()).isNotNull();
                    assertThat(timerResponse.getServerTime()).isNotNull();
                    assertThat(timerResponse.getTargetTime()).isEqualTo(targetTime);
                    
                    // 남은 시간이 음수가 아닌지 확인
                    assertThat(timerResponse.getRemainingTime()).isNotNull();
                    assertThat(timerResponse.getRemainingTime().isNegative()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("타이머 조회 - 완료된 타이머 조회 시 완료 상태 반환")
    void getTimerInfo_CompletedTimer_ReturnsCompletedStatus() {
        // Given
        Instant now = Instant.now();
        Instant pastTargetTime = now.minusSeconds(60); // 1분 전 (이미 완료됨)
        
        Timer completedTimer = Timer.builder()
                .id(TEST_TIMER_ID)
                .ownerId(TEST_OWNER_ID)
                .targetTime(pastTargetTime)
                .completed(true)
                .completedAt(pastTargetTime)
                .build();

        when(timerRepository.findById(TEST_TIMER_ID)).thenReturn(Mono.just(completedTimer));
        when(connectionManager.getOnlineUserCount(anyString())).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(timerService.getTimerInfo(TEST_TIMER_ID, TEST_USER_ID))
                .assertNext(timerResponse -> {
                    assertThat(timerResponse.isCompleted()).isTrue();
                    assertThat(timerResponse.getUserRole()).isEqualTo("VIEWER"); // 소유자가 아님
                    
                    // 완료된 타이머의 남은 시간은 0이어야 함
                    assertThat(timerResponse.getRemainingTime()).isEqualTo(Duration.ZERO);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("타이머 히스토리 조회 - 사용자별 타임스탬프 목록 반환")
    void getTimerHistory_ReturnsUserTimestamps() {
        // Given
        TimestampEntry entry1 = TimestampEntry.builder()
                .id("ts-1")
                .timerId(TEST_TIMER_ID)
                .userId(TEST_USER_ID)
                .savedAt(Instant.now().minusSeconds(100))
                .build();
                
        TimestampEntry entry2 = TimestampEntry.builder()
                .id("ts-2")
                .timerId(TEST_TIMER_ID)
                .userId(TEST_USER_ID)
                .savedAt(Instant.now().minusSeconds(50))
                .build();

        when(timestampRepository.findByTimerIdOrderByCreatedAtAsc(TEST_TIMER_ID))
                .thenReturn(Flux.just(entry1, entry2));

        // When & Then
        StepVerifier.create(timerService.getTimerHistory(TEST_TIMER_ID))
                .expectNext(entry1)
                .expectNext(entry2)
                .verifyComplete();

        verify(timestampRepository).findByTimerIdOrderByCreatedAtAsc(TEST_TIMER_ID);
    }

    @Test
    @DisplayName("사용자별 타이머 히스토리 조회 - 특정 사용자의 타임스탬프만 반환")
    void getUserTimerHistory_ReturnsSpecificUserTimestamps() {
        // Given
        TimestampEntry userEntry = TimestampEntry.builder()
                .id("ts-user")
                .timerId(TEST_TIMER_ID)
                .userId(TEST_USER_ID)
                .savedAt(Instant.now())
                .build();

        when(timestampRepository.findByTimerIdAndUserIdOrderByCreatedAtAsc(TEST_TIMER_ID, TEST_USER_ID))
                .thenReturn(Flux.just(userEntry));

        // When & Then
        StepVerifier.create(timerService.getUserTimerHistory(TEST_TIMER_ID, TEST_USER_ID))
                .expectNext(userEntry)
                .verifyComplete();

        verify(timestampRepository).findByTimerIdAndUserIdOrderByCreatedAtAsc(TEST_TIMER_ID, TEST_USER_ID);
    }
}
