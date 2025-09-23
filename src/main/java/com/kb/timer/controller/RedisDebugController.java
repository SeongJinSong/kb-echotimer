package com.kb.timer.controller;

import com.kb.timer.service.RedisConnectionManager;
import com.kb.timer.service.ServerShutdownHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis 상태 디버깅용 컨트롤러
 * 개발/테스트 환경에서 Redis 데이터 확인용
 */
@RestController
@RequestMapping("/api/v1/debug/redis")
@RequiredArgsConstructor
@Slf4j
public class RedisDebugController {

    private final RedisConnectionManager redisConnectionManager;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ServerShutdownHandler serverShutdownHandler;

    /**
     * 모든 Redis 키 조회
     */
    @GetMapping("/keys")
    public Flux<String> getAllKeys(@RequestParam(defaultValue = "*") String pattern) {
        log.info("Redis 키 조회 요청: pattern={}", pattern);
        
        return redisTemplate.keys(pattern)
                .doOnNext(key -> log.debug("Redis 키 발견: {}", key))
                .doOnComplete(() -> log.info("Redis 키 조회 완료"))
                .doOnError(error -> log.error("Redis 키 조회 실패: {}", error.getMessage(), error));
    }

    /**
     * 특정 타이머의 온라인 사용자 목록 조회
     */
    @GetMapping("/timer/{timerId}/users")
    public Mono<Map<String, Object>> getTimerUsers(@PathVariable String timerId) {
        log.info("타이머 사용자 목록 조회: timerId={}", timerId);
        
        return redisConnectionManager.getOnlineUserCount(timerId)
                .defaultIfEmpty(0L)
                .flatMap(count -> {
                    String setKey = "timer:" + timerId + ":online_users";
                    
                    return redisTemplate.opsForSet().members(setKey)
                            .collectList()
                            .map(members -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("timerId", timerId);
                                result.put("onlineUserCount", count);
                                result.put("userList", members);
                                result.put("redisKey", setKey);
                                return result;
                            });
                })
                .doOnNext(result -> log.info("타이머 사용자 정보: {}", result))
                .doOnError(error -> log.error("타이머 사용자 조회 실패: timerId={}, error={}", 
                        timerId, error.getMessage(), error));
    }

    /**
     * 모든 타이머의 온라인 사용자 현황 조회
     */
    @GetMapping("/timers/all-users")
    public Flux<Map<String, Object>> getAllTimerUsers() {
        log.info("모든 타이머 사용자 현황 조회");
        
        return redisTemplate.keys("timer:*:online_users")
                .flatMap(key -> {
                    String timerId = key.replaceAll("^timer:|:online_users$", "");
                    
                    return redisTemplate.opsForSet().size(key)
                            .defaultIfEmpty(0L)
                            .flatMap(count -> 
                                redisTemplate.opsForSet().members(key)
                                        .collectList()
                                        .map(members -> {
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("timerId", timerId);
                                            result.put("redisKey", key);
                                            result.put("onlineUserCount", count);
                                            result.put("userList", members);
                                            return result;
                                        })
                            );
                })
                .doOnNext(result -> log.debug("타이머 사용자 현황: {}", result))
                .doOnComplete(() -> log.info("모든 타이머 사용자 현황 조회 완료"))
                .doOnError(error -> log.error("모든 타이머 사용자 현황 조회 실패: {}", error.getMessage(), error));
    }

    /**
     * 특정 Redis 키의 값 조회
     */
    @GetMapping("/key/{key}")
    public Mono<Map<String, Object>> getKeyValue(@PathVariable String key) {
        log.info("Redis 키 값 조회: key={}", key);
        
        return redisTemplate.type(key)
                .flatMap(type -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("key", key);
                    result.put("type", type.name());
                    
                    switch (type) {
                        case STRING:
                            return redisTemplate.opsForValue().get(key)
                                    .map(value -> {
                                        result.put("value", value);
                                        return result;
                                    });
                        case SET:
                            return redisTemplate.opsForSet().members(key)
                                    .collectList()
                                    .map(members -> {
                                        result.put("members", members);
                                        result.put("size", members.size());
                                        return result;
                                    });
                        case HASH:
                            return redisTemplate.opsForHash().entries(key)
                                    .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                    .map(entries -> {
                                        result.put("entries", entries);
                                        return result;
                                    });
                        default:
                            result.put("value", "지원하지 않는 타입");
                            return Mono.just(result);
                    }
                })
                .doOnNext(result -> log.info("Redis 키 값: {}", result))
                .doOnError(error -> log.error("Redis 키 값 조회 실패: key={}, error={}", 
                        key, error.getMessage(), error));
    }

    /**
     * 특정 Redis 키 삭제
     */
    @DeleteMapping("/key/{key}")
    public Mono<Map<String, Object>> deleteKey(@PathVariable String key) {
        log.info("Redis 키 삭제 요청: key={}", key);
        
        return redisTemplate.delete(key)
                .map(deleted -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("key", key);
                    result.put("deleted", deleted > 0);
                    result.put("deletedCount", deleted);
                    return result;
                })
                .doOnNext(result -> log.info("Redis 키 삭제 결과: {}", result))
                .doOnError(error -> log.error("Redis 키 삭제 실패: key={}, error={}", 
                        key, error.getMessage(), error));
    }

    /**
     * 특정 타이머의 사용자 강제 제거
     */
    @DeleteMapping("/timer/{timerId}/user/{userId}")
    public Mono<Map<String, Object>> removeUserFromTimer(@PathVariable String timerId, 
                                                         @PathVariable String userId) {
        log.info("타이머에서 사용자 강제 제거: timerId={}, userId={}", timerId, userId);
        
        return redisConnectionManager.removeUserConnection(timerId, userId)
                .then(redisConnectionManager.getOnlineUserCount(timerId))
                .defaultIfEmpty(0L)
                .map(remainingCount -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("timerId", timerId);
                    result.put("removedUserId", userId);
                    result.put("remainingUserCount", remainingCount);
                    return result;
                })
                .doOnNext(result -> log.info("사용자 제거 결과: {}", result))
                .doOnError(error -> log.error("사용자 제거 실패: timerId={}, userId={}, error={}", 
                        timerId, userId, error.getMessage(), error));
    }

    /**
     * 모든 타이머 사용자 정리 (좀비 세션 제거)
     */
    @DeleteMapping("/cleanup/all-users")
    public Mono<Map<String, Object>> cleanupAllUsers() {
        log.info("모든 타이머 사용자 정리 시작");
        
        return redisTemplate.keys("timer:*:online_users")
                .flatMap(key -> redisTemplate.delete(key))
                .count()
                .map(deletedCount -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("deletedTimerKeys", deletedCount);
                    result.put("message", "모든 타이머 사용자 정보가 정리되었습니다");
                    return result;
                })
                .doOnNext(result -> log.info("타이머 사용자 정리 완료: {}", result))
                .doOnError(error -> log.error("타이머 사용자 정리 실패: {}", error.getMessage(), error));
    }

    /**
     * 모든 좀비 키 완전 정리 (사용자, 서버, 세션 포함)
     */
    @DeleteMapping("/cleanup/all-zombie-keys")
    public Mono<Map<String, Object>> cleanupAllZombieKeys() {
        log.info("모든 좀비 키 완전 정리 시작");
        
        return serverShutdownHandler.forceCleanupAllZombieKeys()
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("message", "모든 좀비 키가 완전히 정리되었습니다");
                    result.put("cleanupType", "force_cleanup");
                    return result;
                }))
                .doOnNext(result -> log.info("좀비 키 완전 정리 완료: {}", result))
                .doOnError(error -> log.error("좀비 키 완전 정리 실패: {}", error.getMessage(), error));
    }

    /**
     * Redis 전체 통계
     */
    @GetMapping("/stats")
    public Mono<Map<String, Object>> getRedisStats() {
        log.info("Redis 통계 조회");
        
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("timestamp", System.currentTimeMillis());
            return stats;
        })
        .flatMap(stats -> 
            redisTemplate.keys("timer:users:*")
                    .count()
                    .map(timerCount -> {
                        stats.put("activeTimerCount", timerCount);
                        return stats;
                    })
        )
        .flatMap(stats ->
            redisTemplate.keys("*")
                    .count()
                    .map(totalKeys -> {
                        stats.put("totalRedisKeys", totalKeys);
                        return stats;
                    })
        )
        .doOnNext(stats -> log.info("Redis 통계: {}", stats))
        .doOnError(error -> log.error("Redis 통계 조회 실패: {}", error.getMessage(), error));
    }
}
