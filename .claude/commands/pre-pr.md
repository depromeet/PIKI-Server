PR 올리기 전 최종 점검 — `origin/dev` 와 up-to-date 를 맞추고, 변경 영역에 맞는 테스트를 **CLAUDE.md 테스트 규약대로** 작성·실행하며, 컨벤션·하드코딩 self-audit + 응답 전수 문서화 + (전역 변경 시) blast-radius 까지 확인한 뒤 `/commit`·`/pr` 로 넘긴다. 한 군데라도 막히면 PR 을 만들지 않고 멈춘다.

## 핵심 전제

- **base 는 `origin/dev`.** 이 프로젝트 기본 브랜치는 `dev` 이고, 로컬 `dev` 는 stale 일 수 있어 항상 `origin/dev` 를 기준으로 diff·머지한다 (로컬 `dev` 로 비교하면 머지로 끌려온 남의 커밋까지 섞여 diff 가 부풀려진다).
- **테스트는 CLAUDE.md 규약을 그대로 따른다.** 단위(Spring 없음) + 통합(IntegrationTestSupport·Testcontainers MySQL) **두 종류만**. `@WebMvcTest`·`@MockBean`·`@SpyBean`·Mockito·`@DirtiesContext`·`@ActiveProfiles`·service 단독 테스트는 **금지**. 외부 호출 경계만 프로그래머블 stub 으로 격리한다.
- **구현이 끝난 뒤 호출한다.** TDD-first 가 아니라, 이미 구현된 변경에 대해 테스트 커버리지를 메우고 최종 점검하는 단계다.

## 절차

### 0단계: 작업 위치·base 확정

```bash
CURRENT_BRANCH=$(git branch --show-current)
git fetch origin dev -q
```

- cwd 가 워크트리(작업 브랜치) 안인지 확인한다. `$CURRENT_BRANCH` 가 `dev` 이면 base 브랜치에서 호출한 것 — 작업 워크트리에서 다시 부르라고 안내하고 멈춘다.
- 이후 모든 diff 의 base 는 `origin/dev`.

### 1단계: dev up-to-date 확인 (branch out-of-date 게이트 사전 해소)

머지 게이트("Require branches to be up to date")에 막히기 전에 미리 맞춘다.

```bash
BEHIND=$(git rev-list --count HEAD..origin/dev)
echo "dev 미반영 커밋: $BEHIND"
```

- `0` 이면 통과.
- `1` 이상이면 `git merge origin/dev` 한다.
  - **commit-msg 훅이 기본 머지 메시지를 거부**하므로 규격 메시지로 완료: `git merge origin/dev -m "chore: dev 최신 변경 머지 (branch up-to-date 게이트 충족)"`.
  - 충돌이 나면 사용자에게 보고하고 함께 해소한다 (자동 강제 머지 금지).
- 머지 후에는 5·6·7 단계의 테스트·점검을 머지된 상태로 다시 돌린다.

### 2단계: 변경 파일 분석 (origin/dev 기준)

```bash
git diff origin/dev...HEAD --name-only
git diff origin/dev...HEAD --stat
git diff origin/dev...HEAD          # 실제 내용
```

**우리 변경만** 본다. 머지로 끌려온 남의 커밋 파일은 대상이 아니다 (필요하면 `git log` 로 작성자를 확인해 가른다).

**테스트 작성 대상 판단:**

| 대상 O | 대상 X |
|---|---|
| `**/domain/**/*.kt` (entity·VO·도메인 메서드 — 분기/상태변화/계산) | `**/*Api.kt`, `**/*ApiExamples.kt` (OpenAPI 문서) |
| `**/controller/**/*.kt` (HTTP contract·예외→status·응답 모양) | `**/config/**/*.kt`, `**/*Application.kt` |
| `**/controller/dto/**/*.kt` (Bean Validation·매핑 함수) | `**/service/dto/**/*.kt`, `**/*Properties.kt` |
| `**/service/**/*.kt` (DB 상태+결정·외부호출 분기 → **통합** 으로) | `**/repository/**/*.kt` (interface) |
| `**/repository/**/*Impl.kt` (구현 로직 있을 때) | `.github/**`, `*.md`, `*.yml`, `build.gradle.kts`, 마이그레이션 SQL |

파일명만 보지 말고 **diff 내용**으로 판단한다. 도메인 이름은 diff 에서 동적으로 추출(하드코딩 금지).

대상이 없으면 → 바로 **6단계**(self-audit)로.

### 3단계: 테스트 작성 — CLAUDE.md 테스트 규약 준수

**분기 위치 결정 트리** (이 분기는 무엇을 검사하는가):

1. 입력 형식·값 범위·null·정규화 → 도메인 생성자/팩토리 → **단위 테스트**
2. 상태 변화·계산·정책 → 도메인 메서드 → **단위 테스트**
3. DTO ↔ 도메인 매핑·정규화 → 매퍼 함수(`from`/`toCommand`/`toXxx`) → **단위 테스트**
4. "DB 상태 + 결정"(예: 이미 존재하는가) → **통합 테스트**
5. HTTP contract(Bean Validation·예외→status·응답 모양) → **통합 테스트**
6. 외부 API 결과 분기(성공/실패/timeout) → 외부 호출 stub + **통합 테스트**

> 서비스에 분기가 쌓이면 도메인이 빈약(anemic)하다는 신호 — 분기를 도메인 메서드로 옮겨 단위 테스트로 내린다. **서비스 단독 테스트는 만들지 않는다.**

**단위 테스트**: 도메인과 같은 패키지(`src/test/.../domain/`), Spring·DB 의존 0, `@ParameterizedTest` 로 분기 망라.

**통합 테스트**: `IntegrationTestSupport` 상속(단일 컨텍스트 공유), Testcontainers MySQL, 외부 호출만 `IntegrationStubs` 의 프로그래머블 stub 으로 격리. 엔드포인트당 시나리오 3–5건, 응답 contract(`status`·`code`·`detail`·`data` 모양)를 단언에 포함. 검증실패(400)·비즈니스예외(409 등)도 contract 검증에 포함.

**금지 (위반 시 컨벤션 어긋남):**
- `@WebMvcTest`·`@MockBean`·`@SpyBean`·Mockito/MockK → 내부 컴포넌트는 실제 빈으로. 외부 경계만 stub.
- `@DirtiesContext`·`@ActiveProfiles`·클래스별 `@Import`/`@TestConfiguration`/`@TestPropertySource` → 컨텍스트 캐시를 깬다.
- `@BeforeEach`/`@BeforeAll`/`@Sql` 셋업 hook 으로 fixture·stub·MockMvc 미리 채우기 → 각 테스트 본문에서 직접 만든다. DB 격리는 클래스 레벨 `@Transactional` 자동 롤백.
- service 단독 테스트.

**스타일**: 한국어 backtick 네이밍(시나리오 한 문장), `kotlin.test`/AssertJ(한 메서드 안에서 섞지 않음), 외부 API 실호출 테스트는 `@EnabledIfEnvironmentVariable`. 동시성·시간 의존은 별도 분류(`*ConcurrencyIntegrationTest` 등, `@Transactional` 미사용).

### 4단계: 컨벤션·하드코딩 self-audit

변경된 `src/main` 파일에 대해 (리뷰어·CodeRabbit 이 잡기 전에) 훑는다:

- **`== null` / `!= null` 금지** → Elvis(`?:`) + early return/throw 로. (`requireNotNull`/`checkNotNull` 은 허용)
- **하드코딩 시크릿·redirect·client_id 금지** → 전부 env/Properties. provider 엔드포인트 등 환경 무관 고정값만 named `const val`.
- **매직 넘버·문자열** → named const / enum.
- **Logger**: `private val log = LoggerFactory.getLogger(javaClass)`, SLF4J `{}` placeholder, URL·토큰·입력 원본 마스킹, 레벨(info=정상·클라계약위반 / warn=외부실패·방어차단 / error=예상못한 버그+스택).
- **`@Transactional` 메서드 레벨**, 외부 호출(LLM·HTTP·결제)은 트랜잭션 밖, self-invocation 주의.
- **도메인 예외**: "멀쩡한 클라가 정상 요청으로 닿나" — 닿으면 custom 예외(`*Exception` private 생성자 + 정적 팩토리 + `HttpMappable`), 못 닿으면 `require`/`check`/`error`. 예외 message 에 내부정보·입력원본 노출 금지.
- **DTO ↔ 도메인 매핑은 DTO 자신에** (`from`/`toCommand`), 별도 Mapper 빈 금지.
- **응답은 `ApiResponseBody` 래퍼**, `ResponseEntity`/raw DTO 직접 노출 금지, **204 금지**(200 + data=null).

발견 시 고치고 3단계 테스트도 갱신한다.

### 5단계: 응답 전수 문서화 체크 (`*Api.kt` — 절대 규칙)

새 엔드포인트·시그니처 변경이 있으면, 해당 `*Api.kt` 의 `@ApiResponses` 와 `*ApiExamples` 가 **멀쩡한 클라가 정상 요청으로 받을 수 있는 모든 응답**을 빠짐없이 담는지 확인:

- 성공 2xx (컨트롤러 `@ResponseStatus` 와 일치) · 계약 실패 4xx(도메인 예외 `httpStatus`) · 외부 의존성 실패 5xx(예: `GeminiApiException`→502) · `SecurityConfig` 기반 401/403 · Bean Validation 400.
- 어노테이션과 example payload(`ApiResponseBody.ok/created/fail`) 를 **함께** 박는다 — 한쪽만 있으면 위반.
- 제외: `handleUnexpected` 가 잡는 일반 500(정상 요청으로 도달 불가).

> 깨진 body·잘못된 메서드·미디어타입 같은 **framework 4xx 는 "정상 요청" 이 아니므로 per-endpoint 문서화 대상이 아니다** (전역 `GlobalExceptionHandler` 동작).

### 6단계: 전역 컴포넌트 변경 시 blast-radius 노트

`GlobalExceptionHandler`·`SecurityConfig`·공통 필터·`ApiResponseBody` 등 **전역 공유 컴포넌트**를 바꿨다면, 영향이 내 엔드포인트 너머로 번진다. 영향받는 컨트롤러/엔드포인트를 추려 (`git log -1 -- <file>` 로 작성자 식별) **누구의 어떤 엔드포인트가 어떻게 바뀌는지** 를 PR 본문에 적도록 준비한다. "남의 파일은 안 건드렸지만 동작은 바뀐다" 를 명확히 — 해당 작성자 멘션/리뷰어 지정도 고려.

### 7단계: 빌드·테스트 실행 — Docker 가드 + `./gradlew test`

**`./gradlew build` 가 아니라 `./gradlew test`** 를 돌린다 — CI 가 `test` 만 돌리고(`build`/`check` 는 `ktlintCheck` 를 포함해 dev 의 남이 만든 사전 위반에 걸려 실패할 수 있다). 단위+통합을 함께 돌린다.

```bash
# 로컬 macOS 전용 Docker 가드 — Testcontainers 가 Docker 를 요구한다. 사전 검증 없이 돌리면 한 사이클 헛돈다.
docker info > /dev/null 2>&1 || (open -a Docker && until docker info > /dev/null 2>&1; do sleep 2; done)
./gradlew test
```

- **실패** → 실패 테스트·오류를 출력하고 **즉시 중단**. PR 만들지 않는다. (수정 후 `/pre-pr` 재실행 안내)
- **성공** → 아래 형식으로 결과를 출력:

```
## ✅ 테스트 결과
| 항목 | 결과 |
|---|---|
| 전체 | N passed |
| 신규 작성 | XxxTest, ... (없으면 "없음") |
```

> **(선택) ktlint 는 CI 가 강제하지 않는다.** 적용하려면 `./gradlew ktlintFormat` 후 **우리 변경 파일만 유지**하고 — `ktlintFormat` 은 dev 의 남의 파일까지 포매팅하므로 — 우리 changeset(`git diff origin/dev HEAD --name-only`) 밖의 파일은 `git checkout` 으로 revert 한다.

### 8단계: 테스트 커밋

테스트를 새로 썼으면 `/commit` 으로 커밋한다 (타입 `test:`, 테스트 파일만). 본 구현이 아직 미커밋이면 그 커밋도 `/commit` 규칙에 따라 분리 커밋.

### 9단계: PR 생성

`/pr` 을 호출한다. `/pr` 은 대화 컨텍스트로 본문을 쓰므로, 위 테스트 결과·self-audit·blast-radius 노트가 자연스럽게 PR 본문에 반영된다.

## 한 줄 요약

`origin/dev` 기준 + up-to-date → CLAUDE.md 규약 테스트(단위/통합, Mockito·@WebMvcTest 금지) → 컨벤션·하드코딩 self-audit → 응답 전수 문서화 → (전역이면) blast-radius → Docker 가드 + `./gradlew test`(build 아님) → `/commit` → `/pr`.

$ARGUMENTS
