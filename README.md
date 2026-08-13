# TossWatch (stock-monitor)

토스증권 Open API를 연동해 **관심종목 알림**과 **자산 대시보드**를 한 서비스에서 제공하는 프로젝트.

- 대시보드: 계좌·보유종목·관심종목 시세와 손익을 한 화면에서 확인
- 알림: 목표가/등락률/거래량 급증 등 조건을 설정하면 디스코드·이메일·인앱으로 알림

전체 기획/설계 배경은 [`docs/PLANNING.md`](docs/PLANNING.md)에 정리되어 있습니다. 이 README는 실제 구현 스택과 실행 방법을 다룹니다.

## 기술 스택

| 영역 | 스택 |
|---|---|
| 백엔드 | Java 21, Spring Boot 3.3.x, Gradle |
| DB | H2(파일모드, 로컬) → MySQL(운영) |
| DB 접근 | Spring Data JPA (Hibernate) |
| 스케줄러 | Spring `@Scheduled` (시세 폴링 · 알림 조건 평가) |
| 프론트엔드 | React + TypeScript + Vite |
| 알림 채널 | Discord Webhook, Email(SMTP), 인앱 알림 (채널 인터페이스로 추상화) |

## 레포 구조

```
stock-monitor/
 ├─ backend/    # Spring Boot API 서버 + 스케줄러
 ├─ frontend/   # React 대시보드 웹앱
 └─ docs/       # 기획/설계 문서
```

## 시작하기

### 백엔드

```bash
cd backend
./gradlew bootRun
```

- 기본 포트: `8080`
- 로컬 DB: H2 파일모드 (`backend/data/`, 최초 실행 시 자동 생성, git에는 포함되지 않음)
- 헬스체크: `GET http://localhost:8080/api/health`

### 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

- 기본 포트: `5173`
- 개발 서버는 `/api/*` 요청을 백엔드(`localhost:8080`)로 프록시합니다 (`frontend/vite.config.ts`).

## 환경변수 / 시크릿

`client_secret`, Discord Webhook URL 등 민감정보는 코드에 커밋하지 않고 환경변수로 주입합니다. 백엔드는 다음 환경변수를 읽습니다 (`backend/src/main/resources/application.yml` 참고):

| 변수 | 설명 |
|---|---|
| `TOSS_CLIENT_ID` | 토스증권 Open API client ID |
| `TOSS_CLIENT_SECRET` | 토스증권 Open API client secret |

값은 설정하지 않았습니다 — 프로젝트 구현이 끝난 뒤 직접 채워 넣을 예정입니다. 로컬에서 필요하면 `TOSS_CLIENT_ID=... TOSS_CLIENT_SECRET=... ./gradlew bootRun`처럼 실행 시 주입하거나, gitignore된 `application-local.yml`을 만들어 사용하세요.

## 현재 상태

프로젝트 스캐폴딩 단계입니다. 백엔드는 빌드/구동 가능한 최소 골격(헬스체크 API, CORS 설정)만 있고, 프론트엔드는 백엔드 연결을 확인하는 placeholder 화면만 있습니다. 다음 단계(1단계 MVP 범위)는 [`docs/PLANNING.md`](docs/PLANNING.md#10-개발-로드맵)의 로드맵을 따릅니다.
