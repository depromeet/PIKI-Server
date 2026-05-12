package com.depromeet.team3.product.service.gemini

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.domain.ProductSnapshot
import com.depromeet.team3.product.service.PageFetcher
import com.depromeet.team3.product.service.ProductExtractor
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class GeminiProductExtractor(
    private val objectMapper: ObjectMapper,
    private val geminiProperties: GeminiProperties,
    private val pageFetcher: PageFetcher,
) : ProductExtractor {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient =
        RestClient
            .builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT_MS)
                    setReadTimeout(READ_TIMEOUT_MS)
                },
            ).build()

    override fun extract(link: ProductLink): ProductSnapshot {
        val fetchStart = System.nanoTime()
        val page = pageFetcher.fetch(link)
        val fetchMs = (System.nanoTime() - fetchStart) / 1_000_000

        val html = sanitize(page.html)
        val request = GeminiExtractionRequest.forHtmlExtraction(link.value, html)

        val llmStart = System.nanoTime()
        // TODO: Gemini API 의 간헐적 5xx/타임아웃 대응 재시도 로직 필요. RETRYABLE 카테고리 예외 대상.
        val response =
            try {
                restClient
                    .post()
                    .uri {
                        it
                            .path("/v1beta/models/{model}:generateContent")
                            .build(geminiProperties.model)
                    }.header(GEMINI_API_KEY_HEADER, geminiProperties.apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body<GeminiExtractionResponse>()
            } catch (e: RestClientResponseException) {
                throw when {
                    e.statusCode.is5xxServerError -> GeminiApiException.upstreamError(e)
                    else -> GeminiApiException.clientError(e)
                }
            } catch (e: ResourceAccessException) {
                throw GeminiApiException.upstreamError(e)
            } ?: throw GeminiApiException.emptyResponse()
        val llmMs = (System.nanoTime() - llmStart) / 1_000_000

        log.info(
            "extract latency: fetch={}ms llm={}ms html={}chars url={}",
            fetchMs,
            llmMs,
            html.length,
            link.safeLogString(),
        )

        val text = response.extractText()
        val result =
            try {
                objectMapper.readValue<GeminiExtractionResult>(text)
            } catch (e: Exception) {
                throw GeminiApiException.parseError(e)
            }
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
        private const val CONNECT_TIMEOUT_MS = 5_000

        // LLM 응답은 수십 초까지 갈 수 있어 넉넉하게 설정.
        private const val READ_TIMEOUT_MS = 60_000

        // API 키는 access log 에 남지 않도록 쿼리 대신 헤더로 전달.
        // https://ai.google.dev/gemini-api/docs/api-key#provide-api-key-explicitly
        private const val GEMINI_API_KEY_HEADER = "x-goog-api-key"

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
