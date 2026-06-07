package com.depromeet.piki.product.service.gemini

import com.depromeet.piki.product.service.PageContent
import com.depromeet.piki.product.service.ProductSnapshot
import org.springframework.stereotype.Component

// fetch 된 HTML 에서 Gemini LLM 으로 상품 정보를 추출한다.
// fetch 는 오케스트레이터(DefaultProductLinkExtractor)가 1회 수행해 PageContent 로 넘기므로,
// 구조화 우선 파싱이 실패했을 때의 fallback 으로 재fetch 없이 같은 HTML 을 받아 호출된다.
@Component
class GeminiHtmlExtractor(
    private val geminiClient: GeminiClient,
) {
    fun extract(page: PageContent): ProductSnapshot {
        val html = sanitize(page.html)
        val request = GeminiExtractionRequest.forHtmlExtraction(page.link.value, html)
        val result = geminiClient.generateContent(request, GeminiExtractionResult::class.java)
        return result.toProductSnapshot(page.link)
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
