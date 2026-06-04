# FCM 푸시 작업 — 진행상황 & 이어서 할 일 (WIP)

> 이 문서는 작업 핸드오프용 임시 메모. **PR 올리기 전 삭제.**
> 브랜치: `feat/242-fcm-push` (origin 푸시됨). 내일: `git fetch && git switch feat/242-fcm-push`.

## 한 줄 요약
이벤트 → 알림 생성(m-a-king 토대) → **FCM 푸시 발송** 경로를 붙이는 작업. 토큰 수집 API·테이블·Firebase 초기화까지 완료, **발송 실구현(#245)이 남음.**

---

## ✅ 완료 (커밋됨)

### #242 — Firebase Admin SDK + 초기화
- `build.gradle.kts`: `com.google.firebase:firebase-admin:9.9.0` (BOM 미관리라 버전 명시)
- `notification/fcm/FirebaseConfig.kt`: `FIREBASE_SERVICE_ACCOUNT`(base64) → `FirebaseApp` 초기화. `@ConditionalOnProperty(firebase.service-account)` 라 **키 없는 로컬/테스트에선 빈 자체가 안 떠** 부팅 안 깨짐.
- **secret 등록 완료**: repo secret `FIREBASE_SERVICE_ACCOUNT` (dev/prod 공유, 프로젝트 `piki-54800`). Spring relaxed binding 으로 `FIREBASE_SERVICE_ACCOUNT` → `firebase.service-account` 자동 매핑.

### #243 — user_devices 테이블 + 엔티티/저장소
- 마이그레이션 `V20260604202838__create_user_devices.sql`: `(id, user_id BINARY16, device_id, fcm_token, created/updated/deleted_at)`, FK 없음.
  - **UNIQUE(`fcm_token`)** = 배달 정확성(토큰 1개=한 사용자). **UNIQUE(`user_id`, `device_id`)** = 기기당 1 row(leftmost 가 user_id 라 발송 조회도 커버).
  - **`push_enabled` 같은 on/off 컬럼 없음** — 동의 게이트는 OS 권한(클라만 읽음), 서버는 그냥 발송하고 OS 가 표시 결정.
- `notification/fcm/domain/UserDevice.kt`: `refreshToken()` 만. (소유자 재배정은 서비스의 reconcile 로직이 담당)
- `notification/fcm/repository/`: `UserDeviceRepository`(인터페이스) + `UserDeviceJpaRepository` + `UserDeviceRepositoryImpl`. finder: `findByFcmToken`, `findByUserIdAndDeviceId`, `findAllByUserId`, `deleteByUserIdAndDeviceId`, `deleteAllByFcmTokenIn`, `delete`, `flush`.

### #244 — 토큰 등록/해제 API
- `POST /api/v1/fcm/tokens { token, deviceId }` / `DELETE /api/v1/fcm/tokens { deviceId }`
- `notification/fcm/controller/`: `FcmTokenApi`(OpenAPI 전수 문서화 200/400/401) · `FcmTokenController` · `FcmTokenApiExamples`
- `notification/fcm/controller/dto/`: `FcmTokenRegisterRequest`, `FcmDeviceUnregisterRequest`
- `notification/fcm/service/UserDeviceService.kt`: `register`(upsert + 토큰 탈취 reconcile + flush), `unregister`(멱등)
- `SecurityConfig`: `/api/v1/fcm/**` → `authenticated()` (GUEST 포함)
- **reconcile 핵심**: 같은 토큰을 든 다른 row 를 먼저 delete + **flush** → 그래야 후속 save 가 UNIQUE(fcm_token) 안 깨짐(Hibernate INSERT-before-DELETE 순서 함정 회피).

---

## 🚧 남은 작업

### #245 — PushNotificationChannel 실구현 (진행중)
**이미 만든 것:** `notification/fcm/service/FcmMessageSender.kt` (외부 경계 인터페이스, `send(tokens, notification): List<String>` → 죽은 토큰 반환)

**해야 할 것:**
1. **`FirebaseMessageSender`** (`notification/fcm/service/`) — `@Component @ConditionalOnBean(FirebaseApp::class)`.
   - `FirebaseMessaging.getInstance(firebaseApp)` 보관.
   - `tokens.chunked(500)` → `MulticastMessage`(`setNotification(title/body)` + `putData("type", type.name)`, `putData("refId", refId.toString())`) → `sendEachForMulticast`.
   - 응답 순회: `messagingErrorCode in [UNREGISTERED, INVALID_ARGUMENT]` 인 토큰을 stale 로 수집해 반환. (토큰은 로그에 안 남김 — 민감)
2. **`PushNotificationChannel`** (`notification/service/PushNotificationChannel.kt`) NoOp → 실구현.
   - 생성자: `ObjectProvider<FcmMessageSender>` + `UserDeviceService`.
   - `send()`: `sender = provider.getIfAvailable() ?: return`(FCM 미설정 no-op) → `tokens = userDeviceService.findTokens(userId)` → 비면 return → `val stale = sender.send(tokens, n)` (트랜잭션 밖) → `userDeviceService.removeStaleTokens(stale)` (짧은 @Transactional).
3. **`UserDeviceService` 메서드 추가**: `findTokens(userId): List<String>`(`@Transactional(readOnly=true)`), `removeStaleTokens(tokens)`(`@Transactional`, 빈 리스트 early return).
4. **테스트 stub**: `support/StubFcmMessageSender`(default 람다 `emptyList`, 호출 기록 counter) + `IntegrationStubs` 에 `@Bean @Primary` 등록. → PushNotificationChannel 이 테스트에서 stub 주입받아 fan-out·정리 로직 검증.
5. **deploy.yml 배선**: `docker run` 에 `-e FIREBASE_SERVICE_ACCOUNT` + job env `FIREBASE_SERVICE_ACCOUNT: ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}`.
6. **통합 테스트**: stub 으로 "이벤트 → 알림 저장 → PushChannel 이 토큰 조회·발송·죽은토큰 삭제" + register/unregister API contract(200/400/401) + reconcile(토큰 재등록 시 이전 row 해제).

### #246 — 알림 조회/badge API (후속)
`notifications` 테이블 읽기. 목록 + unread-count + 읽음 처리. (수신자 정책 #236 과 함께)

### #247 — 토너먼트 이벤트 발행 (후속)
`TournamentJoined`/`TournamentItemAdded` 발행 지점 연결 + 수신자 정책 구현(현재 핸들러 `resolveRecipients` 전부 `emptySet()` = #236 보류).

---

## ❓ 미해결 결정 (조재중과 논의중)
- **로그아웃 시 토큰 삭제 방식**:
  - (A) 분리: 클라가 `DELETE /fcm/tokens {deviceId}` → `/auth/logout`. (현재 #244 구현)
  - (B) userId 전체삭제: `/auth/logout` 시 `UserLoggedOut(userId)` 이벤트 → fcm 리스너가 그 유저 토큰 전부 삭제. FE 콜 1번. 단 멀티기기에서 타 기기 푸시 일시 끊김(다음 앱 진입 시 재등록으로 자가복구).
  - → B 로 가면 `DELETE` 엔드포인트 제거하고 이벤트 리스너로 대체.

## 설계 결정 메모
- on/off 인앱 플래그 안 둠 (OS 권한이 게이트). 앱 음소거 기능 생기면 user 레벨 선호 테이블 additive.
- Android: 같은 `user_devices`/엔드포인트/발송 경로 공통. 합류 시 `platform` 컬럼 additive + `setAndroidConfig/setApnsConfig` 분기만.
- 포그라운드 인앱 알림은 SSE(재중, 기존), 백그라운드/종료는 FCM(이 작업).

## 검증
- **자동**: stub 통합 테스트(위 #245-6). FE/실토큰 불필요.
- **실배달 sanity**: 가짜 토큰은 FCM 거부 → 진짜 토큰 필요. RN 앱 없이 하려면 웹 FCM 테스트 페이지(웹 토큰, 브라우저 수신) 또는 하은 dev 빌드 토큰 1개.
- **테스트 실행 전 Docker 가드**(CLAUDE.md): `docker info >/dev/null 2>&1 || (open -a Docker && until docker info ...)` → `./gradlew test`.

## 참고
- FE 핸드오프 문서(gist): https://gistpreview.github.io/?4d19a6d49c0dcecd90fa158a6386a097
- ktlint: 기존 dev 파일들도 `ktlintMainSourceSetCheck` 실패 → CI 가 이 task 를 블로킹 게이트로 안 쓰는 듯. 내 `*Api.kt` 도 기존 `AuthApi` 와 동일 스타일이라 일관. PR 전 실제 CI lint 설정만 확인.
