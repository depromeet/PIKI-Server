package com.depromeet.team3.product.service.gemini

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.service.PageFetcher
import com.depromeet.team3.product.service.ProductLinkExtractor
import com.depromeet.team3.product.service.ProductSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GeminiProductLinkExtractor(
    private val geminiHttpClient: GeminiHttpClient,
    private val pageFetcher: PageFetcher,
) : ProductLinkExtractor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun extract(link: ProductLink): ProductSnapshot {
        val fetchStart = System.nanoTime()
        val page = pageFetcher.fetch(link)
        val fetchMs = (System.nanoTime() - fetchStart) / 1_000_000

        val html = sanitize(page.html)
        val request = GeminiExtractionRequest.forHtmlExtraction(link.value, html)

        val llmStart = System.nanoTime()
        val result = geminiHttpClient.generateContent(request, GeminiExtractionResult::class.java)
        val llmMs = (System.nanoTime() - llmStart) / 1_000_000

        log.info(
            "extract latency: fetch={}ms llm={}ms html={}chars url={}",
            fetchMs,
            llmMs,
            html.length,
            link.safeLogString(),
        )
        return result.toProductSnapshot(link)
    }

    // LLM 입력에서 <script>/<style>/주석을 제거해 토큰 낭비와 오판(스크립트 안의 가짜 가격 JSON 등)을 줄인다.
    // 단 <script type="application/ld+json"> 은 product schema 가 들어있을 가능성이 높아 보존한다.
    private fun sanitize(html: String): String =
        html
            .replace(NON_LDJSON_SCRIPT_PATTERN, "")
            .replace(STYLE_PATTERN, "")
            .replace(COMMENT_PATTERN, "")

    companion object {
        // application/ld+json 만 살리고 나머지 <script> 블록은 제거. (?!...) 는 negative lookahead.
        private val NON_LDJSON_SCRIPT_PATTERN =
            Regex(
                "<script\\b(?![^>]*type=[\"']application/ld\\+json[\"'])[^>]*>.*?</script>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        private val STYLE_PATTERN =
            Regex(
                "<style\\b[^>]*>.*?</style>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        private val COMMENT_PATTERN =
            Regex(
                "<!--.*?-->",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
    }
}
