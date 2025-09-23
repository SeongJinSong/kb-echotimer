package com.kb.timer.service;

import com.kb.timer.model.dto.SessionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis 연결 상태 관리 서비스
 * WebSocket 연결 상태를 Redis에서 관리하는 핵심 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisConnectionManager {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ReactiveRedisTemplate<String, String> stringRedisTemplate;
    
    /**
     * 사용자 연결 상태 기록
     * WebSocket 연결 시 호출
     */
    public Mono<Void> recordUserConnection(String timerId, String userId, String serverId, String sessionId) {
        SessionInfo sessionInfo = SessionInfo.builder()
            .sessionId(sessionId)
            .timerId(timerId)
            .userId(userId)
            .serverId(serverId)
            .connectedAt(Instant.now())
            .lastHeartbeat(Instant.now())
            .build();
        
        return Mono.when(
            // 1. 타이머별 온라인 사용자 목록에 추가
            stringRedisTemplate.opsForSet().add("timer:" + timerId + ":online_users", userId)
                .doOnNext(added -> {
                    log.info("Redis SET 추가: timer:{}:online_users, userId={}, added={}", timerId, userId, added);
                    if (added == 0) {
                        log.warn("사용자가 이미 존재함: userId={}, timerId={}", userId, timerId);
                    }
                }),
            
            // 2. 사용자별 연결 서버 정보 저장
            stringRedisTemplate.opsForValue().set("user:" + userId + ":server", serverId)
                .doOnNext(result -> log.info("Redis VALUE 저장: user:{}:server = {}", userId, serverId)),
            
            // 3. 서버별 연결된 사용자 목록에 추가
            stringRedisTemplate.opsForSet().add("server:" + serverId + ":users", userId)
                .doOnNext(added -> log.info("Redis SET 추가: server:{}:users, userId={}, added={}", serverId, userId, added)),
            
            // 4. 세션 정보 저장 (TTL 1시간)
            redisTemplate.opsForValue().set("session:" + sessionId, sessionInfo, Duration.ofHours(1))
                .doOnNext(result -> log.info("Redis SESSION 저장: session:{} = {}", sessionId, sessionInfo))
        ).doOnSuccess(ignored -> 
            log.info("User connection recorded: timerId={}, userId={}, serverId={}, sessionId={}", 
                timerId, userId, serverId, sessionId)
        ).doOnError(e -> 
            log.error("User connection record failed: timerId={}, userId={}, error={}", 
                timerId, userId, e.getMessage(), e)
        );
    }
    
    /**
     * 사용자 연결 해제 처리
     * WebSocket 연결 해제 시 호출
     */
    public Mono<SessionInfo> removeUserConnection(String sessionId) {
        return redisTemplate.opsForValue().get("session:" + sessionId)
            .cast(SessionInfo.class)
            .flatMap(sessionInfo -> {
                String timerId = sessionInfo.getTimerId();
                String userId = sessionInfo.getUserId();
                String serverId = sessionInfo.getServerId();
                
                return Mono.when(
                    // 1. 타이머별 온라인 사용자에서 제거
                    stringRedisTemplate.opsForSet().remove("timer:" + timerId + ":online_users", userId),
                    
                    // 2. 사용자별 서버 정보 삭제
                    stringRedisTemplate.delete("user:" + userId + ":server"),
                    
                    // 3. 서버별 사용자 목록에서 제거
                    stringRedisTemplate.opsForSet().remove("server:" + serverId + ":users", userId),
                    
                    // 4. 세션 정보 삭제
                    redisTemplate.delete("session:" + sessionId)
                ).thenReturn(sessionInfo)
                 .doOnSuccess(ignored -> 
                     log.debug("User connection removed: timerId={}, userId={}, serverId={}", 
                         timerId, userId, serverId)
                 );
            });
    }
    
    /**
     * 특정 타이머에 현재 서버가 관련되어 있는지 확인
     * Kafka Consumer에서 필터링용으로 사용하는 핵심 메서드
     */
    public Mono<Boolean> isServerRelevantForTimer(String timerId, String serverId) {
        return stringRedisTemplate.opsForSet()
            .intersect("timer:" + timerId + ":online_users", "server:" + serverId + ":users")
            .hasElements()
            .doOnNext(isRelevant -> {
                if (log.isTraceEnabled()) {
                    log.trace("Server relevance check: timerId={}, serverId={}, relevant={}", 
                        timerId, serverId, isRelevant);
                }
            });
    }
    
    /**
     * 특정 타이머의 온라인 사용자 목록 조회
     */
    public Flux<String> getOnlineUsers(String timerId) {
        return stringRedisTemplate.opsForSet()
            .members("timer:" + timerId + ":online_users")
            .cast(String.class);
    }
    
    /**
     * 특정 타이머의 온라인 사용자 수 조회
     */
    public Mono<Long> getOnlineUserCount(String timerId) {
        return stringRedisTemplate.opsForSet()
            .size("timer:" + timerId + ":online_users")
            .doOnNext(count -> log.info("온라인 사용자 수 조회: timerId={}, count={}", timerId, count))
            .doOnError(e -> log.error("온라인 사용자 수 조회 실패: timerId={}, error={}", timerId, e.getMessage()));
    }
    
    /**
     * 특정 타이머에 연결된 서버 목록 조회
     */
    public Flux<String> getConnectedServers(String timerId) {
        return getOnlineUsers(timerId)
            .flatMap(userId -> 
                stringRedisTemplate.opsForValue().get("user:" + userId + ":server")
            )
            .distinct();
    }
    
    /**
     * 세션 정보 조회
     */
    public Mono<SessionInfo> getSessionInfo(String sessionId) {
        return redisTemplate.opsForValue().get("session:" + sessionId)
            .cast(SessionInfo.class);
    }
    
    /**
     * 하트비트 업데이트
     * 클라이언트 연결 상태 확인용
     */
    public Mono<Void> updateHeartbeat(String sessionId) {
        return getSessionInfo(sessionId)
            .flatMap(sessionInfo -> {
                sessionInfo.setLastHeartbeat(Instant.now());
                return redisTemplate.opsForValue()
                    .set("session:" + sessionId, sessionInfo, Duration.ofHours(1));
            })
            .then();
    }
}
