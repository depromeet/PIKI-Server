<!--
본 문서는 프로젝트 전반의 코딩·테스트 컨벤션을 한 파일에 모아둔다.
파일이 길어지면 `conventions/testing.md` 등으로 분리될 수 있다. 분리 시 본 파일에서는
링크로 대체한다.
-->

# 프로젝트 컨벤션

## Null 처리 원칙

**`== null` / `!= null` 분기를 제거한다.** 모든 nullable 처리는 Elvis(`?:`) + early return / throw / default 로 표현한다.

### 규칙
- **`== null` / `!= null` 사용 금지** — Elvis(`?:`)로 대체한다.
- **`requireNotNull` / `checkNotNull` 은 허용한다.** "non-null이어야 한다"는 의도가 시그니처에 명확히 드러나는 Kotlin 표준 idiom이며, 금지 대상은 어디까지나 `== null` / `!= null` 분기 패턴 한정이다.
- **Elvis + early return 패턴을 기본으로 한다.**
  ```kotlin
  // 금지
  if (value == null) return Default
  val x = if (value == null) throw E() else value

  // 권장
  value ?: return Default
  val x = value ?: throw E()
  ```
- 복합 조건이 필요해 보이면 함수를 분해해 **guard clause 여러 줄**로 푼다.
  ```kotlin
  fun toField(value: T?, box: Box?): Field<T> {
      value ?: return Field.NotFound
      box ?: return Field.Inferred(value)
      return Field.Extracted(value, box.toBoundingBox())
  }
  ```
- `sealed class` / `sealed interface` 분기는 `when` + `is` 를 사용한다. (null 체크와는 무관)

### 예외
- 외부 라이브러리 시그니처가 강제하는 경우 (예: `Optional.isPresent()` 같은 Java interop)
- 이 경우에도 **주석으로 이유를 명시**한다.

## 테스트 분류

테스트는 두 종류만 둔다.

- **단위 테스트** — Spring · DB 없이 도메인 객체와 순수 함수만 검증. 컨텍스트가 뜨지 않으므로 가장 빠르다. 입력 형식 / 정규화 / 상태 변화 / 계산 등 분기 폭이 큰 영역은 모두 여기서 망라한다.
- **통합 테스트** — 컨트롤러(HTTP 진입)부터 DB(Testcontainers MySQL)까지 실제 흐름을 검증. 외부 호출(Gemini API, HTTP fetch 등)만 stub 빈으로 격리.

서비스 단독 테스트는 두지 않는다. 분기는 도메인으로 옮겨 단위 테스트로 내리고, 시나리오·contract 검증은 통합 테스트로 흡수한다.

### 예외적 분류
시간 · 동시성 · 트랜잭션 경계 검증처럼 컨트롤러 진입으로 표현이 어려운 경우, 별도 분류를 명시적으로 만든 뒤 추가한다. 기본 분류에 욱여넣지 않는다.

### 분기 위치 결정 트리

```text
이 분기는 무엇을 검사하는가?

(1) 입력 형식 · 값 범위 · null · 정규화
    → 도메인 객체 생성자 / 팩토리 안 → 단위 테스트
(2) 상태 변화 · 계산 · 정책
    → 도메인 객체 메서드 안 → 단위 테스트
(3) DTO ↔ 도메인 매핑 · 정규화
    → 매퍼 함수 → 단위 테스트
(4) "DB 상태 + 결정" (예: "이미 존재하는가?")
    → 통합 테스트
(5) HTTP contract (Bean Validation · 예외→상태 매핑 · 응답 모양)
    → 통합 테스트
(6) 외부 API 호출 결과 분기 (성공 / 실패 / timeout)
    → 외부 호출 stub + 통합 테스트
```

서비스에 분기가 쌓이면 보통 **도메인 모델이 빈약(anemic)** 하다는 신호다. 분기를 도메인 객체 메서드로 옮긴 뒤 단위 테스트로 검증한다.

### 단위 테스트
- 위치: 도메인 객체와 같은 패키지 (예: `src/test/.../product/domain/ProductLinkTest.kt`).
- Spring · DB 의존성 없이 작성. 부팅 비용 0.
- 분기 망라가 목적. `@ParameterizedTest` 적극 활용 권장.

### 통합 테스트 작성 원칙
- 엔드포인트 당 시나리오·contract 검증을 3~5건 수준으로 유지한다. 분기 망라 목적의 추가는 도메인 단위로 내린다.
- 응답 contract(`status`, `code`, `detail`, `data` 필드 모양)를 단언에 포함한다. 도메인 객체 단언만으로는 직렬화·예외 매핑 회귀를 잡지 못한다.
- 검증 실패(400) · 비즈니스 예외(409 등) 케이스도 contract 검증에 포함한다.
- DB 는 Testcontainers MySQL 한정 (H2 등 다른 DB 로 대체 금지).

## 모킹 / Stub 정책

**내부 컴포넌트는 모킹·stub 모두 금지하고 실제 빈으로 통합 테스트한다.** 외부 호출 경계(외부 HTTP API · 메시징 · 결제 등 우리 애플리케이션 바깥의 의존성)는 격리해야 하며, 격리 도구로 **프로그래머블 stub 을 기본**으로 한다.

### 규칙
- 외부 호출 경계는 **프로그래머블 stub**(`@TestConfiguration` + 빈 교체) 으로 격리한다. 인터페이스(`ProductExtractor`)에 stub 구현(`StubProductExtractor`)을 만들어 빈으로 등록하고, stub 의 응답을 람다로 받아 매 테스트가 시나리오별로 교체한다.
- **stub 의 default 람다는 throw 로 둔다.** 명시 세팅을 빠뜨리면 호출 시점에 즉시 `IllegalStateException` 으로 깨져 "이전 테스트의 build 가 살아남는" 함정을 차단한다. stub 을 호출하지 않는 테스트는 영향 없다.
  ```kotlin
  class StubProductExtractor : ProductExtractor {
      // default 가 의도된 시나리오인 양 동작 가능한 값을 두면 명시 세팅을 빠뜨려도 통과해버린다.
      // 명시 세팅을 강제하기 위해 default 자체를 throw 로 둔다.
      var build: (ProductLink) -> Product = {
          error("stub.build 를 테스트 본문에서 명시 세팅해야 한다.")
      }
      override fun extract(link: ProductLink): Product = build(link)
  }

  // 테스트 본문
  stubExtractor.build = { link -> Product(link, name = "나이키", price = 99_000) }
  ```
- `@Mock` / `@MockBean` / `@SpyBean` 사용은 다음 두 조건을 **모두** 만족할 때만 허용한다.
  1. 인터페이스 메서드 수가 많아 stub 구현 부담이 크거나, 호출 횟수·순서 검증이 시나리오의 본질인 경우
  2. `IntegrationStubs` 에 한 번 등록해 모든 통합 테스트가 같은 mock 인스턴스를 공유함으로써 컨텍스트 캐싱이 깨지지 않을 때
- 클래스별 `@MockBean` 으로 컨텍스트 캐시를 깨는 패턴은 금지한다.
- 컨트롤러 테스트도 서비스를 모킹·stub 하지 않는다. `IntegrationTestSupport` + MockMvc 로 실제 흐름을 검증한다.

### Stub 이 mock 보다 우월한 이유
- **응답 고정의 함정 회피** — 람다 교체로 시나리오별 동적 응답이 가능해 `when().thenReturn()` 과 표현력이 사실상 동등하다.
- **시그니처 변경 안전** — stub 은 인터페이스 구현이라 컴파일 타임에 깨짐을 잡는다. mock 은 reflection 기반이라 메서드 이름 오타·시그니처 변경이 런타임 실패로만 드러난다.
- **컨텍스트 캐시 보존** — stub 은 `IntegrationStubs` 한 곳에 등록되어 모든 통합 테스트가 같은 인스턴스를 공유한다. `@MockBean` 은 빈 그래프를 변경시켜 캐시를 깬다.
- **디버깅 용이** — stub 은 실제 인스턴스라 디버거에서 내부가 보이고 IDE 의 reference 탐색이 동작한다.
- 호출 검증이 필요하면 stub 에 카운터 필드를 추가한다 (`val invocations = mutableListOf<...>()`). `verify` 와 동등.

### 이유 (왜 내부는 금지인가)
- 모킹·stub 은 본질적으로 "이 호출이 이렇게 동작한다"는 가정 코드다. 가정과 실제의 어긋남이 통합 시점에 드러나면 가장 비싸다.
- 내부 컴포넌트를 모킹·stub 하면 리팩터링이 깨져도 테스트는 초록불을 유지하는 사고가 잦다.
- 외부 호출만 격리하고 그 외는 실제로 돌리는 것이 회귀 검출력의 균형점이다.

## 스프링 컨텍스트 캐싱

**모든 통합 테스트는 `IntegrationTestSupport` 단일 컨텍스트를 공유한다.** 컨텍스트가 한 번만 부팅되고 캐시 재사용되어 빌드 시간이 크게 줄어든다.

### 규칙
- 클래스별 `@Import(...)` / `@TestConfiguration` / `@MockBean` 으로 컨텍스트 변형 금지. Spring 은 빈 그래프가 다르면 새 컨텍스트를 부팅한다.
- 외부 호출 stub 빈은 `support/IntegrationStubs` 한 곳에 등록한다. `IntegrationTestSupport` 가 이를 import 하므로 모든 통합 테스트가 같은 인스턴스를 공유한다.
- `@DirtiesContext` 금지. 캐시를 폭파해 다음 테스트가 컨텍스트 재부팅 비용을 부담한다.
- `@ActiveProfiles` 분기 금지. 다른 프로파일은 다른 컨텍스트로 캐싱된다.
- `@SpringBootTest(webEnvironment = ...)` 옵션 분기 금지. 옵션이 다르면 별개 컨텍스트.
- `@TestPropertySource` 로 클래스별 프로퍼티 변형 금지. 같은 이유.

### Stub 빈의 mutable 상태 다루기
같은 컨텍스트를 공유하면 `var` 필드를 가진 stub 빈이 클래스 간에 누수될 수 있다.
- 매 테스트가 stub 의 mutable state(`build` 람다 등)를 본문에서 명시적으로 세팅한다.
- 클래스 간 격리는 "다음 테스트가 자기 시나리오를 명시적으로 다시 세팅"으로 자연스럽게 보장된다.
- 다른 테스트의 stub state 에 의존하는 코드는 작성 금지.

## 테스트 셋업 원칙

**`@BeforeEach` / `@BeforeAll` / `@Sql` 등 셋업 hook 으로 fixture 데이터·mock 상태·인프라 객체를 미리 채우지 않는다.** 각 테스트 메서드가 자기 시나리오에 필요한 데이터·stub·MockMvc 등을 본문에서 직접 만든다.

### 규칙
- DB 격리는 클래스 레벨 `@Transactional` 의 자동 롤백으로 해결한다. `@BeforeEach` 에서 `deleteAll()` 류로 정리하지 않는다.
- 공유 mutable 상태(Spring bean 으로 주입된 stub 의 람다 등)는 매 테스트에서 명시적으로 세팅한다. 셋업 hook 에서 default 로 되돌리는 구조 금지.
- MockMvc / RestAssured 포트 / 테스트용 client 같은 인프라 객체도 셋업 hook 대신 각 테스트 본문에서 만든다.

### 이유
- 테스트 메서드 하나만 봐도 시나리오를 완전히 이해할 수 있어야 한다.
- 셋업 hook 은 한 테스트의 변경이 다른 테스트로 누수되는 함정을 만든다.
- "암묵적 사전 상태"를 없애 회귀 추적·리뷰 비용을 낮춘다.

## 테스트 메서드 작성 규약

### 네이밍
- 한국어 backtick 네이밍을 기본으로 한다. 시나리오를 한 문장으로 표현.
  ```kotlin
  @Test
  fun `같은 guest 가 같은 URL 을 두 번 등록하면 409 CONFLICT 가 반환된다`() { ... }
  ```

### 단언
- `kotlin.test` 와 AssertJ 를 자유롭게 사용한다. 둘 다 `spring-boot-starter-test` 에 포함되어 있어 추가 의존성이 없다.
- 단순 단언(`assertEquals`, `assertNotNull`, `assertFailsWith`)은 `kotlin.test` 가 짧다. 컬렉션 비교 / 객체 그래프 깊은 비교 / soft assertions 처럼 표현력 차이가 큰 경우 AssertJ(`assertThat(...)`) 가 우월하다. 상황에 맞춰 고른다.
- 한 테스트 메서드 안에서는 두 스타일을 섞지 않는다 (가독성 일관성).
- Kotest · Strikt 같은 Kotlin 전용 테스트 라이브러리는 별도 의존성이라 이후 도입 예정. 현재는 사용 금지.

### 환경 의존 테스트
- 실제 외부 API 호출이 들어가는 테스트(예: Gemini)는 `@EnabledIfEnvironmentVariable(named = "...", matches = ".+")` 로 환경 의존을 격리한다.
- `@Disabled` 는 환경과 무관하게 영구 비활성화 의미. 사용 시 이유를 어노테이션 인자에 명시한다.

### 동시성 · 시간 의존 통합 테스트

race · 동시성 · timeout 처럼 일반 통합 테스트 패턴(`@Transactional` 자동 롤백 + 단일 스레드)으로 표현이 어려운 시나리오는 **별도 분류**로 명시적으로 작성한다. "검증이 어려우니 생략한다" 로 귀결시키지 않는다.

#### 규약
- 클래스명·패키지로 명확히 구분: `*ConcurrencyIntegrationTest` / `*TimingIntegrationTest`.
- `IntegrationTestSupport` 를 그대로 상속해 컨텍스트 캐시를 보존한다. 단 `@Transactional` 은 사용하지 않는다 — 별도 트랜잭션 동시 진행이 시뮬레이션의 본질이기 때문.
- 데이터 격리는 자기가 만든 행을 메서드 끝에서 명시적으로 정리하거나, 매 테스트가 격리된 식별자(예: 새 `UUID guestId`)를 사용한다.
- 동시 호출 시뮬레이션은 `ExecutorService` + `CountDownLatch` 로 동시 출발을 강제한다. `Thread.sleep()` 으로 타이밍을 맞추지 않는다.
- timeout 검증은 JUnit `@Timeout` 또는 Awaitility 를 사용한다. wall clock 비교(`System.currentTimeMillis()` 차이) 금지.

## 컨트롤러 / OpenAPI 문서

**컨트롤러는 `*Api` 인터페이스를 구현한다.** OpenAPI 어노테이션은 인터페이스, 매핑/검증 어노테이션은 구현체로 분리한다. example 은 평문 JSON 으로 박지 않고 `*ApiExamples` 의 `OperationCustomizer` 빈으로 객체화한다.

### 규칙
- **인터페이스 (`*Api.kt`)**: `@Tag`, `@Operation`, `@ApiResponse(s)`, `@Schema` 만 둔다. 메서드 시그니처는 평범한 함수 (`@PostMapping` 등 매핑 어노테이션 / `@RequestBody` 등 파라미터 어노테이션 / `@Valid` / `@ResponseStatus` 모두 두지 않는다).
- **구현체 (`*Controller.kt`)**: `@RestController`, `@RequestMapping`, 메서드별 `@PostMapping` / `@GetMapping`, 파라미터 어노테이션, `@ResponseStatus` 를 둔다. 라우팅이 컨트롤러만 보면 한눈에 드러나야 한다.
- **example (`*ApiExamples.kt`)**: `@Configuration` + `OperationCustomizer` 빈. 핸들러 매칭은 `HandlerMethod.binds(Controller::method)` (method reference), 응답 코드는 `HttpStatus` enum. path / status 매직 스트링 금지.
- example payload 는 실제 DTO 인스턴스를 만들어 `ApiResponseBody.ok/created/fail` 로 감싸 넘긴다. `@ExampleObject(value = "...JSON 평문...")` 형태 금지 — DTO 시그니처 변경이 컴파일로 추적되지 않는다.
- 새 엔드포인트 추가 / 시그니처 변경 시 인터페이스 + example 빈을 함께 갱신한다. 한쪽만 바꾸면 OpenAPI 문서가 실제 응답과 어긋난다.

### 응답 포맷
**모든 응답은 `ApiResponseBody` 래퍼로 감싼다.** 컨트롤러 메서드는 항상 `ApiResponseBody<T>` 를 반환하고, 직접 `ResponseEntity` / raw DTO 를 노출하지 않는다.

- 성공 응답은 `ApiResponseBody.ok(...)` / `ApiResponseBody.created(...)`. 실패 응답은 `GlobalExceptionHandler` 가 `ApiResponseBody.fail(...)` 로 매핑한다.
- **HTTP 204 No Content 는 사용하지 않는다.** 래퍼가 항상 body 를 만들기 때문에 RFC 7231 상 "body 없음" 이 본질인 204 와 충돌한다. 내릴 데이터가 없는 응답은 200 OK + `ApiResponseBody.ok()` (data=null) 로 표현한다.
- 비기본 status (`201 CREATED` 등) 는 컨트롤러 메서드에 `@ResponseStatus` 를 명시한다. body 의 `status` 필드와 HTTP 상태 코드를 항상 일치시킨다.

### 이유
- 라우팅(컨트롤러)과 contract(인터페이스)의 관심사를 분리해 컨트롤러가 REST 본질에 집중하게 만든다.
- example 객체화로 DTO 변경이 휴먼 에러로 example 만 어긋나는 함정을 차단한다.
- 일관된 응답 래퍼는 클라이언트 파싱 코드를 단순화하고 status / detail / code 를 한 자리에서 추적 가능하게 한다.

### 향후 개선 여지
`*ApiExamples` 의 평문 문자열(에러 `detail`, example `name` 등) 중 본질이 도메인의 비즈니스 메시지와 같은 항목은 **contract 공유 리팩터**의 후보다. 도메인 예외(`WishException` 등)에 정적 prefix 상수를 두고 example / 실제 응답이 같은 상수를 참조하면 회귀가 컴파일러로 잡힌다. 단 응답 detail 의 보안·노이즈, 디버깅 컨텍스트 보존 같은 트레이드오프가 있어 일괄 적용 대신 케이스별로 판단한다. 더 좋은 패턴이 보이면 별도 PR 로 제안한다.
