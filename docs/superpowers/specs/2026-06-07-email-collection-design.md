# 소셜 로그인 이메일 수집 재도입 (구글·애플)

- 이슈: #442
- 작성일: 2026-06-07

## 배경

`user_details.email` 컬럼은 OAuth 통합(#122) 진행 전 마이그레이션 `V20260527011356__drop_user_details_email` 에서 제거됐다. 제거 이유는 카카오 이메일 동의항목을 받으면 검수가 1-2주 걸려, 동의항목 0으로 면제받기 위함이었다(마케팅·인증·복구 어떤 용도로도 안 받기로 결정).

이후 **마케팅 발송·서비스 알림·계정 식별/복구** 용도로 이메일을 다시 수집하기로 결정했다. 단 이번 작업은 **수집·저장까지만** 다룬다 — 마케팅 수신 동의와 실제 메일 발송은 별도 작업이다(동의 없이 마케팅 발송 불가).

## 목표

소셜 로그인(구글·애플) 시 OAuth 가 주는 email 을 받아 `user_details` 에 저장한다. 카카오는 이번 범위에서 제외하되, 구조는 카카오 email 을 나중에 수용할 수 있게 둔다.

## 설계

### 1. 데이터 모델

- `user_details` 에 `email` 컬럼 재추가 — **nullable VARCHAR(255)**, FK 없음(프로젝트 정책).
  - nullable 인 이유: 애플(Private Relay 거부 또는 2회차 미제공), 카카오(이번 미수집), 기존 가입자.
- `UserDetail` 엔티티에 `email: String?` 필드 추가.
- 탈퇴 시 `WithdrawalPersistenceService` 가 `user_details` 행을 하드삭제하므로(#423 cascade), email 도 자동으로 함께 파기된다. 별도 파기 로직 불필요.

### 2. OAuth 파싱

- `OAuthUserInfo` 에 `email: String?` 필드 추가.
- **Google**: `GoogleUserInfoResponse` 에 `email` 필드 추가 후 파싱. authorize scope 는 이미 `email profile` 로 요청 중.
- **Apple**: id_token JWT 의 `email` 클레임 파싱. authorize scope 는 이미 `email name` 으로 요청 중.
- **Kakao**: 이번엔 파싱하지 않는다. `OAuthUserInfo.email = null` 로 둔다. 구조만 수용하고, 비즈니스 앱 전환 + 동의항목 검수가 완료되면 파싱만 추가한다.

### 3. 저장·갱신 흐름 (매 로그인 upsert)

- **신규 가입**: `SocialAccountWriter.link()` 가 `UserDetail` 을 만들 때 email 을 포함해 저장한다.
- **기존 로그인**: 현재 흐름은 기존 유저면 `UserDetail` 을 갱신하지 않는다. 여기에 **email upsert 단계를 추가**해, 매 로그인마다 provider email 로 `user_details.email` 을 갱신한다.
  - 효과: 기존 가입자도 다음 로그인 때 자동 backfill 되고, provider 측 email 변경도 최신으로 반영된다.
- **null 처리**: email 이 `null` 로 오면(애플 2회차 등) **기존 값을 유지**한다. null 로 덮어쓰지 않는다.

### 4. 검증·예외

- **email 형식 재검증 안 함** — provider(구글·애플)가 인증한 값이라 우리가 다시 검증할 필요 없다. 받은 값을 그대로 저장한다.
- **PII 비노출** — email 은 개인정보라 로그·응답에 노출하지 않는다(CLAUDE.md 로깅 규약). `UserResponse` 등 기존 응답 DTO 에 email 을 추가하지 않는다(수집만, 노출 용도 없음).
- **로그인 흐름 무영향** — email 은 부가 정보라 못 받아도(null) 로그인을 실패시키지 않는다.

### 5. 테스트 (CLAUDE.md 규약)

- **단위**: `OAuthUserInfo`/`UserDetail` 의 email 매핑, null 처리(미제공 시 null, null 로 덮어쓰지 않기).
- **통합**: 소셜 로그인 시 `user_details.email` 저장(구글·애플 stub 응답에 email 포함 → DB 확인), 기존 유저 재로그인 시 backfill, null 입력 시 기존값 유지.
- 외부 OAuth 는 기존 `StubOAuthClient` 에 email 을 포함시켜 격리한다.

## 범위 밖 (명시적 제외)

- 카카오 email (비즈니스 앱 전환 + 동의항목 검수 후 별도 작업)
- 마케팅 수신 동의 필드/플로우 (메일 발송 기능과 함께)
- email 발송 (마케팅·알림·복구 메일)
- email 을 응답으로 노출 (당장 용도 없음)
- 기존 유저 일괄 backfill 배치 (매 로그인 자연 backfill 로 충분)
