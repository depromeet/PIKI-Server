package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.gemini.GeminiHtmlExtractor
import com.depromeet.piki.product.service.structured.StructuredDataExtractor
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

// 상품 URL 추출의 오케스트레이터. fetch 를 1회 수행하고, 구조화 데이터(JSON-LD/OpenGraph)로 먼저 파싱한다.
// 필수 필드가 검증을 통과하면 LLM 을 건너뛰고, 미달이면 같은 HTML 을 Gemini 로 넘겨 추출한다(재fetch 없음).
// 진입점은 ProductLinkExtractor 단일 빈이라 AsyncItemParsingWorker 의 호출·통합 테스트 stub 구조가 그대로 유지된다.
@Component
class DefaultProductLinkExtractor(
    private val pageFetcher: PageFetcher,
    private val structuredDataExtractor: StructuredDataExtractor,
    private val geminiHtmlExtractor: GeminiHtmlExtractor,
) : ProductLinkExtractor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun extract(link: ProductLink): ProductSnapshot {
        val fetchStart = System.nanoTime()
        val page = pageFetcher.fetch(link)
        val fetchMs = (System.nanoTime() - fetchStart) / 1_000_000

        // HTML 을 한 번만 파싱해 구조화 파서와 Gemini fallback 이 같은 Document 를 공유한다(파싱·ld+json 식별 중복 제거).
        val document = Jsoup.parse(page.html, link.value.toString())

        structuredDataExtractor.extract(document, link)?.let { snapshot ->
            log.info(
                "extract via=structured fetch={}ms html={}chars url={}",
                fetchMs,
                page.html.length,
                link.safeLogString(),
            )
            return snapshot
        }

        val llmStart = System.nanoTime()
        val snapshot = geminiHtmlExtractor.extract(document, link)
        val llmMs = (System.nanoTime() - llmStart) / 1_000_000
        log.info(
            "extract via=llm fetch={}ms llm={}ms html={}chars url={}",
            fetchMs,
            llmMs,
            page.html.length,
            link.safeLogString(),
        )
        return snapshot
    }
}
