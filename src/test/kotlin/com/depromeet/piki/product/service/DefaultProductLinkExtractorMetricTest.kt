package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.gemini.GeminiExtractionResult
import com.depromeet.piki.product.service.gemini.GeminiHtmlExtractor
import com.depromeet.piki.product.service.structured.StructuredDataExtractor
import com.depromeet.piki.support.StubGeminiClient
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.assertTrue

// product.extract 카운터가 운영 레지스트리(Prometheus)에서 두 경로(via=structured/llm) 모두 scrape 되는지 검증한다.
// Prometheus(client 1.x)는 같은 메트릭 이름의 태그 키 집합이 다르면 뒤 시계열을 예외 없이 조용히 드롭하므로,
// structured 와 llm 두 경로의 태그 키가 {via, reason} 으로 일치해야 둘 다 남는다. 일반 통합 테스트는
// SimpleMeterRegistry(이 제약 미적용)를 주입해 이 회귀를 못 잡으므로, 운영과 같은 PrometheusMeterRegistry 로
// 추출기를 직접 구성하는 별도 분류로 둔다(외부 경계 PageFetcher·GeminiClient 만 stub).
class DefaultProductLinkExtractorMetricTest {
    private val link = ProductLink.parse("https://shop.example.com/products/42")

    @Test
    fun `structured 와 llm 두 경로의 product_extract 시계열이 Prometheus scrape 에 모두 남는다`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val stubGemini = StubGeminiClient()
        val pageFetcher = StubFetcher()
        val extractor =
            DefaultProductLinkExtractor(
                pageFetcher = pageFetcher,
                structuredDataExtractor = StructuredDataExtractor(jacksonObjectMapper()),
                geminiHtmlExtractor = GeminiHtmlExtractor(stubGemini),
                meterRegistry = registry,
            )

        // 1) 구조화 데이터로 직접 파싱 성공 → via=structured
        pageFetcher.html =
            """<html><head><script type="application/ld+json">{"@type":"Product","name":"직접파싱","offers":{"price":"1000"}}</script></head><body></body></html>"""
        extractor.extract(link)

        // 2) 구조화 데이터 없음 → LLM fallback → via=llm,reason=no_data
        stubGemini.build = { GeminiExtractionResult(isProductPage = true, name = "엘엘엠", currentPrice = 2_000) }
        pageFetcher.html = "<html><body>구조화 없음</body></html>"
        extractor.extract(link)

        val scrape = registry.scrape()
        val lines = scrape.lines().filter { it.startsWith("product_extract_total{") }
        // 라벨 출력 순서는 Prometheus 가 정렬하므로(via/reason 순서 비보장) 부분 문자열로 단언한다.
        assertTrue(
            lines.any { it.contains("""via="structured"""") && it.contains("""reason="none"""") },
            "structured 시계열(reason=none)이 scrape 에 있어야 한다:\n$scrape",
        )
        assertTrue(
            lines.any { it.contains("""via="llm"""") },
            "llm 시계열이 scrape 에 있어야 한다 — 태그 키 불일치로 드롭되면 안 된다:\n$scrape",
        )
    }

    private class StubFetcher : PageFetcher {
        var html: String = ""

        override fun fetch(link: ProductLink): PageContent = PageContent(link, html)
    }
}
