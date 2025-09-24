package com.kb.timer.service;

import com.kb.timer.model.entity.Timer;
import com.kb.timer.model.entity.TimerCompletionLog;
import com.kb.timer.repository.TimerCompletionLogRepository;
import com.kb.timer.repository.TimerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 타이머 완료 모니터링 서비스
 * 누락된 타이머 완료 처리를 감지하고 알림
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimerCompletionMonitoringService {

    private final TimerRepository timerRepository;
    private final TimerCompletionLogRepository completionLogRepository;

    /**
     * 누락된 타이머 완료 처리 감지 (1분마다 실행)
     * 
     * 감지 로직:
     * 1. 현재 시간 기준 과거 5분 이내에 완료되어야 했던 타이머들 조회
     * 2. 해당 타이머들의 완료 로그 존재 여부 확인
     * 3. 완료 로그가 없거나 실패한 타이머들을 누락으로 판단
     */
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void detectMissedTimerCompletions() {
        Instant now = Instant.now();
        Instant fiveMinutesAgo = now.minus(5, ChronoUnit.MINUTES);
        
        log.debug("누락된 타이머 완료 처리 감지 시작: 검사 범위={} ~ {}", fiveMinutesAgo, now);
        
        // 1. 과거 5분 이내에 완료되어야 했던 타이머들 조회
        timerRepository.findByCompletedFalseAndTargetTimeBefore(now)
                .filter(timer -> timer.getTargetTime().isAfter(fiveMinutesAgo)) // 5분 이내
                .collectList()
                .flatMap(expiredTimers -> {
                    if (expiredTimers.isEmpty()) {
                        log.debug("검사 대상 타이머 없음");
                        return Mono.empty();
                    }
                    
                    log.debug("검사 대상 타이머 {}개 발견", expiredTimers.size());
                    
                    // 2. 각 타이머의 완료 로그 확인
                    return checkTimerCompletionLogs(expiredTimers);
                })
                .doOnError(error -> log.error("누락된 타이머 완료 처리 감지 실패: {}", error.getMessage(), error))
                .subscribe();
    }

    /**
     * 타이머들의 완료 로그 확인
     * 
     * @param expiredTimers 만료된 타이머 목록
     * @return 처리 결과
     */
    private Mono<Void> checkTimerCompletionLogs(List<Timer> expiredTimers) {
        return Flux.fromIterable(expiredTimers)
                .flatMap(timer -> 
                    completionLogRepository.existsByTimerIdAndSuccessTrue(timer.getId())
                            .map(hasSuccessfulLog -> new TimerLogStatus(timer, hasSuccessfulLog))
                )
                .collectList()
                .flatMap(this::reportMissedTimers);
    }

    /**
     * 누락된 타이머들 보고
     * 
     * @param timerLogStatuses 타이머 로그 상태 목록
     * @return 처리 결과
     */
    private Mono<Void> reportMissedTimers(List<TimerLogStatus> timerLogStatuses) {
        List<Timer> missedTimers = timerLogStatuses.stream()
                .filter(status -> !status.hasSuccessfulLog)
                .map(status -> status.timer)
                .toList();
        
        if (missedTimers.isEmpty()) {
            log.debug("누락된 타이머 없음 - 모든 타이머가 정상 처리됨");
            return Mono.empty();
        }
        
        // 누락된 타이머들 에러 로그 출력
        log.error("누락된 타이머 완료 처리 감지! 총 {}개 타이머", missedTimers.size());
        
        for (Timer missedTimer : missedTimers) {
            Instant targetTime = missedTimer.getTargetTime();
            Instant now = Instant.now();
            long delayMinutes = ChronoUnit.MINUTES.between(targetTime, now);
            
            log.error("❌ 누락된 타이머: timerId={}, targetTime={}, 지연시간={}분, ownerId={}", 
                    missedTimer.getId(), 
                    targetTime, 
                    delayMinutes,
                    missedTimer.getOwnerId());
        }
        
        // 상세 분석을 위한 추가 로그
        return analyzeFailureReasons(missedTimers);
    }

    /**
     * 실패 원인 분석
     * 
     * @param missedTimers 누락된 타이머 목록
     * @return 분석 결과
     */
    private Mono<Void> analyzeFailureReasons(List<Timer> missedTimers) {
        return Flux.fromIterable(missedTimers)
                .flatMap(timer -> 
                    completionLogRepository.findByTimerId(timer.getId())
                            .collectList()
                            .map(logs -> new TimerAnalysis(timer, logs))
                )
                .collectList()
                .doOnNext(analyses -> {
                    log.error("📊 누락 타이머 상세 분석:");
                    
                    for (TimerAnalysis analysis : analyses) {
                        Timer timer = analysis.timer;
                        List<TimerCompletionLog> logs = analysis.completionLogs;
                        
                        if (logs.isEmpty()) {
                            log.error("  - timerId={}: Keyspace Notification 자체를 받지 못함 (Redis 연결 문제 가능성)", 
                                    timer.getId());
                        } else {
                            TimerCompletionLog latestLog = logs.get(logs.size() - 1);
                            
                            if (!latestLog.isLockAcquired()) {
                                log.error("  - timerId={}: 분산 락 획득 실패 (다른 서버에서 처리 중이었으나 실패)", 
                                        timer.getId());
                            } else if (!latestLog.isSuccess()) {
                                log.error("  - timerId={}: 처리 시도했으나 실패 - {}", 
                                        timer.getId(), latestLog.getErrorMessage());
                            } else {
                                log.error("  - timerId={}: 로그상 성공이지만 Timer 엔티티 미업데이트 (데이터 불일치)", 
                                        timer.getId());
                            }
                        }
                    }
                })
                .then();
    }

    /**
     * 최근 완료 처리 통계 조회 (디버깅용)
     * 
     * @return 통계 정보
     */
    public Mono<CompletionStatistics> getRecentCompletionStatistics() {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        
        return Mono.zip(
                completionLogRepository.findByProcessingStartedAtAfter(oneHourAgo).count(),
                completionLogRepository.findBySuccessTrueAndProcessingStartedAtAfter(oneHourAgo).count(),
                completionLogRepository.findBySuccessFalseAndCreatedAtAfter(oneHourAgo).count()
        ).map(tuple -> CompletionStatistics.builder()
                .totalAttempts(tuple.getT1())
                .successfulCompletions(tuple.getT2())
                .failedAttempts(tuple.getT3())
                .successRate(tuple.getT1() > 0 ? (double) tuple.getT2() / tuple.getT1() * 100 : 0.0)
                .build());
    }

    /**
     * 타이머 로그 상태 내부 클래스
     */
    private static class TimerLogStatus {
        final Timer timer;
        final boolean hasSuccessfulLog;
        
        TimerLogStatus(Timer timer, boolean hasSuccessfulLog) {
            this.timer = timer;
            this.hasSuccessfulLog = hasSuccessfulLog;
        }
    }

    /**
     * 타이머 분석 내부 클래스
     */
    private static class TimerAnalysis {
        final Timer timer;
        final List<TimerCompletionLog> completionLogs;
        
        TimerAnalysis(Timer timer, List<TimerCompletionLog> completionLogs) {
            this.timer = timer;
            this.completionLogs = completionLogs;
        }
    }

    /**
     * 완료 통계 정보
     */
    public static class CompletionStatistics {
        private final long totalAttempts;
        private final long successfulCompletions;
        private final long failedAttempts;
        private final double successRate;
        
        private CompletionStatistics(long totalAttempts, long successfulCompletions, 
                                   long failedAttempts, double successRate) {
            this.totalAttempts = totalAttempts;
            this.successfulCompletions = successfulCompletions;
            this.failedAttempts = failedAttempts;
            this.successRate = successRate;
        }
        
        public static CompletionStatisticsBuilder builder() {
            return new CompletionStatisticsBuilder();
        }
        
        // Getters
        public long getTotalAttempts() { return totalAttempts; }
        public long getSuccessfulCompletions() { return successfulCompletions; }
        public long getFailedAttempts() { return failedAttempts; }
        public double getSuccessRate() { return successRate; }
        
        public static class CompletionStatisticsBuilder {
            private long totalAttempts;
            private long successfulCompletions;
            private long failedAttempts;
            private double successRate;
            
            public CompletionStatisticsBuilder totalAttempts(long totalAttempts) {
                this.totalAttempts = totalAttempts;
                return this;
            }
            
            public CompletionStatisticsBuilder successfulCompletions(long successfulCompletions) {
                this.successfulCompletions = successfulCompletions;
                return this;
            }
            
            public CompletionStatisticsBuilder failedAttempts(long failedAttempts) {
                this.failedAttempts = failedAttempts;
                return this;
            }
            
            public CompletionStatisticsBuilder successRate(double successRate) {
                this.successRate = successRate;
                return this;
            }
            
            public CompletionStatistics build() {
                return new CompletionStatistics(totalAttempts, successfulCompletions, failedAttempts, successRate);
            }
        }
    }
}
