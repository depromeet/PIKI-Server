package com.depromeet.piki.product.service.gemini

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.http.HttpPageFetcher
import io.micrometer.observation.ObservationRegistry
import org.jsoup.Jsoup
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull

/**
 * 실제 Gemini API 를 호출하는 생존성 테스트.
 *
 * 비용·외부 의존성이 있으므로 기본은 @Disabled. 호출 경로(인증·스키마·직렬화·모델)가 살아 있는지
 * 확인이 필요할 때만 명시적으로 enable. GEMINI_API_KEY 가 환경에 있다고 가정한다.
 *
 * fetch + Gemini 만 직접 조립해 호출한다 (구조화 우선 경로 우회) — Gemini 호출 자체의 생존성이 목적이라,
 * 페이지가 JSON-LD 를 줘서 LLM 이 스킵되면 검증 의미가 사라지기 때문.
 */
@Disabled("실제 Gemini API 호출. 검증 필요 시 수동으로 enable 후 실행.")
class GeminiHtmlExtractorTest {
    private val pageFetcher = HttpPageFetcher(ObservationRegistry.NOOP)
    private val objectMapper = jacksonObjectMapper()
    private val properties =
        GeminiProperties(
            apiKey = System.getenv("GEMINI_API_KEY"),
            model = "gemini-3-flash-preview",
        )
    private val httpClient = GeminiHttpClient(properties, objectMapper, ObservationRegistry.NOOP)
    private val extractor = GeminiHtmlExtractor(httpClient)

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    fun `Gemini end-to-end 호출이 살아 있고 응답을 구조화해 돌려준다`() {
        // 호출 경로(인증·스키마·직렬화·모델)가 살아 있는지 확인하는 생존성 테스트.
        // 어떤 종류의 실패도 회귀 신호로 간주해 fail 시킨다.
        val link = ProductLink.parse("https://www.apple.com/shop/buy-iphone")

        val page = pageFetcher.fetch(link)
        val product = extractor.extract(Jsoup.parse(page.html, link.value.toString()), link)

        assertNotNull(product.name, "Gemini 가 상품명을 추출했어야 한다")
    }
}
