# KB EchoTimer

실시간 공유 타이머 애플리케이션 - Spring Boot + React + WebSocket

## 🚀 프로젝트 개요

KB EchoTimer는 여러 사용자가 실시간으로 공유할 수 있는 타이머 애플리케이션입니다. 
WebSocket을 통한 실시간 동기화와 Kafka를 이용한 이벤트 스트리밍을 지원합니다.

### 주요 기능

- ⏰ **실시간 타이머**: 여러 사용자가 동시에 같은 타이머를 공유
- 🔄 **실시간 동기화**: WebSocket (STOMP)을 통한 즉시 업데이트
- 📱 **반응형 UI**: 모바일과 데스크톱 모두 지원
- 💾 **타임스탬프 저장**: 특정 시점의 타이머 상태 저장 (사용자별 개별 저장)
- 🔗 **공유 링크**: URL을 통한 간편한 타이머 공유
- 📊 **실시간 모니터링**: 온라인 사용자 수 표시
- 🔔 **실시간 알림**: 공유 타이머 접속 시 소유자에게 즉시 알림
- 🎯 **스마트 UI**: 진행률 기반 색상 변화, 타이머 완료 시 자동 알림
- 👤 **사용자 관리**: 개별 사용자별 타임스탬프 관리 및 권한 제어

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

## 🚀 빠른 시작

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

#### 방법 1: IntelliJ IDEA (권장)
```bash
# 1. IntelliJ IDEA에서 프로젝트 열기
# 2. TimerApplication.java 실행 (Run/Debug)
# 3. Run Configuration 설정으로 프론트엔드 실행
#    - Run → Edit Configurations
#    - + → npm
#    - Name: Frontend Dev
#    - Package.json: frontend/package.json
#    - Command: run
#    - Scripts: dev
#    - Working directory: frontend
# 4. 설정한 Run Configuration으로 실행 (npm install 자동 실행됨)
```

#### 방법 2: 통합 실행
```bash
# 백엔드 + 프론트엔드 개발 서버 동시 실행
./gradlew bootRun

# 별도 터미널에서 프론트엔드 개발 서버 실행
./gradlew npmDev
```

#### 방법 3: 개별 실행
```bash
# 백엔드만 실행
./gradlew bootRun

# 프론트엔드만 실행 (별도 터미널, npm install 자동 실행됨)
cd frontend
npm run dev
```

### 4. 접속

- **프론트엔드**: http://localhost:3000
- **백엔드 API**: http://localhost:8090/api/v1
- **WebSocket**: ws://localhost:8090/ws
- **Actuator**: http://localhost:8090/actuator

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

### 6. Redis 키 구조 및 사용자 행동 시나리오

#### Redis 키 구조
```
timer:{timerId}:online_users -> SET {userId1, userId2, ...} (TTL: 30분)
user:{userId}:connected_server_id -> STRING (serverId) (TTL: 1시간)
user:{userId}:sessions -> SET {sessionId1, sessionId2, ...} (TTL: 2시간)
server:{serverId}:users -> SET {userId1, userId2, ...} (TTL: 45분)
session:{sessionId} -> OBJECT (SessionInfo) (TTL: 2시간)
```

#### 사용자 행동에 따른 Redis 키 변화

##### 🆕 **시나리오 1: 새로운 타이머 생성**
```
사용자 A가 25분 타이머 생성
```

**Redis 키 생성:**
```redis
# 1. 타이머별 온라인 사용자 목록
SADD timer:abc123:online_users user-A
EXPIRE timer:abc123:online_users 1800  # 30분 TTL

# 2. 사용자별 연결 서버 정보
SET user:user-A:connected_server_id server-001
EXPIRE user:user-A:connected_server_id 3600  # 1시간 TTL

# 3. 서버별 연결된 사용자 목록
SADD server:server-001:users user-A
EXPIRE server:server-001:users 2700  # 45분 TTL

# 4. 세션 정보
SET session:ws-session-001 {timerId: "abc123", userId: "user-A", serverId: "server-001", ...}
EXPIRE session:ws-session-001 7200  # 2시간 TTL

# 5. 사용자별 세션 인덱스
SADD user:user-A:sessions ws-session-001
EXPIRE user:user-A:sessions 7200  # 2시간 TTL
```

##### 🔗 **시나리오 2: 공유 링크로 다른 사용자 접속**
```
사용자 B가 공유 링크로 접속
```

**Redis 키 변화:**
```redis
# 1. 기존 사용자 A의 세션 인덱스 정리
DEL user:user-A:sessions

# 2. 타이머에 사용자 B 추가
SADD timer:abc123:online_users user-B
EXPIRE timer:abc123:online_users 1800

# 3. 사용자 B의 서버 정보
SET user:user-B:connected_server_id server-001
EXPIRE user:user-B:connected_server_id 3600

# 4. 서버에 사용자 B 추가
SADD server:server-001:users user-B
EXPIRE server:server-001:users 2700

# 5. 사용자 B의 세션 정보
SET session:ws-session-002 {timerId: "abc123", userId: "user-B", serverId: "server-001", ...}
EXPIRE session:ws-session-002 7200

# 6. 사용자 B의 세션 인덱스
SADD user:user-B:sessions ws-session-002
EXPIRE user:user-B:sessions 7200
```

**결과:**
- `timer:abc123:online_users` = `{user-A, user-B}`
- `server:server-001:users` = `{user-A, user-B}`
- 각 사용자별로 개별 세션 관리

##### 🔄 **시나리오 3: 새로고침 (F5)**
```
사용자 A가 브라우저 새로고침
```

**Redis 키 변화:**
```redis
# 1. 기존 세션 정리 (연결 해제 시)
DEL session:ws-session-001
DEL user:user-A:sessions

# 2. 타이머에서 사용자 A 제거
SREM timer:abc123:online_users user-A

# 3. 사용자 A의 서버 정보 삭제
DEL user:user-A:connected_server_id

# 4. 서버에서 사용자 A 제거
SREM server:server-001:users user-A

# 5. 새로운 세션 생성 (재연결 시)
SET session:ws-session-003 {timerId: "abc123", userId: "user-A", serverId: "server-001", ...}
EXPIRE session:ws-session-003 7200

# 6. 새로운 세션 인덱스
SADD user:user-A:sessions ws-session-003
EXPIRE user:user-A:sessions 7200

# 7. 타이머에 사용자 A 다시 추가
SADD timer:abc123:online_users user-A
EXPIRE timer:abc123:online_users 1800
```

**결과:**
- 기존 세션은 즉시 삭제 (메모리 효율성)
- 새로운 세션으로 재연결
- 타이머 상태는 유지

##### 🚪 **시나리오 4: 브라우저 탭 닫기**
```
사용자 B가 브라우저 탭을 닫음
```

**Redis 키 변화:**
```redis
# 1. 세션 즉시 삭제
DEL session:ws-session-002

# 2. 사용자 B의 세션 인덱스 삭제
DEL user:user-B:sessions

# 3. 타이머에서 사용자 B 제거
SREM timer:abc123:online_users user-B

# 4. 사용자 B의 서버 정보 삭제
DEL user:user-B:connected_server_id

# 5. 서버에서 사용자 B 제거
SREM server:server-001:users user-B
```

**결과:**
- `timer:abc123:online_users` = `{user-A}` (사용자 B 제거)
- `server:server-001:users` = `{user-A}` (사용자 B 제거)
- 사용자 A에게 "사용자 B가 나갔습니다" 알림

##### ⏰ **시나리오 5: 타이머 완료**
```
25분 후 타이머 완료
```

**Redis 키 변화:**
```redis
# 1. 타이머 TTL 만료 (자동)
# timer:abc123:schedule 키가 만료되어 Keyspace Notification 발생

# 2. 모든 관련 키 TTL 갱신 (하트비트 중단으로 점진적 만료)
# timer:abc123:online_users -> 30분 후 만료
# user:user-A:sessions -> 2시간 후 만료
# session:ws-session-003 -> 2시간 후 만료
```

**결과:**
- 타이머 완료 이벤트가 Kafka를 통해 모든 서버에 전파
- WebSocket을 통해 모든 사용자에게 알림
- 소유자: 스낵바 알림, 공유자: alert 알림

##### 🏢 **시나리오 6: 서버 재시작**
```
서버가 재시작됨
```

**Redis 키 변화:**
```redis
# 1. 메모리 기반 sessionTracker 초기화
# (서버 재시작으로 메모리 상태 손실)

# 2. Redis 키는 TTL에 의해 자동 정리
# timer:abc123:online_users -> 30분 후 만료
# user:user-A:sessions -> 2시간 후 만료
# session:ws-session-003 -> 2시간 후 만료
```

**결과:**
- 서버 재시작으로 WebSocket 연결 끊어짐
- 클라이언트가 자동 재연결 시도
- Redis 키는 TTL에 의해 자동 정리되어 메모리 누수 방지

#### 키 관리 최적화 전략

##### ✅ **유지하는 것들**
- **세션 ID로 직접 삭제**: `removeSessionById(sessionId)` - cost가 낮고 메모리 효율적
- **사용자별 세션 인덱스**: `user:{userId}:sessions` - 빠른 세션 조회
- **TTL 기반 자동 정리**: 모든 키에 적절한 TTL 설정

##### ❌ **제거한 것들**
- **`KEYS` 명령어**: Redis 서버 부하 방지
- **패턴 매칭 스캔**: 불필요한 복잡성 제거
- **다른 타이머에서 사용자 제거**: 과도한 정리 로직 제거

##### 🎯 **성능 최적화 효과**
- **Redis 부하 감소**: `KEYS` 명령어 제거로 서버 블로킹 방지
- **메모리 효율성**: 연결 해제 시 즉시 세션 삭제
- **확장성**: 사용자 수 증가에 따른 성능 저하 없음
- **안정성**: TTL 기반 자동 정리로 메모리 누수 방지

### 7. 이벤트 기반 아키텍처 (순환 의존성 해결)
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

### 7. 실시간 알림 시스템
```
공유 타이머 접속 감지 → 소유자 실시간 알림 → 브라우저 알림
WebSocket 구독 감지 → SharedTimerAccessedEvent → Kafka → 소유자 알림
```

**핵심 특징:**
- 🔍 **접속 감지**: WebSocket 구독 시점에서 소유자/참여자 구분
- 🎯 **정확한 타겟팅**: 소유자에게만 선택적 알림 전송  
- 🔔 **다중 알림**: 스낵바 + 브라우저 네이티브 알림
- ⚡ **즉시성**: WebSocket → Kafka → 실시간 브로드캐스트
- 📱 **크로스 플랫폼**: 모든 브라우저에서 일관된 알림 경험

**알림 플로우:**
```
1. 참여자 접속: WebSocketEventHandler → 소유자/참여자 구분
2. 이벤트 발행: TimerService → SharedTimerAccessedEvent → Kafka
3. 실시간 전송: KafkaConsumer → WebSocket → 모든 클라이언트
4. 선택적 표시: 프론트엔드에서 소유자만 알림 표시
5. 브라우저 알림: Notification API로 시스템 레벨 알림
```

### 8. WebSocket vs SSE 기술 선택
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
| `POST` | `/api/v1/timers/{timerId}/timestamps` | 타임스탬프 저장 |
| `GET` | `/api/v1/timers/{timerId}/history` | 타이머 히스토리 조회 |

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
| `/app/timer/{timerId}/change-target` | 목표 시간 변경 (소유자만) |
| `/app/timer/{timerId}/complete` | 타이머 완료 알림 |

### WebSocket 이벤트 타입

| 이벤트 타입 | 설명 | 대상 |
|-------------|------|------|
| `TARGET_TIME_CHANGED` | 목표 시간 변경 | 모든 참여자 |
| `TIMESTAMP_SAVED` | 타임스탬프 저장 | 해당 사용자 |
| `USER_JOINED` | 사용자 접속 | 모든 참여자 |
| `USER_LEFT` | 사용자 퇴장 | 모든 참여자 |
| `TIMER_COMPLETED` | 타이머 완료 | 모든 참여자 |
| `SHARED_TIMER_ACCESSED` | 공유 타이머 접속 | 타이머 소유자만 |

## 🎨 사용자 경험 (UX) 특징

### 직관적인 UI 설계
- 🎯 **진행률 시각화**: 남은 시간에 따른 색상 변화 (파란색 → 주황색 → 빨간색)
- 🔘 **명확한 버튼 배치**: 정지 버튼 → 수정 버튼 순서로 직관적 배치
- 📱 **간소화된 인터페이스**: 불필요한 네비게이션 제거, 핵심 기능에 집중
- 👤 **사용자 식별**: 현재 사용자 ID 표시로 다중 사용자 환경에서 명확성 제공

### 실시간 피드백 시스템
- 🔔 **즉시 알림**: 타이머 완료 시 스낵바 + 브라우저 알림
- 👋 **접속 알림**: 공유 타이머 접속 시 소유자에게 실시간 알림
- 📊 **실시간 상태**: 온라인 사용자 수, 연결 상태 실시간 표시
- ⚡ **즉시 동기화**: 목표 시간 변경 시 모든 참여자에게 즉시 반영

### 권한 기반 인터페이스
- 👑 **소유자 권한**: 목표 시간 수정 버튼은 소유자만 표시
- 🔒 **완료 시 제한**: 타이머 완료 후 수정 버튼 자동 비활성화
- 👥 **참여자 보호**: 참여자는 조회만 가능, 실수로 인한 변경 방지
- 💾 **개별 저장**: 타임스탬프는 사용자별로 개별 저장 및 관리

### 브라우저 네이티브 기능 활용
- 🔔 **시스템 알림**: Notification API를 통한 브라우저 레벨 알림
- 🔄 **자동 재연결**: WebSocket 연결 끊김 시 자동 재연결
- 📱 **반응형 디자인**: 모바일/데스크톱 모든 환경에서 최적화
- 🌐 **URL 공유**: 브라우저 주소창을 통한 간편한 타이머 공유

### 세션 영속성 및 복원 기능
- 💾 **세션 유지**: sessionStorage 기반 탭별 사용자 상태 관리
- 🔄 **URL 복원**: 공유자 새로고침 시 공유 링크 URL 자동 복원
- 🧹 **스마트 정리**: 타이머 생성 시 이전 세션 자동 정리로 충돌 방지
- ⚠️ **오류 처리**: 무효한 공유 링크 접속 시 적절한 안내 메시지

### 견고한 상태 관리
- 🔍 **실시간 검증**: API 응답 유효성 실시간 검증 및 자동 복구
- 🎯 **정확한 라우팅**: 공유 토큰 vs 타이머 ID 자동 구분 처리
- 🔄 **상태 동기화**: 프론트엔드-백엔드 상태 불일치 자동 감지 및 해결
- 🛡️ **방어적 프로그래밍**: 예외 상황에 대한 포괄적 처리 및 사용자 안내

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

### 테스트 구조

프로젝트는 **핵심 비즈니스 로직에 집중한 견고한 테스트 기반**을 제공합니다:

#### ✅ 핵심 테스트 모듈

1. **`TimerServiceTest`** (9개 테스트)
   - 타이머 생성, 조회, 수정 로직
   - 권한 관리 (소유자/뷰어)
   - 시간 계산 정확성
   - 타임스탬프 저장/조회

2. **`KafkaEventPublisherTest`** (6개 테스트)
   - Kafka 이벤트 발행 시스템
   - 모든 이벤트 타입 검증

3. **`RedisTTLSchedulerServiceTest`** (10개 테스트)
   - Redis TTL 기반 스케줄링
   - 타이머 완료 처리 로직

4. **`TimerWebSocketControllerTest`** (4개 테스트)
   - WebSocket 구독 기본 기능
   - 사용자 ID 처리 로직

### 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "com.kb.timer.service.TimerServiceTest"

# 연속 실행 (실패해도 계속)
./gradlew test --continue

# 테스트 리포트 확인
open build/reports/tests/test/index.html
```

### 테스트 철학

- **핵심 비즈니스 로직 우선**: 복잡한 통합 테스트보다 핵심 로직에 집중
- **단위 테스트 중심**: Mock을 활용한 빠르고 안정적인 테스트
- **실용적 접근**: 유지보수 비용과 테스트 가치의 균형

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

10. **프론트엔드 {scanAvailable: true} 응답 문제**
    ```bash
    # 문제: WebFlux Reactive Streams 메타데이터가 JSON에 포함됨
    # 해결: ResponseEntity 제거, Flux → Mono<Map> 변경
    
    # API 응답 확인
    curl http://localhost:8090/api/v1/timers/{timerId}
    # 정상: {"timerId":"...", "targetTime":"..."}
    # 비정상: {"scanAvailable":true, "prefetch":256}
    
    # 디버그 API 응답 확인
    curl http://localhost:8090/api/v1/debug/redis/keys
    # 정상: {"keys":["key1","key2"], "count":2}
    # 비정상: {"scanAvailable":true}
    ```

11. **타이머 생성 후 초기화면 복귀 문제**
    ```bash
    # 문제: sessionStorage 충돌 및 isShareToken 상태 오류
    # 해결: 타이머 생성 시 사전 정리 + 상태 관리 개선
    
    # 브라우저 개발자 도구에서 확인
    sessionStorage.getItem('kb-echotimer-current-timer-id')
    sessionStorage.getItem('kb-echotimer-is-share-token')
    
    # 로그 확인 (브라우저 콘솔)
    # 정상: "isShareToken: false" → "GET /timers/{timerId}"
    # 비정상: "isShareToken: true" → "GET /timers/shared/{timerId}" (404)
    ```

12. **공유자 새로고침 시 URL 유실 문제**
    ```bash
    # 문제: 공유 링크 접속 후 새로고침 시 홈으로 이동
    # 해결: sessionStorage 기반 URL 복원 로직 추가
    
    # 정상 동작 확인
    # 1. 공유 링크 접속: /timer/{shareToken}
    # 2. 새로고침 → URL 유지: /timer/{shareToken}
    # 3. sessionStorage 확인:
    sessionStorage.getItem('kb-echotimer-is-share-token') // "true"
    ```

13. **무효한 공유 링크 접속 문제**
    ```bash
    # 문제: 이전 공유 링크로 접속 시 부적절한 처리
    # 해결: 전용 오류 메시지 + 완전한 상태 초기화
    
    # 백엔드 로그 확인
    grep "유효하지 않은 공유 링크" logs/application.log
    
    # 프론트엔드 동작 확인 (브라우저 콘솔)
    # 정상: "공유 링크가 만료되었거나 존재하지 않습니다" 메시지 표시
    # 자동으로 홈 화면 이동 및 sessionStorage 정리
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