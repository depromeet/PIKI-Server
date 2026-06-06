# 실시간 알림 (SSE) 명세

> API docs(OpenAPI/Stoplight)는 요청→단일 JSON 응답 모델이라 **스트림(text/event-stream)** 을 제대로 표현하지 못한다. 이 문서는 그 공백을 메우는 클라이언트용 명세다. (자동 생성 docs 아님, 수동 관리)

---

## 1. 개요

- 인증된 유저가 **자기 알림 스트림 1개**를 열어두면, 개인 알림과 토너먼트 알림이 모두 그 스트림으로 흘러온다.
- 프로토콜: **SSE (Server-Sent Events)**, 단방향(서버→클라이언트), `text/event-stream`.
- 토너먼트 알림은 해당 토너먼트 참여자에게만 fan-out 되므로, **스트림은 유저당 1개면 충분**하다 (토너먼트별로 따로 열 필요 없음).

---

## 2. 엔드포인트

| 항목 | 값 |
|---|---|
| Method | `GET` |
| Path | `/api/v1/notifications/subscribe` |
| 응답 Content-Type | `text/event-stream` |
| 인증 | 필요 (GUEST 포함 인증 유저면 OK) |

---

## 3. 인증 (중요 — 클라이언트 구현이 갈리는 지점)

서버는 토큰을 **이 우선순위**로 읽는다:

1. `Authorization: Bearer <accessToken>` 헤더 (우선)
2. 없으면 `access_token` **쿠키**

### 플랫폼별 권장

- **WEB (브라우저 네이티브 `EventSource`)**
  - 브라우저 `EventSource` 는 **커스텀 헤더(`Authorization`)를 보낼 수 없다.** 따라서 **쿠키 인증**에 의존한다.
  - 같은 도메인/CORS 설정에서 `access_token` 쿠키가 자동 전송되도록 `withCredentials: true` 로 연다.
    ```js
    const es = new EventSource("/api/v1/notifications/subscribe", { withCredentials: true });
    ```
  - 헤더로 토큰을 꼭 실어야 하면 `EventSourcePolyfill` 같은 라이브러리로 헤더 주입이 가능하다.
- **APP (직접 HTTP 스트림 처리)**
  - `Authorization: Bearer <accessToken>` 헤더로 보내면 된다.

> 미인증/유효하지 않은 토큰 → **401** (아래 6번). 스트림이 열리기 전에 거절된다.

---

## 4. 스트림에 흐르는 이벤트 3종

SSE 한 연결 위로 아래 세 가지가 흐른다. 클라이언트는 **이벤트 `name`** 으로 구분한다.

### (1) `connect` — 연결 성립 신호

구독 직후 **1회** 전송. 응답 헤더를 즉시 flush 해 클라이언트가 "연결됨"을 곧장 인지하게 한다.

```
event: connect
data: connected
```

### (2) `notification` — 알림 1건 (핵심)

알림이 발생할 때마다 전송. `data` 는 아래 [5. payload](#5-notification-payload-스키마) JSON.

```
event: notification
data: {"id":123,"type":"TOURNAMENT_JOINED","title":"홍길동님이 참가했어요","body":"","refId":45,"isRead":false,"createdAt":"2026-06-06T14:32:10"}
```

### (3) 하트비트 (주석 ping)

약 **15초 간격**. 연결 유지(프록시 idle timeout 회피)용이며 **`data` 이벤트가 아니다.** SSE 주석 라인(`:` 으로 시작)이라 `EventSource` 의 `onmessage`/리스너에 잡히지 않는다. **클라이언트는 무시하면 된다.**

```
: ping
```

---

## 5. notification payload 스키마

`notification` 이벤트의 `data` 로 직렬화되는 JSON.

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number (long) | 알림 식별자. 추후 읽음 처리 API 의 키. |
| `type` | string (enum) | 알림 종류. 딥링크 분기 키. (아래 표) |
| `title` | string | 표시용 제목. 발송 시점에 변수 치환이 끝난 완성본. |
| `body` | string | 표시용 본문. **현재는 전 타입 빈 문자열(`""`)** (후속 템플릿 작업에서 채워질 예정). |
| `refId` | number (long) | 딥링크 대상 식별자. `type` 에 따라 가리키는 대상이 다름 (아래 표). |
| `isRead` | boolean | 읽음 여부. SSE 로 즉시 도착하는 알림은 사실상 `false`. |
| `createdAt` | string (ISO-8601 LocalDateTime) | 생성 시각. 예: `2026-06-06T14:32:10`. 타임존 오프셋 없음(서버 로컬). |
| `kind` | string (enum) \| 생략 | **파싱 알림(`ITEM_PARSING_*`) 전용** 출처 구분. `WISH` \| `TOURNAMENT`. 그 외 타입엔 키 자체가 없다. |
| `tournamentId` | number (long) \| 생략 | **`kind=TOURNAMENT` 일 때만.** 딥링크로 입장할 토너먼트. |
| `tournamentItemId` | number (long) \| 생략 | **`kind=TOURNAMENT` 일 때만.** 입장한 토너먼트 안에서 지목할 출전 아이템. |

> **라우팅 컨텍스트(`kind`·`tournamentId`·`tournamentItemId`)는 파싱 알림에만, 그것도 조건부로 실린다.** 같은 `type`(`ITEM_PARSING_*`)이 위시·토너먼트 두 출처에서 발행돼 `refId`(=itemId)만으론 출처를 구분할 수 없어 추가한다. 값이 없는 키는 **JSON 에서 아예 생략**된다(NON_NULL). 서버는 완성 URL 을 박지 않고 도메인 식별자만 내려보내며, URL 조립은 클라이언트가 한다.

---

## 6. 알림 타입(`type`)별 의미 — 딥링크 분기표

클라이언트는 `type` 으로 화면을 분기하고 `refId` 로 이동 대상을 정한다.

| `type` | 의미 | `title` (현재 문구) | `refId` 가 가리키는 것 | 수신자 |
|---|---|---|---|---|
| `TOURNAMENT_JOINED` | 누군가 내 토너먼트에 참가 | `{참가자}님이 참가했어요` | **tournamentId** | 해당 토너먼트 참여자 (행위자 제외) |
| `TOURNAMENT_ITEM_ADDED` | 누군가 토너먼트에 아이템 추가 | `{참가자}님이 아이템을 추가했어요` | **tournamentId** | 해당 토너먼트 참여자 (행위자 제외) |
| `ITEM_PARSING_COMPLETED` | 내 상품 정보 추출 성공 | `상품 정보가 저장됐어요` | **itemId** | 본인 |
| `ITEM_PARSING_FAILED` | 내 상품 정보 추출 실패 | `상품 정보를 가져오지 못했어요` | **itemId** | 본인 |

> `title` 문구는 서버 템플릿에서 렌더된 값이라 바뀔 수 있다. 클라이언트는 **문구가 아니라 `type` 으로 분기**할 것.

### 파싱 알림(`ITEM_PARSING_*`)의 출처별 라우팅

파싱 알림은 위시 등록·토너먼트 추가 두 플로우에서 모두 발행되므로, `type`·`refId` 만으론 어디로 보낼지 알 수 없다. `kind` 와 토너먼트 식별자로 분기한다.

| `kind` | 추가로 실리는 필드 | 이동 대상 |
|---|---|---|
| `WISH` | 없음 | `/archive` |
| `TOURNAMENT` | `tournamentId` · `tournamentItemId` | `/tournament/{tournamentId}/create` 로 입장 후 `tournamentItemId` 로 그 아이템 지목 |

- `refId`(=itemId)는 두 출처 공통으로 항상 실리지만, 위시·토너먼트 이동에는 직접 쓰이지 않는다(향후 아이템 단위 참조용).
- 한 아이템은 파싱 시점에 단일 출처라 라우팅 식별자도 단건이다 (여러 토너먼트 동시 출전 같은 복수 대상은 발생하지 않는다).

---

## 7. 연결 수명 (lifecycle)

| 항목 | 값 | 비고 |
|---|---|---|
| 연결 타임아웃 | **30분** | 만료 시 서버가 연결을 닫음. |
| 하트비트 주기 | **15초** | 프록시 idle timeout(90s) 아래로 유지. |
| 재연결 | **클라이언트 책임** | 끊기면(타임아웃·네트워크) 재호출해 다시 연다. |
| 인증 검증 시점 | **연결을 여는 순간 1회만** | 아래 "토큰 만료" 참고. |

- `EventSource` 는 연결이 끊기면 **자동 재연결**을 시도한다(브라우저 기본 동작). APP 직접 구현은 재연결 로직을 직접 넣어야 한다.
- 다중 탭/기기 접속을 허용한다 (한 유저가 여러 연결을 동시에 열 수 있고, 알림은 그 전부에 전달된다).

### 토큰 만료와 재연결 (특히 APP)

인증은 **연결을 여는 순간 1회만** 검증된다 (SSE 는 단일 요청이 길게 열려 있는 형태라, 인증 필터가 그 시점에 한 번만 돈다).

- **연결 도중 access token 이 만료돼도 그 연결은 끊기지 않는다** (최대 30분 타임아웃까지 유지). 알림은 계속 도착한다.
- 문제는 **재연결 시점**이다. 타임아웃·네트워크 끊김으로 다시 열 때, 그때 들고 있는 토큰이 만료됐으면 **401 로 거절**된다.
- 따라서 클라이언트는 **재연결 전에 토큰 유효성을 확인하고, 만료됐으면 refresh 한 뒤 재연결**해야 한다.
  - WEB(쿠키 인증): `access_token` 쿠키가 만료되면 `/api/v1/auth/token/refresh` 로 갱신(쿠키 재발급) 후 재연결.
  - APP(헤더 인증): refresh 로 새 access token 을 받아 **재연결 요청의 `Authorization` 헤더에 새 토큰**을 실어 연다.

### 끊김 중 발생한 알림 (현재 한계)

- 현재 SSE 는 **연결 중에 발생한 알림만** 실시간 전달한다. **연결이 끊겨 있던 동안 쌓인 알림은 재연결해도 스트림으로 다시 오지 않는다.**
- 단, 모든 알림은 DB(`notifications`)에 영속되므로, **목록/배지 조회 API** 로 놓친 알림을 따라잡는 설계가 정석이다. (해당 조회 API 는 후속 작업)
- 따라서 권장 클라이언트 패턴: **앱 진입/재연결 시 목록 API 로 동기화 + SSE 로 실시간 갱신.**

---

## 8. 에러 응답

스트림이 열리기 전 단계의 거절은 일반 JSON(`ApiResponseBody`) 으로 내려간다.

| 상태 | 의미 | 응답 형태 |
|---|---|---|
| **401** | 미인증 (토큰 없음/유효하지 않음) | `ApiResponseBody` JSON (`status`, `code`, `detail`) |

---

## 9. 클라이언트 구현 예시

### WEB (브라우저, 쿠키 인증)

```js
const es = new EventSource("/api/v1/notifications/subscribe", { withCredentials: true });

es.addEventListener("connect", () => {
  console.log("SSE 연결됨");
});

es.addEventListener("notification", (e) => {
  const n = JSON.parse(e.data);
  // n.type 으로 분기, n.refId 로 딥링크
  switch (n.type) {
    case "TOURNAMENT_JOINED":
    case "TOURNAMENT_ITEM_ADDED":
      goToTournament(n.refId); // refId = tournamentId
      break;
    case "ITEM_PARSING_COMPLETED":
    case "ITEM_PARSING_FAILED":
      // 출처(kind)로 분기 — refId(itemId)는 이동에 직접 쓰지 않는다.
      if (n.kind === "TOURNAMENT") goToTournamentItem(n.tournamentId, n.tournamentItemId); // 입장 후 그 아이템 지목
      else goToArchive();      // kind === "WISH"
      break;
  }
  showToast(n.title);
});

es.onerror = () => {
  // EventSource 가 자동 재연결을 시도한다. 필요 시 추가 처리.
};
```

### APP (헤더 인증 + 재연결 시 토큰 갱신)

브라우저 `EventSource` 와 달리 앱은 네이티브 HTTP 스트림(Android `OkHttp-sse`, iOS `URLSession` 등)을 직접 다루므로 `Authorization` 헤더를 자유롭게 실을 수 있다. 핵심은 **재연결할 때마다 유효한 토큰으로 다시 여는 것**.

```kotlin
// 의사코드 (Android/OkHttp-sse 가정)
fun openSse() {
    val token = tokenStore.validAccessToken()   // 만료됐으면 refresh 후 새 토큰 반환
    val request = Request.Builder()
        .url("$BASE_URL/api/v1/notifications/subscribe")
        .header("Authorization", "Bearer $token")
        .build()

    EventSources.createFactory(client).newEventSource(request, object : EventSourceListener() {
        override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
            when (type) {
                "connect" -> { /* 연결됨 */ }
                "notification" -> {
                    val n = json.decode<NotificationPayload>(data)
                    // n.type 으로 분기, n.refId 로 딥링크 (tournamentId | itemId)
                }
                // 그 외(주석 ping 등)는 무시
            }
        }

        override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
            // 401 이면 토큰 만료 가능성 → refresh 후 재연결
            // 그 외 끊김도 backoff 후 openSse() 재호출 (자동 재연결 직접 구현)
            scheduleReconnect()  // 재연결 시 openSse() 가 다시 validAccessToken() 으로 최신 토큰을 싣는다
        }
    })
}
```

> 포인트: `validAccessToken()` 처럼 **연결을 여는 함수가 매번 최신 토큰을 가져오게** 하면, 재연결 경로가 자연히 "만료 시 refresh 후 새 토큰으로 재연결"이 된다.

### 핵심 체크리스트

- [ ] `type` 으로 분기, **문구로 분기하지 않기**
- [ ] `refId` 의미가 `type` 마다 다름 (tournamentId vs itemId)
- [ ] 주석 `: ping` 은 무시 (data 이벤트 아님)
- [ ] 재연결 시 목록 API 로 놓친 알림 동기화
- [ ] WEB 은 쿠키 인증(`withCredentials`), APP 은 `Authorization` 헤더
- [ ] **재연결 시 토큰이 만료됐으면 refresh 후 새 토큰으로 연결** (연결 도중 만료는 무관, 재연결 시점이 관건)

---

## 10. 참고 (서버 구현 위치)

- 컨트롤러: `notification/controller/NotificationSseController.kt`
- payload: `notification/controller/dto/NotificationSsePayload.kt`
- 타입 enum: `notification/domain/NotificationType.kt`
- 템플릿(임시): `notification/service/InMemoryNotificationTemplateProvider.kt`
- 인증/권한: `auth/config/SecurityConfig.kt` (`/api/v1/notifications/**` → authenticated)
