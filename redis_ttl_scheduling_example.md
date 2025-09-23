# Redis TTL + Keyspace Notifications 스케줄링 예시

## 1. 타이머 3개 등록 시나리오

### 현재 시간: 2025-09-23 19:30:00

```bash
# 타이머 A: 5분 후 만료 (19:35:00) - 300초 TTL
SET timer:schedule:timer-A "timer-A" EX 300

# 타이머 B: 10분 후 만료 (19:40:00) - 600초 TTL  
SET timer:schedule:timer-B "timer-B" EX 600

# 타이머 C: 2분 후 만료 (19:32:00) - 120초 TTL
SET timer:schedule:timer-C "timer-C" EX 120
```

### Redis 내부 상태:
```
timer:schedule:timer-A = "timer-A" (TTL: 300초)
timer:schedule:timer-B = "timer-B" (TTL: 600초)  
timer:schedule:timer-C = "timer-C" (TTL: 120초)
```

## 2. Keyspace Notifications 이벤트 처리

### Redis 설정 (자동으로 설정됨):
```bash
CONFIG SET notify-keyspace-events Ex
```

### 19:32:00 - 타이머 C 만료 이벤트
```
__keyevent@0__:expired timer:schedule:timer-C
```

**Java 처리 로직:**
```java
@Override
public void onMessage(Message message, byte[] pattern) {
    String expiredKey = message.toString(); // "timer:schedule:timer-C"
    String timerId = expiredKey.substring("timer:schedule:".length()); // "timer-C"
    
    // 분산 락 획득 시도
    String lockKey = "timer:processing:timer-C";
    Boolean lockAcquired = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, serverId, Duration.ofMinutes(5));
    
    if (lockAcquired) {
        // 타이머 완료 처리
        timerService.publishTimerCompletedEvent("timer-C");
        
        // MongoDB에 처리 로그 저장
        TimerCompletionLog log = TimerCompletionLog.builder()
            .timerId("timer-C")
            .serverId(serverId)
            .notificationReceivedAt(Instant.now())
            .success(true)
            .build();
        completionLogRepository.save(log);
    }
}
```

### 19:35:00 - 타이머 A 만료 이벤트
```
__keyevent@0__:expired timer:schedule:timer-A
```

### 19:40:00 - 타이머 B 만료 이벤트
```
__keyevent@0__:expired timer:schedule:timer-B
```

## 3. 실제 구현 코드 매핑

### 타이머 등록 (RedisTTLSchedulerService.scheduleTimer)
```java
public Mono<Void> scheduleTimer(Timer timer) {
    String timerId = timer.getId();
    Instant targetTime = timer.getTargetTime();
    Instant now = Instant.now();
    
    Duration delay = Duration.between(now, targetTime);
    String key = "timer:schedule:" + timerId;
    
    return stringRedisTemplate.opsForValue()
        .set(key, timerId, delay)
        .then();
}
```

### 만료 이벤트 처리 (RedisTTLSchedulerService.onMessage)
```java
@Override
public void onMessage(Message message, byte[] pattern) {
    String expiredKey = message.toString();
    
    if (!expiredKey.startsWith("timer:schedule:")) {
        return; // 다른 키 무시
    }
    
    String timerId = expiredKey.substring("timer:schedule:".length());
    
    // 분산 락 + 타이머 완료 처리 + MongoDB 로깅
    processTimerCompletionWithLogging(timerId);
}
```

## 4. TTL 방식의 장점

### 정확성
- **자동 만료**: Redis가 정확한 시간에 키를 만료시킴
- **이벤트 기반**: Keyspace Notifications로 즉시 알림
- **분산 안전**: 분산 락으로 중복 처리 방지

### 효율성
- **메모리 효율**: 만료된 키는 자동 삭제
- **CPU 효율**: 폴링 없이 이벤트 기반 처리
- **네트워크 효율**: 필요한 시점에만 이벤트 발생

### 신뢰성
- **Redis 내장 기능**: 검증된 TTL 메커니즘 활용
- **장애 복구**: 서버 재시작 시에도 TTL 유지
- **모니터링**: MongoDB 로그로 처리 상태 추적

## 5. 모니터링 및 디버깅

### Redis 키 확인
```bash
# 현재 스케줄된 타이머들 확인
KEYS timer:schedule:*

# 특정 키의 TTL 확인
TTL timer:schedule:timer-A

# 만료 이벤트 실시간 모니터링
SUBSCRIBE __keyevent@0__:expired
```

### MongoDB 로그 확인
```javascript
// 최근 완료 처리 로그
db.timer_completion_logs.find().sort({createdAt: -1}).limit(10)

// 실패한 처리들
db.timer_completion_logs.find({success: false})

// 특정 타이머의 처리 이력
db.timer_completion_logs.find({timerId: "timer-A"})
```

### 애플리케이션 API
```bash
# 완료 처리 통계
curl http://localhost:8090/api/v1/monitoring/completion-stats

# Redis 상태 확인  
curl http://localhost:8090/debug/redis/stats

# 수동 누락 감지 실행
curl -X POST http://localhost:8090/api/v1/monitoring/detect-missed-timers
```