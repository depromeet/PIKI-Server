# 소셜 로그인 이메일 수집 - 카카오 추가

- 이슈: #451
- 작성일: 2026-06-08
- 선행: #442 (구글·애플 email 수집 재도입, `2026-06-07-email-collection-design.md`)

## 배경

#442 는 구글·애플 email 수집을 재도입하면서, 카카오는 **비즈니스 앱 전환 + 이메일 동의항목 검수**(영업일 3~5일)가 필요해 범위에서 제외했다. 저장·노출 인프라(`user_details.email`, 매 로그인 upsert, `GET /me` 노출)는 provider-agnostic 하게 구축해 카카오를 나중에 수용할 수 있게 두었다.

카카오 비즈 앱 + 이메일 동의항목 검수가 **승인 완료**되어, 이제 카카오도 email 을 수집한다. 본 작업은 **카카오 파싱만** 추가한다 — 저장·갱신·노출은 #442 인프라를 그대로 탄다.

## 설계

### OAuth 파싱 (이 작업의 전부)

- `KakaoUserInfoResponse.KakaoAccount` 에 `email` · `is_email_valid` · `is_email_verified` 추가 (`/v2/user/me` 응답 필드).
- Google 패턴대로 `KakaoUserInfoResponse.toOAuthUserInfo()` 매퍼를 추출해 HTTP 호출과 분리 (CLAUDE.md: 외부 결과 객체의 `toXxx()`).
- **수집 게이팅**: `is_email_valid && is_email_verified` 둘 다 true 이고 email 이 빈 값이 아닐 때만 채운다. 그 외(미동의로 부재 · 미인증 · 휴면 주소 · 빈값)는 null.
  - 마케팅·알림·복구용이라 미인증·휴면 주소는 신뢰할 수 없어 수집하지 않는다.
- `KakaoOAuthClient` 의 v1(code)·v2(accessToken) 양쪽 모두 `fetchUserInfoByAccessToken → toOAuthUserInfo()` 로 수렴해 email 을 파싱한다 — 구글·애플과 동일한 패리티.

### 저장·갱신·노출 (변경 없음, #442 인프라 재사용)

- `SocialAccountWriter.link()` / `updateEmail()` 가 `OAuthUserInfo.email` 을 provider 구분 없이 그대로 `user_details` 에 저장·upsert. 카카오 email 도 동일 경로.
- null(미동의·미인증 등)이면 기존 값 유지(덮어쓰지 않음) — 애플 2회차 미제공과 동일.
- `GET /me`(`MyProfileResponse`) 가 nullable email 을 내려주는 것도 그대로.

## 테스트 (CLAUDE.md 규약)

- **단위**: `KakaoUserInfoResponseTest` — email 정규화 망라(유효·인증 / 미동의 부재 / 미인증 / 휴면(`is_email_valid=false`) / 빈값 → null, 프사 빈값 → null).
- **역직렬화 경계 테스트 통일 (3 provider)**: 단위 테스트가 객체를 직접 만들면 파싱 경로(키 매핑)가 우회돼, 키가 틀어지면 운영에서만 조용히 null 이 되는 사각이 생긴다. 이를 막기 위해 각 provider 의 raw 응답 → `OAuthUserInfo` 경계를 실제 역직렬화 경로로 고정한다 — 동일 코드가 아니라 동일 원칙.
  - **Kakao**: `/v2/user/me` JSON 역직렬화 테스트 추가(`@JsonProperty` snake_case 리매핑 위험이 가장 큼). DTO 에 `@JsonIgnoreProperties(ignoreUnknown=true)` 명시해 mapper 설정 독립.
  - **Google**: 동일하게 userinfo JSON 역직렬화 테스트 + `@JsonIgnoreProperties` 추가해 Kakao 와 수준 통일(#442 에서 누락분 보강).
  - **Apple**: 구조가 JWT 라 이미 `signAppleIdToken` round-trip 으로 email claim(동의/2회차/빈값)을 검증 중 — 동등 수준이라 변경 없음.
- **통합**: 별도 추가 안 함. 저장·upsert·`GET /me` 경로는 provider-agnostic 이라 #442 의 통합 테스트가 이미 커버하며, stub 은 `OAuthUserInfo.email` 을 직접 주입해 파싱을 우회하므로 카카오 전용 통합 테스트는 중복이다.

## 범위 밖 (#442 와 동일하게 유지)

- 마케팅 수신 동의 필드/플로우, 실제 email 발송
- 로그인·게스트 생성 등 공유 응답에 email 노출 (`GET /me` 한 곳만 노출 유지)
