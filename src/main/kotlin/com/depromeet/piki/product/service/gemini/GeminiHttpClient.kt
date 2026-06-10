package com.depromeet.piki.product.service.gemini

import io.micrometer.observation.ObservationRegistry
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper

/**
 * Gemini generateContent 호출의 공통 뼈대.
 *
 * RestClient 셋업 · timeout · 헤더 · `{model}:generateContent` 호출 + [GeminiRetry] 적용
 * + 에러 분류([GeminiApiException.fromResponseError]) + [GeminiGenerateContentResponse.extractText]
 * + result 파싱까지 한 곳에서 처리한다.
 *
 * 두 추출기(`GeminiProductLinkExtractor`, `GeminiProductImageExtractor`) 가 자기 Request/Result 타입만
 * 알면 되도록 일반 호출 템플릿을 흡수.
 */
@Component
class GeminiHttpClient(
    private val geminiProperties: GeminiProperties,
    private val objectMapper: ObjectMapper,
    observationRegistry: ObservationRegistry,
) : GeminiClient {
    // ObservationRegistry 를 물려 Gemini 호출(최대 30s)이 trace 의 한 구간(HTTP client span)으로 잡히게 한다.
    // 한 요청 trace 에서 LLM 호출 latency 가 막대로 또렷이 보인다.
    private val restClient =
        RestClient
            .builder()
            .baseUrl(BASE_URL)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT_MS)
                    setReadTimeout(READ_TIMEOUT_MS)
                },
            ).observationRegistry(observationRegistry)
            .build()

    private val geminiRetry = GeminiRetry(geminiProperties.retry)

    /**
     * 임의 Request 본문으로 generateContent 를 호출하고, 응답 텍스트 파트를 [resultType] 으로
     * 역직렬화해 반환한다. 일시 장애는 [GeminiRetry] 정책으로 재시도된다.
     */
    override fun <Req : Any, Res : Any> generateContent(
        request: Req,
        resultType: Class<Res>,
    ): Res =
        geminiRetry.execute {
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
                        .body(GeminiGenerateContentResponse::class.java)
                } catch (e: RestClientResponseException) {
                    throw GeminiApiException.fromResponseError(e)
                } catch (e: ResourceAccessException) {
                    throw GeminiApiException.upstreamError(e)
                } catch (e: RestClientException) {
                    // 응답 본문 추출 중 read-timeout 등은 RestClientResponseException 도 ResourceAccessException 도
                    // 아닌 raw RestClientException("Error while extracting response", content-type octet-stream)으로 온다.
                    // 위 두 catch 를 빠져나가 500 으로 새던 것을 막고, transport 장애로 보고 retryable(502)로 분류한다.
                    throw GeminiApiException.upstreamError(e)
                } ?: throw GeminiApiException.emptyResponse()

            val text = response.extractText()
            try {
                objectMapper.readValue(text, resultType)
            } catch (e: Exception) {
                throw GeminiApiException.parseError(e)
            }
        }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com"
        private const val CONNECT_TIMEOUT_MS = 5_000

        // LLM 응답이 길어질 수 있어 넉넉히 두되, 단건 파싱을 60s 안에 끝내기 위해(#461) 30 초로 제한한다.
        // Gemini 내부 재시도는 기본 off(maxAttempts=1)라 이 30s 가 곧 한 번 호출의 상한이다 — fetch(≤약 20s)와 합쳐 단건 ≤ 약 55s.
        private const val READ_TIMEOUT_MS = 30_000

        // API 키는 access log 에 남지 않도록 쿼리 대신 헤더로 전달.
        // https://ai.google.dev/gemini-api/docs/api-key#provide-api-key-explicitly
        private const val GEMINI_API_KEY_HEADER = "x-goog-api-key"
    }
}
