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
import java.util.HashMap;
import java.util.Map;

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
    
    // TTL 설정 상수들
    private static final Duration SESSION_TTL = Duration.ofHours(2);      // 세션 정보: 2시간
    private static final Duration USER_SERVER_TTL = Duration.ofHours(1);  // 사용자-서버 매핑: 1시간  
    private static final Duration TIMER_USERS_TTL = Duration.ofMinutes(30); // 타이머 사용자 목록: 30분
    private static final Duration SERVER_USERS_TTL = Duration.ofMinutes(45); // 서버 사용자 목록: 45분
    
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
            // 1. 타이머별 온라인 사용자 목록에 추가 + TTL 설정
            stringRedisTemplate.opsForSet().add("timer:" + timerId + ":online_users", userId)
                .doOnNext(added -> {
                    log.info("Redis SET 추가: timer:{}:online_users, userId={}, added={}", timerId, userId, added);
                    if (added == 0) {
                        log.warn("사용자가 이미 존재함: userId={}, timerId={}", userId, timerId);
                    }
                })
                .then(stringRedisTemplate.expire("timer:" + timerId + ":online_users", TIMER_USERS_TTL))
                .doOnNext(success -> log.debug("타이머 사용자 목록 TTL 설정: timer:{}:online_users, ttl={}분", 
                    timerId, TIMER_USERS_TTL.toMinutes())),
            
            // 2. 사용자별 연결 서버 정보 저장 + TTL 설정
            stringRedisTemplate.opsForValue().set("user:" + userId + ":server", serverId, USER_SERVER_TTL)
                .doOnNext(result -> log.info("Redis VALUE 저장: user:{}:server = {} (TTL: {}분)", 
                    userId, serverId, USER_SERVER_TTL.toMinutes())),
            
            // 3. 서버별 연결된 사용자 목록에 추가 + TTL 설정
            stringRedisTemplate.opsForSet().add("server:" + serverId + ":users", userId)
                .doOnNext(added -> log.info("Redis SET 추가: server:{}:users, userId={}, added={}", serverId, userId, added))
                .then(stringRedisTemplate.expire("server:" + serverId + ":users", SERVER_USERS_TTL))
                .doOnNext(success -> log.debug("서버 사용자 목록 TTL 설정: server:{}:users, ttl={}분", 
                    serverId, SERVER_USERS_TTL.toMinutes())),
            
            // 4. 세션 정보 저장 + TTL 설정
            redisTemplate.opsForValue().set("session:" + sessionId, sessionInfo, SESSION_TTL)
                .doOnNext(result -> log.info("Redis SESSION 저장: session:{} = {} (TTL: {}시간)", 
                    sessionId, sessionInfo, SESSION_TTL.toHours()))
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
     * 클라이언트 연결 상태 확인용 + TTL 갱신
     */
    public Mono<Void> updateHeartbeat(String sessionId) {
        return getSessionInfo(sessionId)
            .flatMap(sessionInfo -> {
                sessionInfo.setLastHeartbeat(Instant.now());
                
                // 세션 정보 업데이트 + TTL 갱신
                return redisTemplate.opsForValue()
                    .set("session:" + sessionId, sessionInfo, SESSION_TTL)
                    .then(refreshUserTTL(sessionInfo.getTimerId(), sessionInfo.getUserId(), 
                                       sessionInfo.getServerId(), sessionId));
            });
    }

    /**
     * 사용자 연결 해제 처리 (타이머 ID, 사용자 ID 기반)
     * 특정 타이머에서 특정 사용자를 강제로 제거할 때 사용
     */
    public Mono<Void> removeUserConnection(String timerId, String userId) {
        log.info("사용자 연결 강제 해제: timerId={}, userId={}", timerId, userId);
        
        return Mono.when(
            // 1. 타이머별 온라인 사용자에서 제거
            stringRedisTemplate.opsForSet().remove("timer:" + timerId + ":online_users", userId)
                .doOnNext(removed -> log.info("Redis SET에서 제거: timer:{}:online_users, userId={}, removed={}", 
                        timerId, userId, removed)),
            
            // 2. 사용자별 연결 서버 정보 삭제
            stringRedisTemplate.delete("user:" + userId + ":server")
                .doOnNext(deleted -> log.debug("사용자 서버 정보 삭제: user:{}:server, deleted={}", userId, deleted))
        )
        .doOnSuccess(ignored -> log.info("사용자 연결 강제 해제 완료: timerId={}, userId={}", timerId, userId))
        .doOnError(error -> log.error("사용자 연결 강제 해제 실패: timerId={}, userId={}, error={}", 
                timerId, userId, error.getMessage(), error));
    }

    /**
     * 만료된 연결 정리
     * 주기적으로 호출하여 오래된 세션 정보를 정리
     */
    public Mono<Void> cleanupExpiredConnections() {
        log.debug("만료된 연결 정리 시작");
        
        // 현재는 단순히 성공 반환 (실제로는 TTL 기반으로 자동 정리됨)
        return Mono.<Void>empty()
                .doOnSuccess(ignored -> log.debug("만료된 연결 정리 완료"))
                .doOnError(error -> log.error("만료된 연결 정리 실패: {}", error.getMessage(), error));
    }
    
    /**
     * 활성 사용자의 TTL 갱신 (헬스체크)
     * WebSocket 하트비트나 활동 감지 시 호출
     */
    public Mono<Void> refreshUserTTL(String timerId, String userId, String serverId, String sessionId) {
        log.debug("사용자 TTL 갱신: timerId={}, userId={}, serverId={}, sessionId={}", 
            timerId, userId, serverId, sessionId);
        
        return Mono.when(
            // 1. 타이머 사용자 목록 TTL 갱신
            stringRedisTemplate.expire("timer:" + timerId + ":online_users", TIMER_USERS_TTL)
                .doOnNext(success -> log.trace("타이머 사용자 목록 TTL 갱신: timer:{}:online_users", timerId)),
            
            // 2. 사용자-서버 매핑 TTL 갱신  
            stringRedisTemplate.expire("user:" + userId + ":server", USER_SERVER_TTL)
                .doOnNext(success -> log.trace("사용자 서버 정보 TTL 갱신: user:{}:server", userId)),
            
            // 3. 서버 사용자 목록 TTL 갱신
            stringRedisTemplate.expire("server:" + serverId + ":users", SERVER_USERS_TTL)
                .doOnNext(success -> log.trace("서버 사용자 목록 TTL 갱신: server:{}:users", serverId)),
            
            // 4. 세션 정보 TTL 갱신
            redisTemplate.expire("session:" + sessionId, SESSION_TTL)
                .doOnNext(success -> log.trace("세션 정보 TTL 갱신: session:{}", sessionId))
        )
        .doOnSuccess(ignored -> log.debug("사용자 TTL 갱신 완료: userId={}", userId))
        .doOnError(error -> log.warn("사용자 TTL 갱신 실패: userId={}, error={}", userId, error.getMessage()));
    }
    
    /**
     * TTL 상태 조회 (디버깅용)
     */
    public Mono<Map<String, Long>> getTTLStatus(String timerId, String userId, String serverId, String sessionId) {
        return Mono.zip(
            stringRedisTemplate.getExpire("timer:" + timerId + ":online_users")
                .map(Duration::getSeconds).defaultIfEmpty(-1L),
            stringRedisTemplate.getExpire("user:" + userId + ":server")
                .map(Duration::getSeconds).defaultIfEmpty(-1L),
            stringRedisTemplate.getExpire("server:" + serverId + ":users")
                .map(Duration::getSeconds).defaultIfEmpty(-1L),
            redisTemplate.getExpire("session:" + sessionId)
                .map(Duration::getSeconds).defaultIfEmpty(-1L)
        )
        .map(tuple -> {
            Map<String, Long> ttlStatus = new HashMap<>();
            ttlStatus.put("timer_users_ttl", tuple.getT1());
            ttlStatus.put("user_server_ttl", tuple.getT2());
            ttlStatus.put("server_users_ttl", tuple.getT3());
            ttlStatus.put("session_ttl", tuple.getT4());
            return ttlStatus;
        });
    }
}
