# FCM 푸시 작업 — 진행상황 & 이어서 할 일 (WIP)

> 이 문서는 작업 핸드오프용 임시 메모. **PR 올리기 전 삭제.**
> 브랜치: `feat/242-fcm-push` (origin 푸시됨). 이어서: `git fetch && git switch feat/242-fcm-push`.

## 한 줄 요약
이벤트 → 알림 생성(m-a-king 토대) → **FCM 푸시 발송** 경로를 붙이는 작업. **발송 실구현(#245)까지 완료** —
토큰 수집·발송·죽은 토큰 정리·dev 테스트 API·deploy 배선 끝. 실제 이벤트 자동 발송만 **#236(수신자 정책)** 대기.

---

## ✅ 완료 (커밋됨)

### #242 — Firebase Admin SDK + 초기화
- `build.gradle.kts`: `com.google.firebase:firebase-admin` (BOM 미관리라 버전 명시)
- `notification/fcm/FirebaseConfig.kt`: `FIREBASE_SERVICE_ACCOUNT`(base64) → `FirebaseApp` 초기화. `@ConditionalOnProperty(firebase.service-account)` 라 **키 없는 로컬/테스트에선 빈 자체가 안 떠** 부팅 안 깨짐.
- **secret 등록 완료**: repo secret `FIREBASE_SERVICE_ACCOUNT` (dev/prod 공유, 프로젝트 `piki-54800`). Spring relaxed binding 으로 자동 매핑.

### #243 — user_devices 테이블 + 엔티티/저장소
- 마이그레이션 `V20260604202838__create_user_devices.sql`: `(id, user_id BINARY16, device_id, fcm_token, ...)`, FK 없음.
  - **UNIQUE(`fcm_token`)** = 배달 정확성(토큰 1개=한 사용자). **UNIQUE(`user_id`, `device_id`)** = 기기당 1 row.
  - on/off 컬럼 없음 — 동의 게이트는 OS 권한, 서버는 그냥 발송.
- `UserDevice`(refreshToken만) + `UserDeviceRepository`/`Jpa`/`Impl`.

### #244 — 토큰 등록/해제 API
- `POST /api/v1/fcm/tokens { token, deviceId }` / `DELETE /api/v1/fcm/tokens { deviceId }`
- `FcmTokenApi`(OpenAPI 전수 문서화 200/400/401) · `FcmTokenController` · `FcmTokenApiExamples` · 요청 DTO 2종
- `UserDeviceService.register`(upsert + 토큰 탈취 reconcile + flush), `unregister`(멱등)
- `SecurityConfig`: `/api/v1/fcm/**` → `authenticated()` (GUEST 포함)
- **reconcile 핵심**: 같은 토큰 든 다른 row 를 먼저 delete + **flush** → 후속 save 가 UNIQUE(fcm_token) 안 깨짐.
- **✅ 통합테스트 추가**: `FcmTokenControllerIntegrationTest` — 등록 200·저장, 400(빈 토큰), 401, upsert(같은 기기 토큰 교체), reconcile(타 유저 등록 시 이전 row 해제), 해제 200·삭제, 멱등, 401.

### #245 — FCM 발송 실구현 ✅ 완료
- `FcmMessageSender`(외부 경계 인터페이스, `send(tokens, n): List<String>` → 죽은 토큰 반환)
- **`FirebaseMessageSender`** (`@Component @ConditionalOnBean(FirebaseApp)`): `chunked(500)` → `MulticastMessage`(notification + data) → `sendEachForMulticast`. 죽은 토큰(UNREGISTERED/INVALID_ARGUMENT) 수집해 반환. 토큰은 로그에 안 남김.
  - **플랫폼 확장 seam**: `applyPlatformConfig()` extension 으로 분기 자리만 열어둠(지금 iOS, 공통 notification 으로 충분). Android/Web 합류 시 (1) `user_devices.platform` 컬럼 additive (2) `findTokens` 플랫폼 그룹 반환 (3) 이 함수에 `setApnsConfig/setAndroidConfig/setWebpushConfig` 분기 — 인터페이스·호출부 불변.
  - **상수화**: FCM data 키 `DATA_KEY_TYPE`/`DATA_KEY_REF_ID` (FE contract, `NotificationSsePayload` 필드명과 일치), `MULTICAST_LIMIT=500`.
  - **단위테스트**: `FirebaseMessageSenderTest` — `isStaleToken` 분기 전수(EnumSource: UNREGISTERED/INVALID→정리, 그외/null→보존).
- **`PushNotificationChannel`** NoOp → 실구현: `ObjectProvider<FcmMessageSender>` + `UserDeviceService`. `getIfAvailable() ?: return`(FCM 미설정 no-op) → `findTokens` → 비면 return → `sender.send`(트랜잭션 밖) → `removeStaleTokens`(짧은 @Transactional). SSE 채널과 대칭.
- **`UserDeviceService`** 추가: `findTokens`(readOnly), `removeStaleTokens`(빈 입력 early return).
- **테스트 stub**: `StubFcmMessageSender`(default `onSend` throw — CLAUDE.md 정책, 토큰 없는 테스트는 채널 early return 으로 미도달) + `IntegrationStubs` 에 `@Primary` 등록.
- **통합테스트**: `PushNotificationChannelIntegrationTest` — 채널 리스트 합류, 멀티캐스트 fan-out, 죽은 토큰 정리, 토큰 없는 유저 미발송.
- **deploy.yml 배선**: env 블록 + `envs:` 목록 + `docker run -e FIREBASE_SERVICE_ACCOUNT` 3곳 완료.

### dev 무한 발송 테스트 API ✅
- `POST /api/v1/dev/fcm/push` (`@Profile("!prod")`, GUEST 권한): 인증 본인의 모든 기기로 즉시 발송. 발송 경로(`PushNotificationChannel`)를 그대로 재사용 → **이 API 로 검증한 동작이 곧 이벤트 경로의 동작.** 응답 `targetTokenCount`·`fcmEnabled` 로 실배달 디버깅.
- `DevFcmApi`/`DevFcmController`/`DevFcmApiExamples` + `DevPushRequest`(title/body 기본값, `toNotification`)/`DevPushResponse`.
- **무한 send** = 이 POST 반복 호출.

---

## 🚧 남은 작업

### #236 — 수신자 정책 (이벤트 자동 발송의 유일한 잔여 의존)
- 현재 4개 핸들러 `resolveRecipients` 전부 `emptySet()` → dispatcher 가 `recipients.isEmpty()` early return → **이벤트 발행해도 발송 안 됨.**
- 풀어야 할 것: (a) 기획 합의 — 특히 토너먼트 `owner-only vs 참가자 fan-out(actor 제외)`, (b) 역조회 구현 — itemId→위시 owner, tournamentId→참가자.
- **발송 코드는 완성** — 핸들러의 `resolveRecipients` 한 곳만 채우면 이벤트 경로가 자동 연결된다(채널·dispatcher 불변).

### #246 — 알림 조회/badge API (후속)
`notifications` 읽기. 목록 + unread-count + 읽음 처리.

### #247 — 토너먼트 이벤트 발행 (후속)
`TournamentJoined`/`TournamentItemAdded` 발행 지점 연결 (+ #236 수신자 정책).

---

## ❓ 미해결 결정 (슬랙 논의 — 여전히 미확정)
- **로그아웃 시 토큰 삭제 방식** ("확정 아니니까 편하게"):
  - (A, 현재 구현) 분리: `DELETE /fcm/tokens {deviceId}` → `/auth/logout`.
  - (B) userId 전체삭제: `/auth/logout` 시 이벤트 → fcm 리스너가 그 유저 토큰 전부 삭제. FE 콜 1번. 단 멀티기기 타 기기 푸시 일시 끊김(재진입 시 자가복구).
  - 세빈 의견: auth 가 device 를 몰라 logout 에서 한 기기만 지우긴 어려움 → 현 A안 유지. #245 범위 밖.

## 설계 결정 메모
- **send 추상화 = FCM 단일**: FCM 한 발송이 APNS/Android/WebPush 를 다 라우팅하므로 sender 를 쪼개지 않고 `applyPlatformConfig` 한 곳에서만 분기(위 #245). 지금은 iOS push 만.
- **FE 인터페이스 확정**(슬랙): FE(하은)는 "FCM 토큰 + deviceID 수집 → 서버 전송"만. 우리 `FcmTokenRegisterRequest{token,deviceId}` 가 정확히 그 구조.
- on/off 인앱 플래그 없음(OS 권한 게이트). 앱 음소거 생기면 user 레벨 선호 additive.
- 포그라운드 인앱은 SSE(재중), 백그라운드/종료는 FCM(이 작업). 두 채널은 `NotificationDispatcher.channels` 리스트로만 공유되고 전달 수단은 각자 대칭(SSE=인메모리 emitter, FCM=user_devices 토큰).

## 토큰(pushId) 생명주기 — 테스트 운영
- **재빌드/덮어쓰기 설치로는 안 바뀜.** 바뀌는 건: 앱 삭제 후 재설치 · 데이터 초기화 · 시뮬레이터 wipe · SDK 자동갱신(드묾) · ~270일 미사용.
- 토큰 바뀌어도 앱이 `onNewToken` → `POST /fcm/tokens` 재등록하면 upsert 가 투명 흡수.
- **한 번 받으면 며칠 재사용 가능** — 매번 실시간 소통 불필요. 서버 검증은 받아둔 토큰으로 비동기, "실제 폰 도착"만 하은이 육안.

## 검증
- **자동**: 단위(`FirebaseMessageSenderTest`) + 통합(`PushNotificationChannelIntegrationTest`, `FcmTokenControllerIntegrationTest`). 통과 확인. 실토큰 불필요.
- **실배달 (로컬에서 dev 머지 없이 가능)**: FCM 발송은 아웃바운드라 로컬 서버 → Firebase → 폰. ① `.env.local` 에 `FIREBASE_SERVICE_ACCOUNT` ② 하은 진짜 토큰 `/fcm/tokens` 등록 ③ `/dev/fcm/push` 무한 호출 ④ 폰 수신 확인. 키 없으면 `fcmEnabled=false` no-op.
- 가짜 토큰은 FCM 거부 → 진짜 토큰 필요(하은 dev 빌드 또는 웹 FCM 토큰). dry-run(`validateOnly`)은 유효성만.

## 참고
- FE 핸드오프 문서(gist): https://gistpreview.github.io/?4d19a6d49c0dcecd90fa158a6386a097
- ktlint: 기존 dev 파일들도 `ktlintMainSourceSetCheck` 실패 → CI 블로킹 게이트 아님. 내 `*Api.kt` 도 기존 스타일과 일관. PR 전 실제 CI lint 설정만 확인.
