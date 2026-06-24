package com.depromeet.piki.support

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * 테스트 컨벤션을 기계로 강제하는 메타 테스트.
 *
 * CLAUDE.md `## 테스트 분류` ~ `## 테스트 메서드 작성 규약` 의 불변식 중
 * **오탐 없이 기계로 PASS/FAIL 을 가를 수 있는 것만** 여기서 강제한다.
 * 서비스 단독 테스트 여부·한국어 네이밍 적정성처럼 사람·모델 판단이 끼는 규칙은 산문(CLAUDE.md)에 둔다.
 *
 * src/test 의 Kotlin 소스를 **import 라인 기준**으로 스캔한다. import 기준이라
 * 이 파일이 문자열 리터럴로 들고 있는 금지 키워드 자체는 오탐하지 않는다.
 * Spring 컨텍스트를 띄우지 않으므로 Docker 없이 단독 실행 가능하다
 * (`./gradlew test --tests "com.depromeet.piki.support.TestConventionTest"`).
 */
class TestConventionTest {
    private val testSources: List<TestSource> by lazy {
        val root = File("src/test/kotlin")
        check(root.isDirectory) { "테스트 소스 루트를 찾지 못했다: ${root.absolutePath}" }
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { TestSource(it, it.readLines()) }
            .toList()
    }

    private data class TestSource(val file: File, val lines: List<String>) {
        val name: String = file.name
        val imports: List<String> =
            lines.mapNotNull { line ->
                line.trim().takeIf { it.startsWith("import ") }?.removePrefix("import ")?.trim()
            }

        fun hasImportUnder(prefix: String): Boolean = imports.any { it == prefix || it.startsWith("$prefix.") }

        fun hasAnyImportUnder(prefixes: Collection<String>): Boolean = prefixes.any { hasImportUnder(it) }
    }

    @Test
    fun `모킹 라이브러리와 컨텍스트 파괴 어노테이션은 테스트에서 import 되지 않는다`() {
        val forbidden =
            mapOf(
                "io.mockk" to "mockk — 내부 모킹 금지. 외부 경계는 IntegrationStubs 의 프로그래머블 stub 으로 격리한다.",
                "com.ninjasquad.springmockk" to "springmockk(@MockkBean/@SpykBean) — 내부 모킹 금지.",
                "org.mockito" to "Mockito — 내부 모킹 금지.",
                "org.springframework.boot.test.mock.mockito" to "@MockBean/@SpyBean — 빈 그래프를 바꿔 컨텍스트 캐시를 깬다.",
                "org.springframework.test.context.bean.override" to "@MockitoBean/@TestBean — 컨텍스트 캐시를 깬다.",
                "org.springframework.test.annotation.DirtiesContext" to "@DirtiesContext — 컨텍스트 캐시를 폭파한다.",
                "org.springframework.test.context.ActiveProfiles" to "@ActiveProfiles — 다른 프로파일은 별도 컨텍스트로 캐싱된다.",
                "org.springframework.test.context.TestPropertySource" to "@TestPropertySource — 클래스별 프로퍼티 변형은 별도 컨텍스트다.",
            )
        val violations =
            testSources.flatMap { src ->
                forbidden
                    .filterKeys { src.hasImportUnder(it) }
                    .map { (prefix, why) -> "${src.name}: import $prefix ($why)" }
            }
        if (violations.isNotEmpty()) {
            fail("테스트 컨벤션 위반 — 금지 import:\n" + violations.joinToString("\n"))
        }
    }

    @Test
    fun `@SpringBootTest 는 IntegrationTestSupport 한 곳에서만 선언된다`() {
        val springBootTest = "org.springframework.boot.test.context.SpringBootTest"
        val offenders =
            testSources
                .filter { it.hasImportUnder(springBootTest) }
                .map { it.name }
                .filter { it != "IntegrationTestSupport.kt" }
        if (offenders.isNotEmpty()) {
            fail(
                "@SpringBootTest 는 IntegrationTestSupport 에만 두고 통합 테스트는 이를 상속해야 한다 (단일 컨텍스트 공유). 위반: " +
                    offenders.joinToString(", "),
            )
        }
    }

    @Test
    fun `통합 테스트는 IntegrationTestSupport 를 상속한다`() {
        val offenders =
            testSources
                .filter { it.name.endsWith("IntegrationTest.kt") }
                .filter { src -> src.lines.none { it.contains(": IntegrationTestSupport(") } }
                .map { it.name }
        if (offenders.isNotEmpty()) {
            fail(
                "*IntegrationTest 는 IntegrationTestSupport 를 상속해야 컨텍스트 캐시를 공유한다. 위반: " +
                    offenders.joinToString(", "),
            )
        }
    }

    @Test
    fun `테스트는 @BeforeEach @BeforeAll 셋업 hook 을 쓰지 않는다`() {
        val setupHooks =
            listOf(
                "org.junit.jupiter.api.BeforeEach",
                "org.junit.jupiter.api.BeforeAll",
            )
        val offenders =
            testSources
                .filter { it.hasAnyImportUnder(setupHooks) }
                .map { it.name }
        if (offenders.isNotEmpty()) {
            fail(
                "셋업 hook(@BeforeEach/@BeforeAll) 금지 — 데이터·stub·MockMvc 는 각 테스트 본문에서 직접 만든다. 위반: " +
                    offenders.joinToString(", "),
            )
        }
    }

    @Test
    fun `E2E 테스트는 @Disabled 또는 @EnabledIf 어노테이션으로 CI 에서 격리한다`() {
        // 무방비 E2E 는 CI 에서 실제 외부 호출(외부 쇼핑몰·Gemini)이 돌아 flaky·비용·차단을 유발한다.
        // import 존재가 아니라 클래스에 어노테이션이 실제로 붙었는지 본다 — import 만 하고 안 달면 무방비다.
        val isolationAnnotations = listOf("@Disabled", "@EnabledIfEnvironmentVariable")
        val offenders =
            testSources
                .filter { it.name.endsWith("E2ETest.kt") }
                .filter { src ->
                    src.lines.none { line ->
                        val trimmed = line.trim()
                        isolationAnnotations.any { trimmed.startsWith(it) }
                    }
                }
                .map { it.name }
        if (offenders.isNotEmpty()) {
            fail(
                "*E2ETest 는 클래스에 @Disabled(\"이유\") 또는 @EnabledIfEnvironmentVariable 를 실제로 달아 CI 기본 실행에서 격리해야 한다 (import 만으로는 부족). 무방비 위반: " +
                    offenders.joinToString(", "),
            )
        }
    }
}
