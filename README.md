# KB EchoTimer

카카오뱅크 과제를 위한 실시간 타이머 서비스입니다.  
Spring Boot 3 + WebFlux + Kafka + Redis + MongoDB를 사용한 분산 환경에서의 WebSocket 기반 타이머 애플리케이션입니다.

## 🏗️ 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   SPA Client    │    │   SPA Client    │    │   SPA Client    │
│   (WebSocket)   │    │   (WebSocket)   │    │   (WebSocket)   │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
    ┌─────────────────────────────┼─────────────────────────────┐
    │                             │                             │
┌───▼────┐                   ┌───▼────┐                   ┌───▼────┐
│Server 1│                   │Server 2│                   │Server 3│
│(8090)  │                   │(8091)  │                   │(8092)  │
└───┬────┘                   └───┬────┘                   └───┬────┘
    │                            │                            │
    └────────────┬───────────────┼───────────────┬────────────┘
                 │               │               │
            ┌────▼────┐     ┌────▼────┐     ┌───▼────┐
            │  Kafka  │     │  Redis  │     │MongoDB │
            │ (9092)  │     │ (6379)  │     │(27017) │
            └─────────┘     └─────────┘     └────────┘
```

## 🚀 기술 스택

### Backend
- **Spring Boot 3.2.0** - 메인 프레임워크
- **Java 21** - 프로그래밍 언어
- **Spring WebFlux** - 리액티브 웹 프레임워크
- **Spring WebSocket (STOMP)** - 실시간 통신
- **Apache Kafka** - 이벤트 스트리밍 (KRaft 모드)
- **Redis** - 연결 상태 관리 및 캐싱
- **MongoDB** - 타임스탬프 데이터 저장

### Infrastructure
- **Docker Compose** - 로컬 개발 환경
- **Kafka UI** - Kafka 모니터링
- **Spring Boot Actuator** - 헬스체크 및 메트릭

## 📋 주요 기능

### 1. 분산 WebSocket 연결 관리
- Redis를 통한 사용자-서버 매핑 관리
- 다중 서버 환경에서의 효율적인 메시지 라우팅

### 2. 이벤트 기반 아키텍처
- Kafka를 통한 이벤트 전파
- 서버별 필터링으로 네트워크 비용 최적화

### 3. 타이머 기능
- 클라이언트 사이드 타이머 (서버 부하 최소화)
- 서버 시간 동기화
- 타임스탬프 저장 및 관리

## 🛠️ 설치 및 실행

### 사전 요구사항
- Java 21
- Docker & Docker Compose
- Git

### 1. 프로젝트 클론
```bash
git clone <repository-url>
cd kb-echotimer
```

### 2. 인프라 서비스 시작
```bash
docker-compose up -d
```

### 3. 애플리케이션 실행
```bash
./gradlew bootRun
```

## 🌐 서비스 접속 정보

| 서비스 | URL | 설명 |
|--------|-----|------|
| Spring Boot | http://localhost:8090 | 메인 애플리케이션 |
| Health Check | http://localhost:8090/actuator/health | 헬스체크 |
| Kafka UI | http://localhost:8081 | Kafka 모니터링 |
| MongoDB | localhost:27017 | 데이터베이스 |
| Redis | localhost:6379 | 캐시 및 세션 관리 |
| Kafka | localhost:9092 | 메시지 브로커 |

## 📁 프로젝트 구조

```
src/main/java/com/kb/timer/
├── TimerApplication.java          # 메인 애플리케이션
├── config/                        # 설정 클래스들
│   ├── KafkaConfig.java          # Kafka 설정
│   ├── RedisConfig.java          # Redis 설정
│   └── WebSocketConfig.java      # WebSocket 설정
├── model/                         # 데이터 모델
│   ├── dto/                      # DTO 클래스들
│   ├── entity/                   # 엔티티 클래스들
│   └── event/                    # 이벤트 클래스들
└── service/                       # 서비스 로직
    └── RedisConnectionManager.java
```

## 🔧 설정

### application.yml 주요 설정
```yaml
server:
  port: 8090

spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/timer_service
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092
```

## 📊 모니터링

### Health Check
```bash
curl http://localhost:8090/actuator/health
```

### Kafka Topics 확인
- Kafka UI: http://localhost:8081
- 또는 CLI: `docker exec -it kb-timer-kafka kafka-topics --bootstrap-server localhost:9092 --list`

## 🐳 Docker 서비스

### 컨테이너 상태 확인
```bash
docker-compose ps
```

### 로그 확인
```bash
# 전체 로그
docker-compose logs

# 특정 서비스 로그
docker-compose logs kafka
docker-compose logs mongodb
docker-compose logs redis
```

### 서비스 재시작
```bash
# 전체 재시작
docker-compose restart

# 특정 서비스 재시작
docker-compose restart kafka
```

## 🔍 개발 가이드

### 새로운 이벤트 추가
1. `src/main/java/com/kb/timer/model/event/` 에 이벤트 클래스 생성
2. `TimerEvent` 인터페이스 구현
3. Kafka Producer/Consumer에서 처리 로직 추가

### 새로운 엔티티 추가
1. `src/main/java/com/kb/timer/model/entity/` 에 엔티티 클래스 생성
2. MongoDB 컬렉션 매핑 설정
3. Repository 인터페이스 생성 (필요시)

## 🚨 문제 해결

### Kafka 연결 실패
```bash
# Kafka 컨테이너 재시작
docker-compose restart kafka

# Kafka 로그 확인
docker-compose logs kafka
```

### MongoDB 연결 실패
```bash
# MongoDB 컨테이너 상태 확인
docker-compose ps mongodb

# MongoDB 로그 확인
docker-compose logs mongodb
```

### Redis 연결 실패
```bash
# Redis 연결 테스트
docker exec -it kb-timer-redis redis-cli ping

# Redis 로그 확인
docker-compose logs redis
```

## 📝 다음 단계

- [ ] 핵심 서비스 로직 구현
- [ ] WebSocket 메시지 핸들러 구현
- [ ] Kafka Consumer/Producer 로직 구현
- [ ] 프론트엔드 SPA 개발
- [ ] 통합 테스트 작성

## 📄 라이선스

이 프로젝트는 카카오뱅크 과제용으로 제작되었습니다.
