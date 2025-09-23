# Redis Keyspace Notifications 테스트

## 1. Redis 설정 확인
```bash
# 현재 설정 확인
CONFIG GET notify-keyspace-events

# 만료 이벤트 활성화
CONFIG SET notify-keyspace-events Ex
```

## 2. 이벤트 구독 테스트

### 터미널 1: 이벤트 구독
```bash
redis-cli
SUBSCRIBE __keyevent@0__:expired
```

### 터미널 2: 키 설정 및 테스트
```bash
redis-cli

# 3초 후 만료되는 키 설정
SET test:timer:123 "hello" EX 3

# 5초 후 만료되는 키 설정  
SET test:timer:456 "world" EX 5

# 즉시 확인
TTL test:timer:123  # 남은 시간 확인
```

### 예상 결과 (터미널 1):
```
1) "subscribe"
2) "__keyevent@0__:expired"
3) (integer) 1

# 3초 후
1) "message"
2) "__keyevent@0__:expired"
3) "test:timer:123"

# 5초 후  
1) "message"
2) "__keyevent@0__:expired"
3) "test:timer:456"
```

## 3. Java 애플리케이션에서 테스트

### 로그 예시:
```
2025-09-23 19:45:00 [redis-listener] INFO - Redis TTL 만료 이벤트 수신: timerId=abc123, expiredKey=timer:schedule:abc123
2025-09-23 19:45:00 [redis-listener] INFO - 타이머 완료 처리 시작: timerId=abc123
2025-09-23 19:45:00 [redis-listener] INFO - 타이머 완료 처리 성공: timerId=abc123
```

## 4. 성능 비교

### 기존 폴링 방식:
```
CPU 사용량: 지속적 (30초마다 체크)
메모리: 수동 정리 필요
정확성: ±30초 오차 가능
```

### Keyspace Notifications 방식:
```
CPU 사용량: 이벤트 발생 시에만
메모리: 자동 정리
정확성: 정확한 시점 (±1초 이내)
```
