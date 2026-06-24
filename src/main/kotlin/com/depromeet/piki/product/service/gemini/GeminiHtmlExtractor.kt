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

    companion object {
        // Gemini 입력의 토큰 비용 상한. fetch 단계가 아니라 sanitize 직후 정리된 HTML 에 적용한다.
        private const val MAX_LLM_CHARS = 200_000

        private val COMMENT_PATTERN =
            Regex(
                "<!--.*?-->",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )

        // LLM 입력에서 토큰 낭비·오판 요소(JS <script>/<style>/주석)를 제거하고, 토큰 비용 상한(MAX_LLM_CHARS)으로 자른다.
        // 단 데이터를 담은 script 는 보존한다(isDataScript): schema.org JSON-LD(product schema)와 일반 JSON data island
        // (Next.js <script id="__NEXT_DATA__" type="application/json"> 등)에 상품명·가격이 들어 있어, JSON-LD/OG 파서가
        // 놓친 사이트를 LLM 이 건져내는 fallback 의 유일한 근거다. 이걸 통째로 지우면(과거 동작) fallback 에 가격이 빠진
        // HTML 이 들어가 LLM 도 손쓸 수 없었다. type 판별은 jsoup(파싱된 attr 기준)이라 charset 파라미터·공백 변형에도 정확하다.
        // 절단은 fetch 단계(원본 앞부분)가 아니라 여기서 정리된 HTML 에 적용한다 — JS·style 을 걷어낸 뒤라 같은
        // 길이 안에 실제 상품 정보가 훨씬 더 담긴다. fetch 단계는 구조화 추출을 위해 전체를 보존한다.
        // 순수 함수라(인스턴스 상태 무의존) companion 에 둬 stub 없이 단위 테스트한다.
        internal fun sanitize(document: Document): String {
            document
                .select("script")
                .filter { !isDataScript(it.attr("type")) }
                .forEach { it.remove() }
            document.select("style").remove()
            val cleaned = document.outerHtml().replace(COMMENT_PATTERN, "")
            return if (cleaned.length > MAX_LLM_CHARS) cleaned.substring(0, MAX_LLM_CHARS) else cleaned
        }

        // LLM 입력에 남길 "데이터 script". JSON-LD(상품 schema)와 일반 JSON data island(Next.js __NEXT_DATA__ 등
        // type=application/json)는 상품명·가격이 담겨 fallback 추출의 근거가 된다. type 이 없거나 text/javascript 인
        // JS 코드 script 는 가격이 inline 변수(window.__PRELOADED_STATE__ 등)에 묻혀 있어도 코드 덩어리라 토큰만 먹고
        // 오판을 부르므로 제거한다 — 그런 거대 state 사이트는 LLM 토큰 상한에도 안 맞아, 전용 파서가 답이다.
        private fun isDataScript(type: String): Boolean {
            val normalized = type.trim()
            return normalized.startsWith("application/ld+json", ignoreCase = true) ||
                normalized.startsWith("application/json", ignoreCase = true)
        }
    }
}
