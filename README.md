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
- **Redis** - 세션 관리 및 캐싱
- **Lombok** - 코드 간소화

### 프론트엔드
- **React 19** - UI 라이브러리
- **TypeScript** - 타입 안정성
- **Vite** - 빠른 개발 서버 및 빌드 도구
- **Material-UI (MUI)** - UI 컴포넌트 라이브러리
- **STOMP.js** - WebSocket 클라이언트
- **Axios** - HTTP 클라이언트

## 📦 프로젝트 구조

```
kb-echotimer/
├── src/main/java/com/kb/timer/          # 백엔드 소스
│   ├── controller/                      # REST API & WebSocket 컨트롤러
│   ├── service/                         # 비즈니스 로직
│   ├── repository/                      # 데이터 접근 계층
│   ├── model/                          # 엔티티 및 DTO
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

| 메서드 | 엔드포인트 | 설명 |
|--------|------------|------|
| `POST` | `/api/v1/timers` | 새 타이머 생성 |
| `GET` | `/api/v1/timers/{timerId}` | 타이머 정보 조회 |
| `PUT` | `/api/v1/timers/{timerId}/target-time` | 목표 시간 변경 |
| `POST` | `/api/v1/timers/{timerId}/timestamps` | 타임스탬프 저장 |
| `GET` | `/api/v1/timers/{timerId}/history` | 타이머 히스토리 조회 |

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

## 📊 모니터링

### Actuator 엔드포인트

- `/actuator/health` - 애플리케이션 상태
- `/actuator/metrics` - 메트릭 정보
- `/actuator/prometheus` - Prometheus 메트릭

### 로그 확인

```bash
# 애플리케이션 로그
tail -f logs/application.log

# Kafka 이벤트 로그
grep "Timer Event" logs/application.log
```

## 🐛 문제 해결

### 자주 발생하는 문제들

1. **WebSocket 연결 실패**
   ```bash
   # 백엔드 서버가 실행 중인지 확인
   curl http://localhost:8090/actuator/health
   ```

2. **Kafka 연결 오류**
   ```bash
   # Docker 컨테이너 상태 확인
   docker-compose ps
   
   # Kafka 재시작
   docker-compose restart kafka
   ```

3. **프론트엔드 빌드 실패**
   ```bash
   # 캐시 정리 후 재설치
   cd frontend
   rm -rf node_modules package-lock.json
   npm install
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