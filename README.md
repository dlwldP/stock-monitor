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
| `TOSS_CLIENT_ID` | 토스증권 Open API client ID (WTS 설정 > Open API 메뉴에서 발급) |
| `TOSS_CLIENT_SECRET` | 토스증권 Open API client secret |
| `TOSS_ACCOUNT_SEQ` | 계좌·자산/주문 API에 필요한 `X-Tossinvest-Account` 헤더 값. `GET /api/v1/accounts`로 계좌 목록 조회 후 확인 |
| `TOSS_API_USE_REAL_CLIENT` | `true`로 설정하면 `MockTossApiClient` 대신 실제 API를 호출하는 `TossHttpApiClient`를 사용 (기본값 `false`) |

`TOSS_API_USE_REAL_CLIENT`는 **키를 넣어도 자동으로 켜지지 않습니다.** 아래 "토스증권 실연동" 절을 먼저 읽어보세요.

알림 규칙에서 디스코드/이메일 채널을 켜면 다음 변수도 필요합니다:

| 변수 | 설명 |
|---|---|
| `DISCORD_WEBHOOK_URL` | 알림을 보낼 디스코드 채널의 Incoming Webhook URL |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | 이메일 발송용 SMTP 서버 정보 |
| `NOTIFICATION_EMAIL_TO` | 알림을 받을 이메일 주소 |

일일 요약(다이제스트) 이메일도 위 SMTP 변수 + `NOTIFICATION_EMAIL_TO`를 그대로 씁니다.

값은 설정하지 않았습니다 — 필요할 때 직접 채워 넣으세요. 설정하지 않은 채로 해당 채널을 쓰는 알림 규칙이 발동하면, 알림 히스토리에 실패 사유(예: "DISCORD_WEBHOOK_URL이 설정되어 있지 않습니다")가 그대로 기록됩니다 (다른 채널은 정상 발송). 다이제스트는 알림 규칙에 안 묶여 있어서 실패해도 로그에만 남고 조용히 스킵됩니다.

## 토스증권 실연동

공식 문서([developers.tossinvest.com/docs](https://developers.tossinvest.com/docs))를 확인해서 반영했습니다. `TossApiClient` 인터페이스 뒤에 두 구현체가 있습니다:

- `MockTossApiClient` — 기본값. 랜덤워크 가상 시세로 전 기능을 개발/검증했습니다.
- `TossHttpApiClient` — 아래처럼 문서 기준으로 구현했습니다:
  - **확정된 부분**: base URL(`https://openapi.tossinvest.com`), OAuth2 client-credentials 인증(`POST /oauth2/token`, form-urlencoded), 계좌·자산 API에 필요한 `X-Tossinvest-Account` 헤더, 에러 응답 포맷(`{"error":{"requestId","code","message","data"}}` → `TossApiException`), 429 레이트리밋 시 `Retry-After` 존중해서 1회 재시도. 시세·종목정보 API는 계좌와 무관해서 토큰만 있으면 호출 가능합니다.
  - **엔드포인트 경로도 확정**: 시세 `GET /api/v1/prices`, 캔들 `GET /api/v1/candles`, 보유종목 `GET /api/v1/holdings`.
  - **아직 미확정**: 위 세 엔드포인트의 **정확한 응답 필드명**(요청 파라미터명도 일부 추정). 문서 목록 페이지에는 엔드포인트만 나열되어 있고, 각 엔드포인트의 상세 요청/응답 스키마 페이지는 아직 못 봤습니다. `TossHttpApiClient.java`의 `QuoteDto`/`CandleDto`/`HoldingDto`가 그 부분이고, 클래스 상단 Javadoc에 정리해뒀습니다. 그 페이지들을 붙여넣어 주시면 마저 고치겠습니다.
  - **계좌 요약(총 평가금액/손익)** 은 별도 엔드포인트가 문서에 없어서, 보유종목 목록 + 종목별 시세를 클라이언트에서 합산하는 방식으로 구현했습니다 (Mock과 동일한 방식).

`TOSS_CLIENT_ID`/`TOSS_CLIENT_SECRET`을 넣어도 `TOSS_API_USE_REAL_CLIENT=true`로 **직접 켜기 전까지는** 계속 Mock이 서빙합니다 (`TossApiClientConfig`가 빈을 갈아끼움). 순서 제안:

1. WTS **설정 > Open API** 메뉴에서 `client_id`/`client_secret` 발급, **허용 IP** 목록에 서버 IP 등록 (미등록 IP는 403).
2. 키를 넣고 `TOSS_API_USE_REAL_CLIENT=true`로 켠 뒤, `POST /api/toss/verify-connection`으로 OAuth 토큰 발급만 먼저 확인하세요 (데이터 엔드포인트는 안 건드립니다).
3. `GET /api/v1/accounts`로 계좌 목록을 조회해 `accountSeq`를 확인하고 `TOSS_ACCOUNT_SEQ`로 설정하세요.
4. `QuoteDto`/`CandleDto`/`HoldingDto` 필드명을 실제 응답에 맞게 고치세요 (postman이나 curl로 실제 응답을 한 번 찍어보는 게 제일 빠릅니다).
5. 그 다음 대시보드/관심종목 등 나머지 기능을 실 데이터로 확인하세요.

## 현재 상태 (1~3단계)

- **토스증권 연동**: 위 "토스증권 실연동" 절 참고. `MockTossApiClient`(랜덤워크 가상 시세, 52주 고저·평균거래량·60일 캔들 포함)가 기본이고, `TossHttpApiClient`는 인증/경로/에러처리는 문서 기준으로 확정, 응답 필드 매핑만 남았습니다. 어느 쪽이든 호출부(서비스/컨트롤러)는 수정할 필요가 없습니다.
- **관심종목**: 추가/삭제/목록 조회 (`/api/watchlist`), 실시간(모의) 시세·등락률 포함
- **알림 규칙**: 목표가 이상/이하, 등락률(±N%), 거래량 급증(평균 대비 N배), 52주 신고가/신저가 근접 — 6가지 조건 × 디스코드/이메일/인앱 채널, 쿨다운, 활성/비활성 토글, 생성/삭제 (`/api/alert-rules`)
- **스케줄러**: 60초 주기로 활성 규칙을 평가하고 조건 충족 시 알림 발송 (`PriceAlertScheduler`)
- **알림 히스토리**: 대시보드에는 최근 알림 미리보기(`/api/alert-logs/recent`), 전용 화면에는 채널/상태 필터 + 페이지네이션을 갖춘 전체 히스토리(`/api/alert-logs`)
- **캔들 차트**: 관심종목/보유종목 선택 후 일봉 캔들 차트 확인 (`/api/candles`, 별도 차트 라이브러리 없이 자체 SVG 렌더링)
- **자산 추이 그래프**: `AccountSnapshotScheduler`가 주기적으로(기본 15분, 로컬 데모용으로 짧게 잡음) 자산 총액을 저장하고 (`/api/dashboard/history`), 대시보드에 라인 차트로 표시
- **다이제스트 알림**: 매일 08:00(설정 가능)에 그날 발송된 알림 요약 + 현재 자산현황을 이메일로 발송 (`DigestScheduler`), 테스트용으로 `POST /api/digest/send-now`도 있음
- **대시보드**: 자산 요약·추이, 보유종목, 관심종목, 알림 규칙 관리를 한 화면에서 확인, 상단 탭으로 차트/알림 히스토리 화면 전환
- **미구현**: 실제 토스증권 연동, 다중 사용자/로그인 (원 기획의 `users` 테이블), Order API 연동 자동매매 — 전부 원 기획 문서에서도 선택/후순위 범위였던 부분

로컬에서 백엔드+프론트엔드를 함께 띄우면(위 "시작하기" 참고) `http://localhost:5173`에서 전체 대시보드를 확인할 수 있습니다.
