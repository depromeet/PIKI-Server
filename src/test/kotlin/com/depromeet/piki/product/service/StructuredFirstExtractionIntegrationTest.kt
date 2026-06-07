package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.gemini.GeminiApiException
import com.depromeet.piki.product.service.gemini.GeminiExtractionResult
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubGeminiClient
import com.depromeet.piki.support.StubPageFetcher
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private fun productJsonLdHtml(
        name: String,
        price: String,
    ): String =
        """<html><head><script type="application/ld+json">{"@type":"Product","name":"$name","offers":{"price":"$price","priceCurrency":"KRW"}}</script></head><body></body></html>"""

    private fun priceOnlyJsonLdHtml(price: String): String =
        """<html><head><script type="application/ld+json">{"@type":"Product","offers":{"price":"$price"}}</script></head><body></body></html>"""
}
