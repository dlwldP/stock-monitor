# stock-monitor

토스증권 Open API를 연동해 **관심종목 알림**과 **자산 대시보드**를 한 서비스에서 제공하는 프로젝트.

- 대시보드: 계좌·보유종목·관심종목 시세와 손익, 자산 추이, 일봉 캔들 차트를 한 화면에서 확인
- 알림: 목표가/등락률/거래량 급증 등 조건을 설정하면 디스코드·이메일·인앱으로 알림

토스증권 실계좌 연동까지 동작 확인했고, API 키 없이도 Mock 시세로 전 기능이 돌아갑니다.

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
- 테스트: `./gradlew test` — 54개. 도메인 로직(알림 조건 판정·쿨다운)·서비스·스케줄러·알림 디스패처 단위 테스트(Mockito 기반)와, 토스 API 응답 매핑을 실제 응답 구조에 고정하는 테스트

### 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

- 기본 포트: `5173`
- 개발 서버는 `/api/*` 요청을 백엔드(`localhost:8080`)로 프록시합니다 (`frontend/vite.config.ts`).

## 환경변수 / 시크릿

`client_secret`, 계좌 정보, Discord Webhook URL 등 민감정보는 **레포에 커밋하지 않고** 환경변수로만 주입합니다. 이 레포에는 실제 키·계좌번호·잔고가 들어있지 않습니다 (설정 화면도 값이 아니라 "설정됨/미설정" 여부만 보여줍니다). 백엔드는 다음 환경변수를 읽습니다 (`backend/src/main/resources/application.yml` 참고):

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

**실계좌 연동 동작 확인 완료** — 대시보드(계좌 요약·보유종목), 관심종목 시세, 일봉 캔들 차트가 실제 계좌 데이터로 동작합니다.

`TossApiClient` 인터페이스 뒤에 두 구현체가 있고, 호출부(서비스/스케줄러/컨트롤러)는 어느 쪽이든 수정할 필요가 없습니다:

- `MockTossApiClient` — **기본값**. 랜덤워크 가상 시세로 전 기능을 개발/검증했습니다. 키 없이도 앱 전체가 동작합니다.
- `TossHttpApiClient` — 실제 API 호출. `TOSS_API_USE_REAL_CLIENT=true`로 **직접 켜기 전까지는** 키를 넣어도 계속 Mock이 서빙합니다 (`TossApiClientConfig`가 빈을 교체).

### 설정 순서

1. WTS **설정 > Open API** 메뉴에서 `client_id`/`client_secret` 발급, **허용 IP** 목록에 서버 IP 등록.
   - 미등록 IP면 토큰 발급이 `403 access_denied: IP address not allowed`로 막힙니다. 가정용 인터넷은 보통 유동 IP라 IP가 바뀌면 다시 등록해야 합니다.
2. 키를 넣고 `TOSS_API_USE_REAL_CLIENT=true`로 켠 뒤, `POST /api/toss/verify-connection`으로 **OAuth 토큰 발급만** 먼저 확인하세요 (데이터 엔드포인트는 건드리지 않습니다).
3. `GET /api/toss/accounts`로 계좌 목록을 조회해 응답의 **`accountSeq`** 값을 `TOSS_ACCOUNT_SEQ`로 설정하세요 (보통 `1` 같은 작은 정수).
   - **계좌번호(`accountNo`)가 아닙니다.** 계좌번호를 넣으면 `account-not-found`가 납니다.
   - 이 엔드포인트는 `accountSeq` 없이도 동작합니다 — 그 값을 찾는 게 목적이라서요.
4. 대시보드/관심종목/차트를 실 데이터로 확인하세요.

### API 스펙 (실제 호출로 확인한 것)

공식 문서([developers.tossinvest.com/docs](https://developers.tossinvest.com/docs))에는 엔드포인트 목록만 있고 요청/응답 스키마 상세는 없어서, 아래 대부분은 **실제로 호출해보고 알아낸 내용**입니다.

**인증·공통**

- base URL `https://openapi.tossinvest.com`, OAuth2 client-credentials (`POST /oauth2/token`, form-urlencoded)
- 계좌·자산 API는 `X-Tossinvest-Account` 헤더 필요. 시세·캔들은 토큰만 있으면 호출 가능
- 에러 포맷 `{"error":{"requestId","code","message","data"}}` → `TossApiException`. 단 **토큰 엔드포인트만 표준 OAuth2 포맷**(`{"error","error_description"}`)을 씁니다
- 429면 `Retry-After`를 존중해 1회 재시도
- 에러 응답이 **gzip으로 압축되어 올 수 있습니다.** JDK `HttpURLConnection`은 에러 스트림을 자동 해제하지 않아서, 직접 풀지 않으면 진짜 에러 메시지 대신 JSON 파싱 에러만 보입니다 (`TossErrorBodyReader`)
- 모든 응답이 `{"result": ...}` 봉투에 감싸여 옵니다

**엔드포인트별**

| 엔드포인트 | 요청 | 응답 |
|---|---|---|
| `GET /api/v1/prices` | `symbols` | `result`는 **배열**. `{"symbol","timestamp","lastPrice","currency"}`뿐 — 등락률·거래량·52주 고저 **없음** (아래 제약 참고) |
| `GET /api/v1/holdings` | (계좌 헤더) | `result`는 **객체**. 계좌 집계(`marketValue`/`profitLoss`/`dailyProfitLoss`, 각각 KRW·USD)와 종목 배열(`items`), 종목마다 `lastPrice` 포함 |
| `GET /api/v1/candles` | `symbol`(단수) + `interval=1d` | `result`는 객체(배열 + `nextBefore` 커서). 항목은 `{"timestamp","openPrice",...,"volume","currency"}`, **최신순** |
| `GET /api/v1/accounts` | — | `{"accountNo","accountSeq","accountType"}` |

주의할 점 몇 가지:

- **금액·수량은 전부 문자열**로 옵니다 (`"48000"`).
- **`rate` 계열은 퍼센트가 아니라 소수**입니다 (`"-0.0026"` = -0.26%).
- 종목의 시장 구분은 `marketCountry`(`"KR"`/`"US"`)입니다. 매핑을 빠뜨리면 미국 주식이 원화로 계산됩니다.
- 캔들 `timestamp`는 자정+KST(`2026-06-08T00:00:00.000+09:00`)라서, Jackson 기본 설정(`ADJUST_DATES_TO_CONTEXT_TIME_ZONE`)으로 바인딩하면 UTC로 변환되며 **하루씩 밀립니다.** 그래서 문자열로 받아 오프셋 그대로 파싱합니다 (`CandleDto.tradingDate()`).
- 캔들에 `count`류 파라미터는 확인된 게 없어서 보내지 않습니다 (모르는 필드를 보내면 요청 전체가 실패). 받은 뒤 필요한 일수만큼 잘라 씁니다. 더 과거 데이터는 `nextBefore` 커서로 페이징 가능하나 아직 사용하지 않습니다.
- 계좌 요약은 별도 엔드포인트가 없지만 보유종목 응답이 집계를 이미 포함하고 있어서 그대로 읽습니다 (종목별 시세를 다시 조회하지 않음). Mock은 여전히 보유종목 × 시세로 합산합니다.

문서가 아니라 실제 호출로 알아낸 필드명이라, 매핑은 `TossApiResponseMappingTest`로 고정해뒀습니다 — 스키마가 바뀌면 대시보드가 조용히 0원/빈 차트로 뜨는 대신 테스트가 깨집니다. (테스트 픽스처는 실제 응답의 **구조만** 재현하고 종목·금액은 가짜 값입니다.)

### 실연동 시 제약 (Mock과의 차이)

`GET /api/v1/prices`가 마지막 체결가만 돌려주기 때문에, 실연동 모드에서는 알림 조건 6개 중 **2개만 동작**합니다:

| 조건 | Mock | 실연동 |
|---|---|---|
| 목표가 이상/이하 (PRICE_ABOVE/BELOW) | ✅ | ✅ |
| 등락률 (PCT_CHANGE) | ✅ | ❌ 데이터 없음 |
| 거래량 급증 (VOLUME_SPIKE) | ✅ | ❌ 데이터 없음 |
| 52주 신고가/신저가 근접 (WEEK52_*) | ✅ | ❌ 데이터 없음 |

데이터가 없는 조건은 예외를 던지지 않고 **그냥 발동하지 않습니다** (`AlertRule.isSatisfiedBy`) — 규칙 하나 때문에 폴링 전체가 죽지 않도록. 관심종목 화면의 등락률도 `-`로 표시됩니다. 등락률/거래량/52주 정보를 주는 엔드포인트를 찾으면 `TossHttpApiClient.getQuote`만 고치면 됩니다.

### 진단용 엔드포인트

응답 스키마를 확인하거나 문제를 좁힐 때 쓰는 것들입니다. **계좌 원문을 그대로 돌려주므로 로컬 개발용입니다** (배포 시 주의 — 위 "배포 전에 확인할 것" 참고):

| 엔드포인트 | 용도 |
|---|---|
| `POST /api/toss/verify-connection` | OAuth 토큰 발급만 확인 |
| `GET /api/toss/accounts` | 계좌 목록 → `accountSeq` 확인 |
| `GET /api/toss/raw/holdings` | 보유종목 원문 JSON |
| `GET /api/toss/raw/prices?symbol=005930` | 시세 원문 JSON |
| `GET /api/toss/raw/candles?symbol=005930&days=5` | 캔들 원문 JSON |
| `GET /api/toss/raw?path=/api/v1/...&아무_파라미터=값` | 임의 `/api/v1/**` 경로에 임의 파라미터로 호출. **파라미터 이름부터 찾아야 할 때** 코드 수정 없이 시도 |
| `GET /api/toss/probe/candle-intervals?symbol=005930` | 캔들 주기 후보를 한 번에 시험 (`?intervals=A,B,C`로 후보 직접 지정) |

## 현재 상태 (1~3단계)

- **토스증권 연동**: 실계좌 연동 동작 확인 완료 (위 "토스증권 실연동" 절 참고). 기본값은 여전히 `MockTossApiClient`(랜덤워크 가상 시세, 52주 고저·평균거래량·60일 캔들 포함)이고, `TOSS_API_USE_REAL_CLIENT=true`로 켜면 실 API를 씁니다. 알림 조건 일부는 실연동에서 데이터가 없어 동작하지 않습니다 ("실연동 시 제약" 참고).
- **관심종목**: 추가/삭제/목록 조회 (`/api/watchlist`), 시세 포함 (등락률은 Mock에서만)
- **알림 규칙**: 목표가 이상/이하, 등락률(±N%), 거래량 급증(평균 대비 N배), 52주 신고가/신저가 근접 — 6가지 조건 × 디스코드/이메일/인앱 채널, 쿨다운, 활성/비활성 토글, 생성/삭제 (`/api/alert-rules`)
- **스케줄러**: 60초 주기로 활성 규칙을 평가하고 조건 충족 시 알림 발송 (`PriceAlertScheduler`)
- **알림 히스토리**: 대시보드에는 최근 알림 미리보기(`/api/alert-logs/recent`), 전용 화면에는 채널/상태 필터 + 페이지네이션을 갖춘 전체 히스토리(`/api/alert-logs`). 인앱 알림은 읽음/안읽음 상태를 관리하고(`/api/alert-logs/unread-count`, `PATCH .../{id}/read`, `POST .../mark-all-read`) 탭에 안읽음 뱃지로 표시
- **캔들 차트**: 관심종목/보유종목 선택 후 일봉 캔들 차트 확인 (`/api/candles`, 별도 차트 라이브러리 없이 자체 SVG 렌더링)
- **자산 추이 그래프**: `AccountSnapshotScheduler`가 주기적으로(기본 15분, 로컬 데모용으로 짧게 잡음) 자산 총액을 저장하고 (`/api/dashboard/history`), 대시보드에 라인 차트로 표시
- **다이제스트 알림**: 매일 08:00(설정 가능)에 그날 발송된 알림 요약 + 현재 자산현황을 이메일로 발송 (`DigestScheduler`), 테스트용으로 `POST /api/digest/send-now`도 있음
- **대시보드**: 자산 요약·추이, 보유종목, 관심종목, 알림 규칙 관리를 한 화면에서 확인, 상단 탭으로 차트/알림 히스토리/설정 화면 전환
- **설정 화면**: 원 기획서(section 8-5)의 마지막 화면. 읽기전용 — 토스 API 키/계좌번호/디스코드 웹훅/SMTP/이메일 수신주소/다이제스트 설정이 되어 있는지만 보여주고 값 자체는 노출하지 않음 (`/api/settings/status`). 값 변경은 여전히 환경변수로만
- **미구현**: 다중 사용자/로그인 (원 기획의 `users` 테이블), Order API 연동 자동매매 — 둘 다 원 기획 문서에서도 선택/후순위 범위였던 부분

로컬에서 백엔드+프론트엔드를 함께 띄우면(위 "시작하기" 참고) `http://localhost:5173`에서 전체 대시보드를 확인할 수 있습니다.
