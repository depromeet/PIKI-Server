package com.depromeet.piki.product.service.gemini

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.ProductSnapshot
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component

// 구조화 우선 파싱이 실패했을 때의 fallback. 오케스트레이터(DefaultProductLinkExtractor)가 파싱한 Document 를
// 공유받아 LLM 입력으로 직렬화한다. 구조화 파서가 먼저 읽기를 끝낸 뒤 호출되므로, 이 단계에서 Document 를
// 직접 변형(불필요 노드 제거)해도 안전하다.
@Component
class GeminiHtmlExtractor(
    private val geminiClient: GeminiClient,
) {
    fun extract(
        document: Document,
        link: ProductLink,
    ): ProductSnapshot {
        val html = sanitize(document)
        val request = GeminiExtractionRequest.forHtmlExtraction(link.value, html)
        val result = geminiClient.generateContent(request, GeminiExtractionResult::class.java)
        return result.toProductSnapshot(link)
    }

    // LLM 입력에서 토큰 낭비·오판 요소(<script>/<style>/주석)를 제거하고, 토큰 비용 상한(MAX_LLM_CHARS)으로 자른다.
    // <script type="application/ld+json"> 은 product schema 라 보존하며, ld+json 판별을 jsoup(파싱된 attr 기준)으로 해
    // type 변형(charset 파라미터·공백 등)에도 정확하다 — 구조화 파서의 select 와 같은 기준이라 두 경로가 일관된다.
    // 절단은 fetch 단계(원본 앞부분)가 아니라 여기서 정리된 HTML 에 적용한다 — script/style 을 걷어낸 뒤라 같은
    // 길이 안에 실제 상품 정보(가격 span 등)가 훨씬 더 담긴다. fetch 단계는 구조화 추출을 위해 전체를 보존한다.
    private fun sanitize(document: Document): String {
        document
            .select("script")
            .filter { !it.attr("type").trim().startsWith("application/ld+json", ignoreCase = true) }
            .forEach { it.remove() }
        document.select("style").remove()
        val cleaned = document.outerHtml().replace(COMMENT_PATTERN, "")
        return if (cleaned.length > MAX_LLM_CHARS) cleaned.substring(0, MAX_LLM_CHARS) else cleaned
    }

    companion object {
        // Gemini 입력의 토큰 비용 상한. fetch 단계가 아니라 sanitize 직후 정리된 HTML 에 적용한다.
        private const val MAX_LLM_CHARS = 200_000

        private val COMMENT_PATTERN =
            Regex(
                "<!--.*?-->",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
    }
}
