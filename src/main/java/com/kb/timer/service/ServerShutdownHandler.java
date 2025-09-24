package com.kb.timer.service;

import com.kb.timer.util.ServerInstanceIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PreDestroy;

/**
 * 서버 종료 시 Redis 정리 핸들러
 * 서버가 종료될 때 해당 서버와 연관된 모든 Redis 키들을 정리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServerShutdownHandler {

    private final ReactiveRedisTemplate<String, String> stringRedisTemplate;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ServerInstanceIdGenerator serverInstanceIdGenerator;
    private final RedisTTLSchedulerService redisTTLSchedulerService;

    /**
     * 서버 종료 시 실행되는 정리 작업
     */
    @PreDestroy
    public void onShutdown() {
        String serverId = serverInstanceIdGenerator.getServerInstanceId();
        log.info("서버 종료 시작: serverId={}", serverId);
        
        try {
            // 1. TTL 스케줄러 정리
            redisTTLSchedulerService.shutdown();
            
            // 2. Redis 정리 작업 수행 (비동기로 처리하여 블로킹 방지)
            cleanupServerRelatedKeys(serverId)
                .doOnSuccess(unused -> log.info("서버 종료 시 Redis 정리 완료: serverId={}", serverId))
                .doOnError(error -> log.warn("서버 종료 시 Redis 정리 실패 (무시됨): serverId={}, error={}", 
                          serverId, error.getMessage()))
                .onErrorComplete() // 에러 발생 시 무시하고 계속 진행
                .subscribe(); // 비동기 실행
                
            log.info("서버 종료 시 TTL 스케줄러 정리 완료: serverId={}", serverId);
        } catch (Exception e) {
            log.warn("서버 종료 시 정리 작업 실패 (무시됨): serverId={}, error={}", serverId, e.getMessage());
        }
    }

    /**
     * 특정 서버와 연관된 모든 Redis 키 정리
     * 
     * @param serverId 서버 ID
     * @return 정리 작업 Mono
     */
    public Mono<Void> cleanupServerRelatedKeys(String serverId) {
        log.info("서버 관련 Redis 키 정리 시작: serverId={}", serverId);
        
        return Mono.when(
            // 1. 서버별 사용자 목록에서 해당 서버의 사용자들 조회 후 정리
            cleanupUsersFromServer(serverId),
            
            // 2. 서버별 사용자 목록 키 삭제
            stringRedisTemplate.delete("server:" + serverId + ":users")
                    .doOnNext(deleted -> log.info("서버 사용자 목록 키 삭제: server:{}:users, deleted={}", serverId, deleted))
        )
        .doOnSuccess(ignored -> log.info("서버 관련 Redis 키 정리 완료: serverId={}", serverId))
        .doOnError(error -> log.error("서버 관련 Redis 키 정리 실패: serverId={}, error={}", 
                serverId, error.getMessage(), error));
    }

    /**
     * 특정 서버의 사용자들을 모든 타이머에서 제거
     * 
     * @param serverId 서버 ID
     * @return 정리 작업 Mono
     */
    private Mono<Void> cleanupUsersFromServer(String serverId) {
        String serverUsersKey = "server:" + serverId + ":users";
        
        return stringRedisTemplate.opsForSet().members(serverUsersKey)
                .collectList()
                .flatMap(userIds -> {
                    if (userIds.isEmpty()) {
                        log.info("서버에 연결된 사용자 없음: serverId={}", serverId);
                        return Mono.empty();
                    }
                    
                    log.info("서버 종료로 인한 사용자 정리: serverId={}, userCount={}, users={}", 
                            serverId, userIds.size(), userIds);
                    
                    return Mono.when(
                        userIds.stream()
                                .map(this::cleanupUserFromAllTimers)
                                .toArray(Mono[]::new)
                    );
                });
    }

    /**
     * 특정 사용자를 모든 타이머에서 제거
     * 
     * @param userId 사용자 ID
     * @return 정리 작업 Mono
     */
    private Mono<Void> cleanupUserFromAllTimers(String userId) {
        return Mono.when(
            // 1. 모든 타이머에서 해당 사용자 제거
            stringRedisTemplate.keys("timer:*:online_users")
                    .flatMap(timerKey -> 
                        stringRedisTemplate.opsForSet().remove(timerKey, userId)
                                .doOnNext(removed -> {
                                    if (removed > 0) {
                                        log.info("타이머에서 사용자 제거: key={}, userId={}, removed={}", 
                                                timerKey, userId, removed);
                                    }
                                })
                    )
                    .then(),
            
            // 2. 사용자별 서버 정보 키 삭제
            stringRedisTemplate.delete("user:" + userId + ":server")
                    .doOnNext(deleted -> log.info("사용자 서버 정보 삭제: user:{}:server, deleted={}", userId, deleted)),
            
            // 3. 해당 사용자의 세션 정보들 삭제 (세션 ID를 정확히 알기 어려우므로 패턴 매칭)
            cleanupUserSessions(userId)
        )
        .doOnSuccess(ignored -> log.debug("사용자 정리 완료: userId={}", userId))
        .doOnError(error -> log.error("사용자 정리 실패: userId={}, error={}", userId, error.getMessage(), error));
    }

    /**
     * 특정 사용자의 세션 정보들 정리
     * 
     * @param userId 사용자 ID
     * @return 정리 작업 Mono
     */
    private Mono<Void> cleanupUserSessions(String userId) {
        // 세션 정보에서 해당 사용자 ID를 포함하는 세션들을 찾아 삭제
        // 실제로는 세션 ID와 사용자 ID의 매핑을 정확히 추적하기 어려우므로
        // 여기서는 로그만 남기고 추후 TTL로 자동 정리되도록 함
        log.debug("사용자 세션 정리 (TTL로 자동 정리 예정): userId={}", userId);
        return Mono.empty();
    }

    /**
     * 모든 좀비 키 강제 정리 (개발/테스트용)
     */
    public Mono<Void> forceCleanupAllZombieKeys() {
        log.warn("모든 좀비 키 강제 정리 시작 (개발/테스트용)");
        
        return Mono.when(
            // 1. 모든 타이머 사용자 키 삭제
            stringRedisTemplate.keys("timer:*:online_users")
                    .flatMap(key -> stringRedisTemplate.delete(key))
                    .then(),
            
            // 2. 모든 사용자 서버 정보 키 삭제
            stringRedisTemplate.keys("user:*:server")
                    .flatMap(key -> stringRedisTemplate.delete(key))
                    .then(),
            
            // 3. 모든 서버 사용자 목록 키 삭제
            stringRedisTemplate.keys("server:*:users")
                    .flatMap(key -> stringRedisTemplate.delete(key))
                    .then(),
            
            // 4. 모든 세션 키 삭제
            redisTemplate.keys("session:*")
                    .flatMap(key -> redisTemplate.delete(key))
                    .then()
        )
        .doOnSuccess(ignored -> log.warn("모든 좀비 키 강제 정리 완료"))
        .doOnError(error -> log.error("좀비 키 강제 정리 실패: error={}", error.getMessage(), error));
    }
}
