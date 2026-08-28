# stock-monitor

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
- 테스트: `./gradlew test` (도메인 로직·서비스·스케줄러·알림 디스패처 단위 테스트, Mockito 기반)

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
| `TOSS_ACCOUNT_SEQ` | 계좌·자산/주문 API에 필요한 `X-Tossinvest-Account` 헤더 값. 계좌번호(`accountNo`)가 아니라 **`accountSeq`** 값입니다 (보통 `1` 같은 작은 정수). `GET /api/toss/accounts`로 확인 |
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

### 배포 전에 확인할 것

로컬 개발용 기본값이 실제 배포에는 안전하지 않은 부분들입니다. 외부에서 접근 가능한 곳에 띄우기 전에 확인하세요:

| 변수 | 기본값 | 설명 |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | API를 호출할 프론트엔드 origin (콤마로 여러 개 가능). 배포된 프론트엔드 도메인으로 바꿔야 함 |
| `H2_CONSOLE_ENABLED` | `true` | `/h2-console`(DB 브라우저) 노출 여부. 외부에서 접근 가능한 서버라면 `false`로 |
| `LOG_LEVEL` | `debug` | `com.stockmonitor` 패키지 로그 레벨. 운영에서는 `info` 권장 |

`/api/toss/**` 진단 엔드포인트(`raw/*` 포함)는 계좌·보유종목 원문을 그대로 돌려줍니다. 로컬 개발용이므로, 외부에 노출되는 서버라면 인증을 붙이거나 해당 컨트롤러를 빼고 배포하세요.

## 토스증권 실연동

공식 문서([developers.tossinvest.com/docs](https://developers.tossinvest.com/docs))를 확인해서 반영했습니다. `TossApiClient` 인터페이스 뒤에 두 구현체가 있습니다:

- `MockTossApiClient` — 기본값. 랜덤워크 가상 시세로 전 기능을 개발/검증했습니다.
- `TossHttpApiClient` — 아래처럼 문서 기준으로 구현했습니다:
  - **확정된 부분**: base URL(`https://openapi.tossinvest.com`), OAuth2 client-credentials 인증(`POST /oauth2/token`, form-urlencoded), 계좌·자산 API에 필요한 `X-Tossinvest-Account` 헤더, 에러 응답 포맷(`{"error":{"requestId","code","message","data"}}` → `TossApiException`), 429 레이트리밋 시 `Retry-After` 존중해서 1회 재시도. 시세·종목정보 API는 계좌와 무관해서 토큰만 있으면 호출 가능합니다.
  - **엔드포인트 경로도 확정**: 시세 `GET /api/v1/prices`, 캔들 `GET /api/v1/candles`, 보유종목 `GET /api/v1/holdings`.
  - **응답 봉투도 확정**: 실계좌로 확인한 결과 모든 응답이 `{"result": ...}` 형태로 감싸여서 옵니다. 배열을 직접 파싱하면 `Cannot deserialize ... from Object value` 에러가 납니다.
  - **시세 응답 확정**: `GET /api/v1/prices`는 `{"symbol","timestamp","lastPrice","currency"}`만 돌려줍니다 (`lastPrice`는 문자열). **등락률·거래량·52주 고저가 없습니다** — 아래 "실연동 시 제약" 참고.
  - **보유종목 응답 확정**: `GET /api/v1/holdings`의 `result`는 배열이 아니라 **객체**입니다. 계좌 전체 집계(`marketValue`/`profitLoss`/`dailyProfitLoss`, 각각 KRW·USD 양쪽)와 종목 배열(`items`)이 같이 들어있고, 종목마다 현재가(`lastPrice`)까지 포함되어 있습니다. 덕분에 계좌 요약은 이 응답 하나로 끝나고(종목별 시세를 다시 조회하지 않음), 일간 손익도 직접 계산하는 대신 API가 주는 값을 씁니다. `rate` 계열은 퍼센트가 아니라 **소수**입니다 (`"-0.0026"` = -0.26%). 종목의 시장 구분은 `marketCountry`(`"KR"`/`"US"`)입니다.
  - 시세·보유종목·캔들 매핑은 실제 응답을 그대로 넣은 `TossApiResponseMappingTest`로 고정해뒀습니다 (문서가 아니라 실제 호출로 알아낸 필드명이라, 스키마가 바뀌면 조용히 0원/빈 차트로 표시되는 대신 테스트가 깨지도록).
  - **캔들 확정**: 파라미터는 `symbol`(단수) + `interval`, 일봉 값은 **`1d`** (시험해본 다른 표기 `D`/`DAY`/`DAY_1`/`P1D` 등은 전부 "지원하지 않는 캔들 주기"). 응답 항목은 `{"timestamp","openPrice","highPrice","lowPrice","closePrice","volume","currency"}`이고 **최신순**으로 옵니다 (차트는 과거순이라 뒤집어서 사용). `count`류 파라미터는 확인된 게 없어서 보내지 않고, 받은 뒤 필요한 일수만큼 잘라 씁니다. 더 과거 데이터는 응답의 `nextBefore` 커서로 페이징할 수 있으나 아직 사용하지 않습니다.
  - 캔들 `timestamp`는 `2026-06-08T00:00:00.000+09:00`처럼 자정+KST라서, Jackson 기본 설정(`ADJUST_DATES_TO_CONTEXT_TIME_ZONE`)으로 바인딩하면 UTC로 변환되며 **하루씩 밀립니다**. 그래서 문자열로 받아 오프셋 그대로 파싱합니다 (`CandleDto.tradingDate()`).

### 실연동 시 제약 (Mock과의 차이)

`GET /api/v1/prices`가 마지막 체결가만 돌려주기 때문에, 실연동 모드에서는 알림 조건 6개 중 **2개만 동작**합니다:

| 조건 | Mock | 실연동 |
|---|---|---|
| 목표가 이상/이하 (PRICE_ABOVE/BELOW) | ✅ | ✅ |
| 등락률 (PCT_CHANGE) | ✅ | ❌ 데이터 없음 |
| 거래량 급증 (VOLUME_SPIKE) | ✅ | ❌ 데이터 없음 |
| 52주 신고가/신저가 근접 (WEEK52_*) | ✅ | ❌ 데이터 없음 |

데이터가 없는 조건은 예외를 던지지 않고 **그냥 발동하지 않습니다** (`AlertRule.isSatisfiedBy`) — 규칙 하나 때문에 폴링 전체가 죽지 않도록. 관심종목 화면의 등락률도 `-`로 표시됩니다. 등락률/거래량/52주 정보를 주는 엔드포인트를 찾으면 `TossHttpApiClient.getQuote`만 고치면 됩니다.
  - **계좌 요약(총 평가금액/손익)** 은 별도 엔드포인트가 없지만, 위처럼 보유종목 응답이 이미 계좌 집계를 포함하고 있어서 그 값을 그대로 읽습니다. (Mock은 여전히 보유종목 × 시세로 합산합니다.)

`TOSS_CLIENT_ID`/`TOSS_CLIENT_SECRET`을 넣어도 `TOSS_API_USE_REAL_CLIENT=true`로 **직접 켜기 전까지는** 계속 Mock이 서빙합니다 (`TossApiClientConfig`가 빈을 갈아끼움). 순서 제안:

1. WTS **설정 > Open API** 메뉴에서 `client_id`/`client_secret` 발급, **허용 IP** 목록에 서버 IP 등록 (미등록 IP는 403).
2. 키를 넣고 `TOSS_API_USE_REAL_CLIENT=true`로 켠 뒤, `POST /api/toss/verify-connection`으로 OAuth 토큰 발급만 먼저 확인하세요 (데이터 엔드포인트는 안 건드립니다).
3. `GET /api/toss/accounts`(우리 백엔드가 대신 호출해주는 진단용 엔드포인트, `accountSeq` 없이도 동작)로 계좌 목록을 조회해 응답의 `accountSeq` 값을 `TOSS_ACCOUNT_SEQ`로 설정하세요. **계좌번호(`accountNo`)가 아닙니다** — 계좌번호를 넣으면 `account-not-found` 에러가 납니다.
4. `QuoteDto`/`CandleDto`/`HoldingDto` 필드명을 실제 응답에 맞게 고치세요. 원문 응답은 아래 진단용 엔드포인트로 바로 확인할 수 있습니다:
   - `GET /api/toss/raw/holdings`
   - `GET /api/toss/raw/prices?symbol=005930`
   - `GET /api/toss/raw/candles?symbol=005930&days=5`
   - `GET /api/toss/raw?path=/api/v1/candles&symbol=005930&interval=1d` — 임의의 `/api/v1/**` 경로에 임의의 쿼리 파라미터로 호출. 캔들처럼 **파라미터 이름부터 찾아야 할 때** 코드 수정 없이 시도해볼 수 있습니다.
   - `GET /api/toss/probe/candle-intervals?symbol=005930` — 캔들 주기 후보들을 한 번에 시험해서 API가 받아주는 값을 찾아줍니다.
5. 그 다음 대시보드/관심종목 등 나머지 기능을 실 데이터로 확인하세요.

## 현재 상태 (1~3단계)

- **토스증권 연동**: 위 "토스증권 실연동" 절 참고. `MockTossApiClient`(랜덤워크 가상 시세, 52주 고저·평균거래량·60일 캔들 포함)가 기본이고, `TossHttpApiClient`는 인증/경로/에러처리는 문서 기준으로 확정, 응답 필드 매핑만 남았습니다. 어느 쪽이든 호출부(서비스/컨트롤러)는 수정할 필요가 없습니다.
- **관심종목**: 추가/삭제/목록 조회 (`/api/watchlist`), 실시간(모의) 시세·등락률 포함
- **알림 규칙**: 목표가 이상/이하, 등락률(±N%), 거래량 급증(평균 대비 N배), 52주 신고가/신저가 근접 — 6가지 조건 × 디스코드/이메일/인앱 채널, 쿨다운, 활성/비활성 토글, 생성/삭제 (`/api/alert-rules`)
- **스케줄러**: 60초 주기로 활성 규칙을 평가하고 조건 충족 시 알림 발송 (`PriceAlertScheduler`)
- **알림 히스토리**: 대시보드에는 최근 알림 미리보기(`/api/alert-logs/recent`), 전용 화면에는 채널/상태 필터 + 페이지네이션을 갖춘 전체 히스토리(`/api/alert-logs`). 인앱 알림은 읽음/안읽음 상태를 관리하고(`/api/alert-logs/unread-count`, `PATCH .../{id}/read`, `POST .../mark-all-read`) 탭에 안읽음 뱃지로 표시
- **캔들 차트**: 관심종목/보유종목 선택 후 일봉 캔들 차트 확인 (`/api/candles`, 별도 차트 라이브러리 없이 자체 SVG 렌더링)
- **자산 추이 그래프**: `AccountSnapshotScheduler`가 주기적으로(기본 15분, 로컬 데모용으로 짧게 잡음) 자산 총액을 저장하고 (`/api/dashboard/history`), 대시보드에 라인 차트로 표시
- **다이제스트 알림**: 매일 08:00(설정 가능)에 그날 발송된 알림 요약 + 현재 자산현황을 이메일로 발송 (`DigestScheduler`), 테스트용으로 `POST /api/digest/send-now`도 있음
- **대시보드**: 자산 요약·추이, 보유종목, 관심종목, 알림 규칙 관리를 한 화면에서 확인, 상단 탭으로 차트/알림 히스토리/설정 화면 전환
- **설정 화면**: 원 기획서(section 8-5)의 마지막 화면. 읽기전용 — 토스 API 키/계좌번호/디스코드 웹훅/SMTP/이메일 수신주소/다이제스트 설정이 되어 있는지만 보여주고 값 자체는 노출하지 않음 (`/api/settings/status`). 값 변경은 여전히 환경변수로만
- **미구현**: 실제 토스증권 연동, 다중 사용자/로그인 (원 기획의 `users` 테이블), Order API 연동 자동매매 — 전부 원 기획 문서에서도 선택/후순위 범위였던 부분

로컬에서 백엔드+프론트엔드를 함께 띄우면(위 "시작하기" 참고) `http://localhost:5173`에서 전체 대시보드를 확인할 수 있습니다.
