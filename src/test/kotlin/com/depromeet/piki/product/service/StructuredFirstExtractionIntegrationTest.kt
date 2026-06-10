package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.gemini.GeminiApiException
import com.depromeet.piki.product.service.gemini.GeminiExtractionRequest
import com.depromeet.piki.product.service.gemini.GeminiExtractionResult
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubGeminiClient
import com.depromeet.piki.support.StubPageFetcher
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 구조화 우선 / LLM fallback 분기를 실제 빈으로 검증한다. 오케스트레이터(DefaultProductLinkExtractor)와
// 구조화 파서는 실제 빈, 외부 경계(PageFetcher·GeminiClient)만 stub 으로 격리한다.
// "구조화 성공 시 LLM 미호출"은 Gemini stub 의 호출 카운터로 단언한다.
// (컨트롤러~DB 전체 비동기 흐름은 WishlistRegisterAsyncIntegrationTest 가 보장하므로 여기선 추출 분기만 본다.)
class StructuredFirstExtractionIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var extractor: DefaultProductLinkExtractor

    @Autowired
    private lateinit var stubPageFetcher: StubPageFetcher

    @Autowired
    private lateinit var stubGeminiClient: StubGeminiClient

    private val link = ProductLink.parse("https://shop.example.com/products/42")

    @Test
    fun `구조화 데이터로 name 과 price 를 뽑으면 LLM 을 호출하지 않는다`() {
        stubGeminiClient.reset()
        stubGeminiClient.build = { error("구조화 성공 경로에서는 LLM 이 호출되면 안 된다") }
        stubPageFetcher.build = { PageContent(it, productJsonLdHtml(name = "나이키 에어포스", price = "99000")) }

        val snapshot = extractor.extract(link)

        assertEquals("나이키 에어포스", snapshot.name)
        assertEquals(99_000, snapshot.currentPrice)
        assertEquals(0, stubGeminiClient.invocations)
    }

    @Test
    fun `구조화 데이터가 없으면 LLM fallback 으로 추출한다`() {
        stubGeminiClient.reset()
        stubGeminiClient.build = { GeminiExtractionResult(isProductPage = true, name = "엘엘엠상품", currentPrice = 50_000) }
        stubPageFetcher.build = { PageContent(it, "<html><body>구조화 데이터 없음</body></html>") }

        val snapshot = extractor.extract(link)

        assertEquals("엘엘엠상품", snapshot.name)
        assertEquals(50_000, snapshot.currentPrice)
        assertEquals(1, stubGeminiClient.invocations)
    }

    @Test
    fun `구조화 데이터의 필수 필드가 미달이면 LLM fallback 으로 간다`() {
        stubGeminiClient.reset()
        stubGeminiClient.build = { GeminiExtractionResult(isProductPage = true, name = "보충상품", currentPrice = 30_000) }
        // JSON-LD 에 price 만 있고 name 이 없어 구조화 파서가 null → fallback.
        stubPageFetcher.build = { PageContent(it, priceOnlyJsonLdHtml(price = "30000")) }

        val snapshot = extractor.extract(link)

        assertEquals("보충상품", snapshot.name)
        assertEquals(1, stubGeminiClient.invocations)
    }

    @Test
    fun `구조화와 LLM 둘 다 실패하면 예외를 전파한다`() {
        stubGeminiClient.reset()
        stubGeminiClient.build = { throw GeminiApiException.upstreamError(RuntimeException("gemini down")) }
        stubPageFetcher.build = { PageContent(it, "<html><body>구조화 없음</body></html>") }

        assertFailsWith<GeminiApiException> { extractor.extract(link) }
        assertEquals(1, stubGeminiClient.invocations)
    }

    @Test
    fun `fallback 시 LLM 입력에서 ld+json 은 보존되고 일반 script·style·주석은 제거된다`() {
        stubGeminiClient.reset()
        stubGeminiClient.build = { GeminiExtractionResult(isProductPage = true, name = "엘엘엠상품", currentPrice = 1_000) }
        // ld+json 은 name 만 있어 구조화 미달 → fallback. 일반 script·style·주석은 빠지고 ld+json 은 남아야 한다.
        val html =
            """
            <html><head>
            <script type="application/ld+json">{"@type":"Product","name":"보존JSONLD"}</script>
            <script>var leak = "제거대상스크립트";</script>
            <style>.x{color:red}</style>
            <!-- 제거대상주석 -->
            </head><body></body></html>
            """.trimIndent()
        stubPageFetcher.build = { PageContent(it, html) }

        extractor.extract(link)

        val sentHtml = llmInputHtmlOf(stubGeminiClient.lastRequest)
        assertTrue(sentHtml.contains("보존JSONLD"), "ld+json 은 LLM 입력에 보존돼야 한다")
        assertFalse(sentHtml.contains("제거대상스크립트"), "일반 script 는 제거돼야 한다")
        assertFalse(sentHtml.contains("color:red"), "style 은 제거돼야 한다")
        assertFalse(sentHtml.contains("제거대상주석"), "주석은 제거돼야 한다")
    }

    @Test
    fun `구조화 데이터가 과거 절단선(200KB) 뒤쪽에 있어도 추출하고 LLM 을 호출하지 않는다`() {
        stubGeminiClient.reset()
        stubGeminiClient.build = { error("절단선 밖 JSON-LD 도 구조화로 잡혀 LLM 이 호출되면 안 된다") }
        // 과거엔 fetch 가 앞 200KB 만 남겨 뒤쪽 JSON-LD 를 놓쳤다(#476). 절단을 fetch 가 아니라 Gemini 입력 직전으로
        // 옮겼으므로, 240KB 필러 뒤에 있는 JSON-LD 도 구조화가 잡아 LLM 없이 추출해야 한다.
        val filler = "<div>x</div>".repeat(20_000) // 약 240KB
        val html =
            "<html><head></head><body>$filler" +
                """<script type="application/ld+json">{"@type":"Product","name":"뒤쪽상품","offers":{"price":"77000","priceCurrency":"KRW"}}</script>""" +
                "</body></html>"
        stubPageFetcher.build = { PageContent(it, html) }

        val snapshot = extractor.extract(link)

        assertEquals("뒤쪽상품", snapshot.name)
        assertEquals(77_000, snapshot.currentPrice)
        assertEquals(0, stubGeminiClient.invocations)
    }

    @Test
    fun `fallback 시 LLM 입력 HTML 은 정리 후 200KB 상한으로 잘린다`() {
        stubGeminiClient.reset()
        stubGeminiClient.build = { GeminiExtractionResult(isProductPage = true, name = "엘엘엠상품", currentPrice = 1_000) }
        // 구조화 데이터 없는 대용량 페이지 → fallback. sanitize 후에도 200KB 를 넘으면 앞쪽(HEAD)은 남고 200KB 뒤(TAIL)는 잘려야 한다.
        val filler = "<p>filler line content here</p>".repeat(20_000) // 약 620KB
        val html = "<html><head></head><body><p>HEAD_MARKER</p>$filler<p>TAIL_MARKER</p></body></html>"
        stubPageFetcher.build = { PageContent(it, html) }

        extractor.extract(link)

        val sentHtml = llmInputHtmlOf(stubGeminiClient.lastRequest)
        assertTrue(sentHtml.contains("HEAD_MARKER"), "앞부분은 LLM 입력에 포함돼야 한다")
        assertFalse(sentHtml.contains("TAIL_MARKER"), "200KB 뒤(MAX_LLM_CHARS 초과)는 잘려 LLM 입력에서 빠져야 한다")
    }

    // GeminiExtractionRequest 의 HTML part(마지막 Part)에서 sanitize 된 LLM 입력 HTML 을 꺼낸다.
    private fun llmInputHtmlOf(request: Any?): String {
        val extractionRequest = request as GeminiExtractionRequest
        return extractionRequest.contents
            .first()
            .parts
            .last()
            .text
    }

    private fun productJsonLdHtml(
        name: String,
        price: String,
    ): String =
        """<html><head><script type="application/ld+json">{"@type":"Product","name":"$name","offers":{"price":"$price","priceCurrency":"KRW"}}</script></head><body></body></html>"""

    private fun priceOnlyJsonLdHtml(price: String): String =
        """<html><head><script type="application/ld+json">{"@type":"Product","offers":{"price":"$price"}}</script></head><body></body></html>"""
}
