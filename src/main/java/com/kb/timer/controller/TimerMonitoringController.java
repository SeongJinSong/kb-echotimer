package com.kb.timer.controller;

import com.kb.timer.service.TimerCompletionMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 타이머 모니터링 REST API 컨트롤러
 * 타이머 완료 처리 상태 및 통계 조회
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
@Slf4j
public class TimerMonitoringController {

    private final TimerCompletionMonitoringService monitoringService;

    /**
     * 최근 타이머 완료 처리 통계 조회
     * 
     * @return 완료 처리 통계
     */
    @GetMapping("/completion-stats")
    public Mono<TimerCompletionMonitoringService.CompletionStatistics> getCompletionStatistics() {
        log.info("타이머 완료 처리 통계 조회 요청");
        
        return monitoringService.getRecentCompletionStatistics()
                .doOnNext(stats -> log.info("완료 처리 통계 조회 완료: 총시도={}, 성공={}, 실패={}, 성공률={}%", 
                        stats.getTotalAttempts(), stats.getSuccessfulCompletions(), 
                        stats.getFailedAttempts(), String.format("%.1f", stats.getSuccessRate())));
    }

    /**
     * 수동으로 누락된 타이머 감지 실행
     * 
     * @return 실행 결과
     */
    @PostMapping("/detect-missed-timers")
    public Mono<Map<String, Object>> detectMissedTimers() {
        log.info("수동 누락 타이머 감지 실행 요청");
        
        return Mono.fromRunnable(() -> {
            // 모니터링 서비스의 감지 로직을 수동으로 실행
            monitoringService.detectMissedTimerCompletions();
        })
        .then(Mono.fromCallable(() -> {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("message", "누락된 타이머 감지가 실행되었습니다. 로그를 확인하세요.");
            result.put("timestamp", java.time.Instant.now().toString());
            return result;
        }))
        .doOnNext(result -> log.info("수동 누락 타이머 감지 실행 완료"));
    }

    /**
     * 모니터링 서비스 상태 확인
     * 
     * @return 서비스 상태
     */
    @GetMapping("/health")
    public Mono<Map<String, Object>> getMonitoringHealth() {
        return Mono.fromCallable(() -> {
            return Map.of(
                "status", "healthy",
                "service", "TimerCompletionMonitoringService",
                "description", "1분마다 누락된 타이머 완료 처리를 감지합니다",
                "checkInterval", "60초",
                "detectionWindow", "5분",
                "timestamp", java.time.Instant.now().toString()
            );
        });
    }
}
