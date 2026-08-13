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
| 알림 채널 | Discord Webhook, Email(SMTP), 인앱 알림 — 모두 구현됨 |

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

알림 규칙에서 디스코드/이메일 채널을 켜면 다음 변수도 필요합니다:

| 변수 | 설명 |
|---|---|
| `DISCORD_WEBHOOK_URL` | 알림을 보낼 디스코드 채널의 Incoming Webhook URL |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | 이메일 발송용 SMTP 서버 정보 |
| `NOTIFICATION_EMAIL_TO` | 알림을 받을 이메일 주소 |

값은 설정하지 않았습니다 — 필요할 때 직접 채워 넣으세요. 설정하지 않은 채로 해당 채널을 쓰는 알림 규칙이 발동하면, 알림 히스토리에 실패 사유(예: "DISCORD_WEBHOOK_URL이 설정되어 있지 않습니다")가 그대로 기록됩니다 (다른 채널은 정상 발송).

## 현재 상태 (1~2단계)

- **토스증권 연동**: 실제 API 문서가 없어 `TossApiClient` 인터페이스만 확정하고, `MockTossApiClient`(랜덤워크 가상 시세, 52주 고저·평균거래량 포함)로 나머지 기능을 개발했습니다. 실제 엔드포인트 스펙이 확보되면 `TossApiClient`를 구현하는 새 빈으로 교체하면 됩니다 — 호출부(서비스/컨트롤러)는 수정할 필요가 없습니다.
- **관심종목**: 추가/삭제/목록 조회 (`/api/watchlist`), 실시간(모의) 시세·등락률 포함
- **알림 규칙**: 목표가 이상/이하, 등락률(±N%), 거래량 급증(평균 대비 N배), 52주 신고가/신저가 근접 — 6가지 조건 × 디스코드/이메일/인앱 채널, 쿨다운, 활성/비활성 토글, 생성/삭제 (`/api/alert-rules`)
- **스케줄러**: 60초 주기로 활성 규칙을 평가하고 조건 충족 시 알림 발송 (`PriceAlertScheduler`)
- **알림 히스토리**: 대시보드에는 최근 알림 미리보기(`/api/alert-logs/recent`), 전용 화면에는 채널/상태 필터 + 페이지네이션을 갖춘 전체 히스토리(`/api/alert-logs`)
- **대시보드**: 자산 요약 + 보유종목 (`/api/dashboard`), 관심종목, 알림 규칙 관리를 한 화면에서 확인, 상단 탭으로 알림 히스토리 화면 전환
- **미구현**: 캔들 차트, 자산 추이 그래프, 다이제스트(일일 요약) 알림, 실제 토스증권 연동, 다중 사용자/로그인 (docs/PLANNING.md 3단계 및 원 기획의 `users` 테이블)

로컬에서 백엔드+프론트엔드를 함께 띄우면(위 "시작하기" 참고) `http://localhost:5173`에서 전체 대시보드를 확인할 수 있습니다.
