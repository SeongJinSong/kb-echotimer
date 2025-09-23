package com.kb.timer.controller;

import com.kb.timer.model.dto.*;
import com.kb.timer.model.entity.TimestampEntry;
import com.kb.timer.service.TimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;

/**
 * 타이머 관련 REST API 컨트롤러
 * HTTP 기반의 타이머 생성, 조회, 히스토리 등의 기능 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/timers")
@RequiredArgsConstructor
public class TimerRestController {

    private final TimerService timerService;

    /**
     * 새로운 타이머를 생성합니다.
     * 
     * @param request 타이머 생성 요청
     * @return 생성된 타이머 정보
     */
    @PostMapping
    public Mono<ResponseEntity<TimerResponse>> createTimer(@Valid @RequestBody CreateTimerRequest request) {
        log.info("REST API - 타이머 생성 요청: targetTime={}초, ownerId={}", 
                request.getTargetTimeSeconds(), request.getOwnerId());
        
        return timerService.createTimer(request.getTargetTimeSeconds(), request.getOwnerId())
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("REST API - 타이머 생성 완료: {}", 
                        response.getBody().getTimerId()))
                .doOnError(error -> log.error("REST API - 타이머 생성 실패: {}", error.getMessage(), error));
    }

    /**
     * 특정 타이머의 정보를 조회합니다.
     * 
     * @param timerId 타이머 ID
     * @param userId 요청 사용자 ID (쿼리 파라미터)
     * @return 타이머 정보
     */
    @GetMapping("/{timerId}")
    public Mono<ResponseEntity<TimerResponse>> getTimer(
            @PathVariable String timerId,
            @RequestParam(required = false, defaultValue = "anonymous") String userId) {
        
        log.info("REST API - 타이머 정보 조회: timerId={}, userId={}", timerId, userId);
        
        return timerService.getTimerInfo(timerId, userId)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("REST API - 타이머 정보 조회 완료: {}", timerId))
                .doOnError(error -> log.error("REST API - 타이머 정보 조회 실패: timerId={}, error={}", 
                        timerId, error.getMessage(), error));
    }

    /**
     * 공유 토큰으로 타이머 정보를 조회합니다.
     *
     * @param shareToken 공유 토큰
     * @param userId     요청 사용자 ID
     * @return 타이머 정보
     */
    @GetMapping("/shared/{shareToken}")
    public Mono<ResponseEntity<TimerResponse>> getTimerByShareToken(
            @PathVariable String shareToken,
            @RequestParam(required = false, defaultValue = "anonymous") String userId) {

        log.info("REST API - 공유 타이머 정보 조회 요청: shareToken={}, userId={}", shareToken, userId);

        return timerService.getTimerInfoByShareToken(shareToken, userId)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("REST API - 공유 타이머 정보 조회 완료: {}", shareToken))
                .doOnError(error -> log.error("REST API - 공유 타이머 정보 조회 실패: shareToken={}, error={}",
                        shareToken, error.getMessage(), error));
    }

    /**
     * 타이머의 목표 시간을 변경합니다.
     * 
     * @param timerId 타이머 ID
     * @param request 변경 요청
     * @return 업데이트된 타이머 정보
     */
    @PutMapping("/{timerId}/target-time")
    public Mono<ResponseEntity<TimerResponse>> changeTargetTime(
            @PathVariable String timerId,
            @Valid @RequestBody ChangeTargetTimeRequest request) {
        
        log.info("REST API - 목표 시간 변경 요청: timerId={}, newTargetTime={}, changedBy={}", 
                timerId, request.getNewTargetTime(), request.getChangedBy());
        
        return timerService.changeTargetTime(timerId, request.getNewTargetTime(), request.getChangedBy())
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("REST API - 목표 시간 변경 완료: timerId={}", timerId))
                .doOnError(error -> log.error("REST API - 목표 시간 변경 실패: timerId={}, error={}", 
                        timerId, error.getMessage(), error));
    }

    /**
     * 타이머에 타임스탬프를 저장합니다.
     * 
     * @param timerId 타이머 ID
     * @param request 저장 요청
     * @return 저장된 타임스탬프 엔트리
     */
    @PostMapping("/{timerId}/timestamps")
    public Mono<ResponseEntity<TimestampEntry>> saveTimestamp(
            @PathVariable String timerId,
            @Valid @RequestBody SaveTimestampRequest request) {
        
        log.info("REST API - 타임스탬프 저장 요청: timerId={}, userId={}, targetTime={}", 
                timerId, request.getUserId(), request.getTargetTime());
        
        return timerService.saveTimestamp(timerId, request.getUserId(), request.getTargetTime(), request.getMetadata())
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("REST API - 타임스탬프 저장 완료: timerId={}, userId={}", 
                        timerId, request.getUserId()))
                .doOnError(error -> log.error("REST API - 타임스탬프 저장 실패: timerId={}, userId={}, error={}", 
                        timerId, request.getUserId(), error.getMessage(), error));
    }

    /**
     * 특정 타이머의 타임스탬프 히스토리를 조회합니다.
     * 
     * @param timerId 타이머 ID
     * @return 타임스탬프 엔트리 목록
     */
    @GetMapping("/{timerId}/history")
    public Flux<TimestampEntry> getTimerHistory(@PathVariable String timerId) {
        log.info("REST API - 타이머 히스토리 조회: timerId={}", timerId);
        
        return timerService.getTimerHistory(timerId)
                .doOnNext(entry -> log.debug("REST API - 타임스탬프 엔트리: {}", entry.getId()))
                .doOnComplete(() -> log.info("REST API - 타이머 히스토리 조회 완료: timerId={}", timerId))
                .doOnError(error -> log.error("REST API - 타이머 히스토리 조회 실패: timerId={}, error={}", 
                        timerId, error.getMessage(), error));
    }

    /**
     * 특정 사용자의 타임스탬프 히스토리를 조회합니다.
     * 
     * @param timerId 타이머 ID
     * @param userId 사용자 ID
     * @return 사용자별 타임스탬프 목록
     */
    @GetMapping("/{timerId}/history/{userId}")
    public Flux<TimestampEntry> getUserTimerHistory(@PathVariable String timerId, @PathVariable String userId) {
        log.info("REST API - 사용자별 타이머 히스토리 조회: timerId={}, userId={}", timerId, userId);
        
        return timerService.getUserTimerHistory(timerId, userId)
                .doOnNext(entry -> log.debug("REST API - 사용자 타임스탬프 엔트리: {}", entry.getId()))
                .doOnComplete(() -> log.info("REST API - 사용자별 타이머 히스토리 조회 완료: timerId={}, userId={}", timerId, userId))
                .doOnError(error -> log.error("REST API - 사용자별 타이머 히스토리 조회 실패: timerId={}, userId={}, error={}", 
                        timerId, userId, error.getMessage(), error));
    }

    /**
     * 타이머 완료를 알립니다.
     * 
     * @param timerId 타이머 ID
     * @return 완료 처리 결과
     */
    @PostMapping("/{timerId}/complete")
    public Mono<ResponseEntity<String>> completeTimer(@PathVariable String timerId) {
        log.info("REST API - 타이머 완료 요청: timerId={}", timerId);
        
        return timerService.publishTimerCompletedEvent(timerId)
                .then(Mono.just(ResponseEntity.ok("Timer completed successfully")))
                .doOnSuccess(response -> log.info("REST API - 타이머 완료 처리 완료: timerId={}", timerId))
                .doOnError(error -> log.error("REST API - 타이머 완료 처리 실패: timerId={}, error={}", 
                        timerId, error.getMessage(), error));
    }

    /**
     * 헬스체크 엔드포인트
     * 
     * @return 서비스 상태
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "kb-timer-service",
                "timestamp", Instant.now().toString()
        )));
    }
}
