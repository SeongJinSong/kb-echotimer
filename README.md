# KB EchoTimer

실시간 공유 타이머 애플리케이션 - Spring Boot + React + WebSocket

## 🚀 프로젝트 개요

KB EchoTimer는 여러 사용자가 실시간으로 공유할 수 있는 타이머 애플리케이션입니다. 
WebSocket을 통한 실시간 동기화와 Kafka를 이용한 이벤트 스트리밍을 지원합니다.

### 주요 기능

- ⏰ **실시간 타이머**: 여러 사용자가 동시에 같은 타이머를 공유
- 🔄 **실시간 동기화**: WebSocket (STOMP)을 통한 즉시 업데이트
- 📱 **반응형 UI**: 모바일과 데스크톱 모두 지원
- 💾 **타임스탬프 저장**: 특정 시점의 타이머 상태 저장
- 🔗 **공유 링크**: URL을 통한 간편한 타이머 공유
- 📊 **실시간 모니터링**: 온라인 사용자 수 표시

## 🏗️ 기술 스택

### 백엔드
- **Spring Boot 3.2** - 메인 프레임워크
- **Spring WebFlux** - 리액티브 웹 프로그래밍
- **Spring WebSocket** - 실시간 통신 (STOMP)
- **Apache Kafka** - 이벤트 스트리밍
- **MongoDB** - 데이터 저장소
- **Redis** - 세션 관리, 캐싱, 분산 락, TTL 스케줄링
- **Lombok** - 코드 간소화

### 프론트엔드
- **React 19** - UI 라이브러리
- **TypeScript** - 타입 안정성
- **Vite** - 빠른 개발 서버 및 빌드 도구
- **Material-UI (MUI)** - UI 컴포넌트 라이브러리
- **STOMP.js** - WebSocket 클라이언트
- **Axios** - HTTP 클라이언트

## 🎯 핵심 아키텍처 설계

### 1. 분산 타이머 스케줄링 시스템
```
Redis TTL + Keyspace Notifications 기반 정확한 타이머 완료 처리
키만료 → Keyspace Notification → 분산락 → Kafka → WebSocket → 클라이언트
```

**핵심 특징:**
- ⚡ **정확한 타이밍**: Redis TTL로 밀리초 단위 정확성
- 🔒 **분산 락**: 여러 서버 환경에서 중복 처리 방지
- 🔄 **실시간 이벤트**: Keyspace Notifications로 즉시 반응
- 📊 **완벽한 추적**: 모든 처리 과정 MongoDB 기록

### 2. 지능형 모니터링 시스템
```
MongoDB JOIN 기반 누락 타이머 자동 감지 및 분석
Timer 컬렉션 ⟷ TimerCompletionLog 컬렉션 비교 분석
```

**감지 가능한 문제:**
- 🚨 **Keyspace Notification 미수신** (Redis 연결 문제)
- 🔐 **분산 락 획득 실패** (서버 간 경쟁 상황)
- ❌ **처리 로직 실패** (MongoDB/Kafka 연결 오류)
- 🔄 **데이터 불일치** (로그 성공 but 엔티티 미업데이트)

### 3. 효율적인 분산 필터링
```
Redis SET 교집합 연산으로 관련 서버만 WebSocket 브로드캐스트
SINTER timer:{id}:online_users server:{id}:users
```

**장점:**
- 🎯 **정확한 타겟팅**: 실제 연결된 사용자가 있는 서버만 처리
- ⚡ **성능 최적화**: 불필요한 네트워크 트래픽 제거
- 📈 **확장성**: 서버 수에 관계없이 O(1) 복잡도

### 4. 견고한 분산 시스템 설계
```
다중 안전장치로 100% 신뢰성 보장
1차: Redis 분산 락 → 2차: MongoDB 상태 확인 → 3차: 모니터링 시스템
```

**안전장치:**
- 🛡️ **분산 락 TTL**: 데드락 방지 (5분 자동 해제)
- 🔍 **실시간 감지**: 1분마다 누락 타이머 자동 감지
- 📋 **상세 분석**: 실패 원인별 분류 및 로그
- 🔄 **자동 복구**: 락 만료 시 다른 서버가 자동 처리

### 5. TTL 기반 자동 정리 시스템
```
Redis TTL + 계층적 만료 시간으로 좀비 키 자동 정리
서버 비정상 종료 시에도 메모리 누수 방지 + 운영 부담 제거
```

**TTL 계층 구조:**
- 🕐 **세션 정보**: 2시간 TTL (가장 중요한 연결 정보)
- 👤 **사용자-서버 매핑**: 1시간 TTL (연결 상태 추적)  
- 🏢 **서버 사용자 목록**: 45분 TTL (서버별 사용자 관리)
- ⏰ **타이머 사용자 목록**: 30분 TTL (빠른 정리)

**자동 정리 메커니즘:**
- 🔄 **실시간 TTL 갱신**: WebSocket 하트비트 시 자동 연장
- 🧹 **점진적 정리**: 중요도에 따른 차등 TTL 적용
- 🛡️ **좀비 키 방지**: 서버 크래시 시에도 자동 정리 보장
- 📊 **TTL 모니터링**: 디버깅 API로 실시간 TTL 상태 확인

**운영 효과:**
```
Before: 서버 크래시 → 좀비 키 영구 잔존 → 수동 정리 필요
After:  서버 크래시 → 최대 2시간 후 자동 정리 → 완전 자동화
```

### 6. 이벤트 기반 아키텍처 (순환 의존성 해결)
```
ApplicationEventPublisher 기반 느슨한 결합 설계
서비스 간 직접 의존성 제거 + 확장 가능한 이벤트 시스템
```

**이벤트 기반 설계:**
- 🔄 **TimerScheduleEvent**: 타이머 스케줄링 (SCHEDULE, UPDATE, CANCEL)
- ⏰ **TimerCompletionEvent**: TTL 만료 시 타이머 완료 처리
- 🎯 **서버 내부 이벤트**: ApplicationEventPublisher로 동일 서버 내에서만 전파
- 🌐 **서버 간 이벤트**: Kafka로 분산 환경 전체에 브로드캐스트

**순환 의존성 해결:**
```
Before: TimerService ↔ RedisTTLSchedulerService (순환 의존성)
After:  TimerService → Event → RedisTTLSchedulerService (단방향)
```

**이벤트 플로우:**
```
1. 타이머 생성: TimerService → TimerScheduleEvent → RedisTTLSchedulerService
2. TTL 만료: RedisTTLSchedulerService → TimerCompletionEvent → TimerService
3. Kafka 발행: TimerService → KafkaEventPublisher → 모든 서버
4. WebSocket: KafkaEventConsumer → SimpMessagingTemplate → 클라이언트
```

### 7. WebSocket vs SSE 기술 선택
```
실시간 양방향 통신을 위한 WebSocket (STOMP) 채택
클라이언트 ↔ 서버 즉시 상호작용 + 효율적 브로드캐스트
```

**WebSocket 선택 이유:**
- 🔄 **양방향 실시간 통신**: 타임스탬프 저장, 타이머 완료 등 즉시 서버 전송
- 🌐 **단일 연결**: REST API + SSE 이중 채널 대신 통합 연결
- 🔌 **자동 재연결**: 서버 재시작/네트워크 문제 시 자동 복구
- 📡 **STOMP 표준화**: 구독/발행 패턴으로 메시지 라우팅
- ⚡ **효율적 필터링**: 기존 Redis SET 교집합 로직 재사용

**SSE 대비 장점:**
```
SSE 방식: 클라이언트 액션 → REST API → Kafka → 관련 서버 → 필터링 → SSE
WebSocket: 클라이언트 액션 → WebSocket → Kafka → 관련 서버 → 필터링 → WebSocket
```
- 🔄 **양방향 통신**: SSE는 서버→클라이언트만 가능, 클라이언트→서버는 별도 REST API 필요
- 📊 **구현 복잡도**: SSE는 실시간 액션을 위해 REST + SSE 이중 구조 필요
- 🔗 **연결 관리**: WebSocket 단일 연결 vs SSE+REST 이중 연결 상태 관리
- 📡 **프로토콜 표준화**: STOMP로 구독/발행/라우팅 표준화 vs SSE 커스텀 구현
- ⚡ **즉시성**: WebSocket은 클라이언트 액션 즉시 전송, SSE는 HTTP 요청 오버헤드

## 📦 프로젝트 구조

```
kb-echotimer/
├── src/main/java/com/kb/timer/          # 백엔드 소스
│   ├── controller/                      # REST API & WebSocket 컨트롤러
│   ├── service/                         # 비즈니스 로직
│   ├── repository/                      # 데이터 접근 계층
│   ├── model/                          # 엔티티, DTO, 이벤트
│   │   ├── entity/                     # MongoDB 엔티티 (Timer, TimestampEntry 등)
│   │   ├── dto/                        # 데이터 전송 객체
│   │   └── event/                      # 이벤트 클래스 (순환 의존성 해결)
│   ├── config/                         # 설정 클래스
│   └── util/                           # 유틸리티
├── frontend/                           # 프론트엔드 소스
│   ├── src/
│   │   ├── components/                 # React 컴포넌트
│   │   ├── hooks/                      # 커스텀 훅
│   │   ├── services/                   # API 클라이언트
│   │   ├── types/                      # TypeScript 타입 정의
│   │   └── utils/                      # 유틸리티
│   ├── package.json
│   └── vite.config.ts
└── build.gradle                        # 빌드 설정
```

## 🚀 시작하기

### 필수 요구사항

- **Java 21** 이상
- **Node.js 18** 이상
- **Docker & Docker Compose** (로컬 개발용)

### 1. 프로젝트 클론

```bash
git clone <repository-url>
cd kb-echotimer
```

### 2. 로컬 인프라 실행 (Docker Compose)

```bash
# Kafka, MongoDB, Redis 실행
docker-compose up -d
```

### 3. 개발 모드 실행

#### 방법 1: 통합 실행 (권장)
```bash
# 백엔드 + 프론트엔드 개발 서버 동시 실행
./gradlew bootRun

# 별도 터미널에서 프론트엔드 개발 서버 실행
./gradlew npmDev
```

#### 방법 2: 개별 실행
```bash
# 백엔드만 실행
./gradlew bootRun

# 프론트엔드만 실행 (별도 터미널)
cd frontend
npm install
npm run dev
```

### 4. 접속

- **프론트엔드**: http://localhost:3000
- **백엔드 API**: http://localhost:8090/api/v1
- **WebSocket**: ws://localhost:8090/ws
- **Actuator**: http://localhost:8090/actuator

## 🔧 빌드 및 배포

### 프로덕션 빌드

```bash
# 전체 빌드 (프론트엔드 + 백엔드)
./gradlew build

# 실행 가능한 JAR 파일 생성
./gradlew bootJar
```

### 빌드 결과물

- `build/libs/kb-echotimer-1.0.0.jar` - 실행 가능한 JAR
- `src/main/resources/static/` - 빌드된 프론트엔드 파일들

### 프로덕션 실행

```bash
java -jar build/libs/kb-echotimer-1.0.0.jar
```

## 🔌 API 문서

### REST API 엔드포인트

#### 타이머 관리
| 메서드 | 엔드포인트 | 설명 |
|--------|------------|------|
| `POST` | `/api/v1/timers` | 새 타이머 생성 |
| `GET` | `/api/v1/timers/{timerId}` | 타이머 정보 조회 |
| `GET` | `/api/v1/timers/shared/{shareToken}` | 공유 타이머 조회 |
| `PUT` | `/api/v1/timers/{timerId}/target-time` | 목표 시간 변경 |
| `POST` | `/api/v1/timers/{timerId}/timestamps` | 타임스탬프 저장 |
| `GET` | `/api/v1/timers/{timerId}/history` | 타이머 히스토리 조회 |
| `GET` | `/api/v1/timers/{timerId}/users/{userId}/history` | 사용자별 히스토리 조회 |

#### 모니터링 & 디버깅
| 메서드 | 엔드포인트 | 설명 |
|--------|------------|------|
| `GET` | `/api/v1/monitoring/completion-stats` | 타이머 완료 처리 통계 |
| `POST` | `/api/v1/monitoring/detect-missed-timers` | 수동 누락 타이머 감지 |
| `GET` | `/api/v1/monitoring/health` | 모니터링 서비스 상태 |
| `GET` | `/api/v1/debug/redis/stats` | Redis 통계 정보 |
| `GET` | `/api/v1/debug/redis/keys` | Redis 키 목록 |
| `GET` | `/api/v1/debug/redis/ttl` | TTL 상태 조회 |
| `POST` | `/api/v1/debug/redis/refresh-ttl` | 사용자 TTL 갱신 |
| `DELETE` | `/api/v1/debug/redis/cleanup/all-zombie-keys` | 좀비 키 정리 |

### WebSocket 엔드포인트

| 목적지 | 설명 |
|--------|------|
| `/topic/timer/{timerId}` | 타이머 이벤트 구독 |
| `/app/timer/{timerId}/save` | 타임스탬프 저장 |
| `/app/timer/{timerId}/change-target` | 목표 시간 변경 |
| `/app/timer/{timerId}/complete` | 타이머 완료 |

## 🛠️ 개발 가이드

### 프론트엔드 개발

```bash
cd frontend

# 의존성 설치
npm install

# 개발 서버 실행 (HMR 지원)
npm run dev

# 타입 체크
npm run build

# 프리뷰 (빌드 결과 확인)
npm run preview
```

### 백엔드 개발

```bash
# 컴파일 및 테스트
./gradlew build

# 테스트만 실행
./gradlew test

# 개발 서버 실행 (자동 재시작)
./gradlew bootRun
```

### 코드 스타일

- **백엔드**: Java 21, Lombok 사용, 한글 주석 권장
- **프론트엔드**: TypeScript strict 모드, ESLint 규칙 준수
- **변수명**: 명확하고 이해하기 쉬운 이름 사용
- **에러 처리**: 모든 예외 상황에 대한 적절한 처리

## 🧪 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "com.kb.timer.service.TimerServiceTest"

# 테스트 리포트 확인
open build/reports/tests/test/index.html
```

## 📊 모니터링 & 운영

### 실시간 모니터링 시스템

#### 1. 자동 누락 감지 (1분마다)
```bash
# 누락된 타이머 감지 로그 예시
🚨 누락된 타이머 완료 처리 감지! 총 2개 타이머
❌ 누락된 타이머: timerId=abc123, targetTime=2025-09-23T20:00:00Z, 지연시간=7분
📊 누락 타이머 상세 분석:
  - timerId=abc123: Keyspace Notification 자체를 받지 못함 (Redis 연결 문제)
  - timerId=def456: 분산 락 획득 실패 (다른 서버에서 처리 중이었으나 실패)
```

#### 2. 완료 처리 통계 조회
```bash
curl http://localhost:8090/api/v1/monitoring/completion-stats
{
  "totalAttempts": 150,
  "successfulCompletions": 147,
  "failedAttempts": 3,
  "successRate": 98.0
}
```

#### 3. Redis 상태 디버깅
```bash
# Redis 키 현황
curl http://localhost:8090/api/v1/debug/redis/keys

# TTL 상태 조회 (좀비 키 방지 확인)
curl "http://localhost:8090/api/v1/debug/redis/ttl?timerId=abc123&userId=user-456&serverId=server-local-789&sessionId=session-xyz"
{
  "timerId": "abc123",
  "userId": "user-456", 
  "ttlStatus": {
    "timer_users_ttl": 1800,     // 30분 (초 단위)
    "user_server_ttl": 3600,     // 1시간
    "server_users_ttl": 2700,    // 45분
    "session_ttl": 7200          // 2시간
  },
  "description": {
    "timer_users_ttl": "타이머 사용자 목록 TTL (초)",
    "user_server_ttl": "사용자-서버 매핑 TTL (초)",
    "server_users_ttl": "서버 사용자 목록 TTL (초)", 
    "session_ttl": "세션 정보 TTL (초)",
    "note": "-1은 키가 존재하지 않거나 TTL이 설정되지 않음을 의미"
  }
}

# 사용자 TTL 수동 갱신 (활성 사용자 연장)
curl -X POST "http://localhost:8090/api/v1/debug/redis/refresh-ttl?timerId=abc123&userId=user-456&serverId=server-local-789&sessionId=session-xyz"
{
  "message": "TTL 갱신 완료",
  "timerId": "abc123",
  "userId": "user-456",
  "updatedTTL": {
    "timer_users_ttl": 1800,
    "user_server_ttl": 3600,
    "server_users_ttl": 2700,
    "session_ttl": 7200
  }
}

# 좀비 키 정리
curl -X DELETE http://localhost:8090/api/v1/debug/redis/cleanup/all-zombie-keys
```

### Actuator 엔드포인트

- `/actuator/health` - 애플리케이션 상태
- `/actuator/metrics` - 메트릭 정보
- `/actuator/prometheus` - Prometheus 메트릭

### 로그 분석

```bash
# 타이머 완료 처리 로그
grep "TTL 만료 타이머" logs/application.log

# Keyspace Notification 수신 로그
grep "Redis TTL 만료 이벤트 수신" logs/application.log

# 분산 락 관련 로그
grep "락 획득" logs/application.log

# 누락 감지 로그
grep "누락된 타이머" logs/application.log

# Kafka 이벤트 로그
grep "Timer Event" logs/application.log

# TTL 설정 로그
grep "TTL 설정" logs/application.log

# TTL 갱신 로그  
grep "TTL 갱신" logs/application.log

# 좀비 키 정리 로그
grep "좀비 키" logs/application.log

# 이벤트 기반 아키텍처 로그
grep "이벤트 발행" logs/application.log

# 타이머 스케줄 이벤트 로그
grep "TimerScheduleEvent" logs/application.log

# 타이머 완료 이벤트 로그  
grep "TimerCompletionEvent" logs/application.log
```

### 성능 메트릭

#### 주요 지표
- **타이머 완료 성공률**: 99%+ 목표
- **처리 지연 시간**: 평균 < 1초
- **Keyspace Notification 수신율**: 100% 목표
- **분산 락 경쟁률**: 서버당 < 5%
- **TTL 자동 정리율**: 100% 목표 (좀비 키 0개)
- **Redis 메모리 사용량**: TTL로 인한 안정적 유지

#### TTL 관련 메트릭
- **세션 TTL 갱신 빈도**: 활성 사용자당 분당 1-2회
- **좀비 키 발생률**: 서버 크래시당 < 0.1%
- **TTL 만료 정확도**: ±5초 이내
- **자동 정리 지연 시간**: 최대 2시간 (세션 TTL)

#### 이벤트 시스템 메트릭
- **이벤트 발행 성공률**: 99.9%+ 목표 (ApplicationEventPublisher)
- **이벤트 처리 지연 시간**: 평균 < 10ms (서버 내부)
- **순환 의존성**: 0개 (이벤트 기반으로 완전 해결)
- **서비스 결합도**: 느슨한 결합 (이벤트 기반)

#### 알람 기준
- 완료 성공률 < 95% → 즉시 알람
- 5분 이상 지연된 타이머 발견 → 경고
- Redis 연결 실패 → 즉시 알람
- Kafka 이벤트 발행 실패 → 경고
- **좀비 키 10개 이상 발견** → 경고
- **TTL 설정 실패율 > 1%** → 경고
- **이벤트 발행 실패율 > 0.1%** → 경고
- **이벤트 처리 지연 > 100ms** → 경고

## 🐛 문제 해결

### 자주 발생하는 문제들

1. **타이머 완료 처리 누락**
   ```bash
   # 누락 감지 수동 실행
   curl -X POST http://localhost:8090/api/v1/monitoring/detect-missed-timers
   
   # Redis Keyspace Notifications 설정 확인
   redis-cli CONFIG GET notify-keyspace-events
   # 결과가 "Ex"가 아니면 설정 필요
   redis-cli CONFIG SET notify-keyspace-events Ex
   ```

2. **분산 락 경쟁 문제**
   ```bash
   # Redis 락 상태 확인
   curl http://localhost:8090/api/v1/debug/redis/keys | grep "timer:processing"
   
   # 좀비 락 정리
   curl -X DELETE http://localhost:8090/api/v1/debug/redis/cleanup/all-zombie-keys
   ```

3. **WebSocket 연결 실패**
   ```bash
   # 백엔드 서버가 실행 중인지 확인
   curl http://localhost:8090/actuator/health
   
   # Redis 연결 상태 확인
   curl http://localhost:8090/api/v1/debug/redis/stats
   ```

4. **Kafka 연결 오류**
   ```bash
   # Docker 컨테이너 상태 확인
   docker-compose ps
   
   # Kafka 재시작
   docker-compose restart kafka
   
   # Kafka 토픽 확인
   docker exec -it kafka kafka-topics.sh --list --bootstrap-server localhost:9092
   ```

5. **좀비 키 발생 (TTL 미적용)**
   ```bash
   # 좀비 키 확인
   curl http://localhost:8090/api/v1/debug/redis/keys | grep -E "(timer|session|user|server)"
   
   # TTL 상태 확인 (특정 키)
   curl "http://localhost:8090/api/v1/debug/redis/ttl?timerId=abc&userId=user123&serverId=server456&sessionId=sess789"
   
   # 좀비 키 강제 정리
   curl -X DELETE http://localhost:8090/api/v1/debug/redis/cleanup/all-zombie-keys
   
   # Redis에서 직접 TTL 확인
   docker exec redis redis-cli TTL "session:sessionId"
   # -1: TTL 없음 (좀비 키), -2: 키 없음, 양수: 남은 시간(초)
   ```

6. **TTL 갱신 실패**
   ```bash
   # 활성 사용자 TTL 수동 갱신
   curl -X POST "http://localhost:8090/api/v1/debug/redis/refresh-ttl?timerId=abc&userId=user123&serverId=server456&sessionId=sess789"
   
   # Redis 연결 상태 확인
   curl http://localhost:8090/api/v1/debug/redis/stats
   
   # 서버 재시작 (TTL 설정 로직 재초기화)
   ./gradlew bootRun
   ```

7. **프론트엔드 빌드 실패**
   ```bash
   # 캐시 정리 후 재설치
   cd frontend
   rm -rf node_modules package-lock.json
   npm install
   
   # Vite 설정 확인
   npm run build -- --debug
   ```

8. **이벤트 발행/처리 실패 (순환 의존성)**
   ```bash
   # 순환 의존성 확인
   grep -r "circular dependency" logs/application.log
   
   # 이벤트 발행 실패 로그 확인
   grep "이벤트 발행.*실패" logs/application.log
   
   # ApplicationEventPublisher 상태 확인
   curl http://localhost:8090/actuator/beans | jq '.contexts.application.beans | keys | map(select(contains("Event")))'
   
   # 서비스 재시작 (이벤트 리스너 재등록)
   ./gradlew bootRun
   ```

9. **이벤트 처리 지연**
   ```bash
   # 이벤트 처리 시간 분석
   grep "이벤트.*처리.*완료" logs/application.log | tail -20
   
   # 스레드 풀 상태 확인
   curl http://localhost:8090/actuator/metrics/executor.active
   
   # JVM 메모리 상태 확인
   curl http://localhost:8090/actuator/metrics/jvm.memory.used
   ```

### 성능 최적화 팁

1. **Redis 메모리 최적화**
   ```bash
   # Redis 메모리 사용량 확인
   redis-cli INFO memory
   
   # 만료된 키 정리
   redis-cli --scan --pattern "timer:*" | xargs redis-cli DEL
   ```

2. **MongoDB 인덱스 최적화**
   ```javascript
   // MongoDB 인덱스 확인
   db.timers.getIndexes()
   db.timer_completion_logs.getIndexes()
   
   // 성능 분석
   db.timers.explain().find({completed: false, targetTime: {$lt: new Date()}})
   ```

3. **JVM 튜닝**
   ```bash
   # 힙 메모리 설정
   java -Xms512m -Xmx2g -jar kb-echotimer.jar
   
   # GC 로그 활성화
   java -XX:+UseG1GC -XX:+PrintGCDetails -jar kb-echotimer.jar
   ```

## 🤝 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 `LICENSE` 파일을 참조하세요.

## 📞 연락처

프로젝트 관련 문의사항이 있으시면 이슈를 생성해 주세요.

---

**KB EchoTimer** - 실시간으로 공유하는 타이머의 새로운 경험 🕐✨