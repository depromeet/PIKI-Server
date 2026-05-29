package com.depromeet.piki.admin.chat.gemini

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.product.service.gemini.GeminiApiException
import com.depromeet.piki.product.service.gemini.GeminiProperties
import com.depromeet.piki.product.service.gemini.GeminiRetry
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * Gemini generateContent 를 function calling 모드로 호출하는 운영 구현.
 *
 * RestClient 셋업·timeout·`x-goog-api-key` 헤더·[GeminiRetry]·[GeminiApiException] 분류는 기존
 * `GeminiHttpClient` 와 동일한 골격을 따른다. 차이는 응답을 `extractText()`+역직렬화 대신
 * [AdminGeminiResponse] 그대로 반환한다는 점 — function calling 은 functionCall/text 분기가 필요해
 * 기존 `generateContent<Req,Res>` 템플릿의 "텍스트 1파트" 계약과 맞지 않는다.
 */
@Component
@ConditionalOnAdminEnabled
class HttpAdminGeminiClient(
    private val geminiProperties: GeminiProperties,
) : AdminGeminiClient {
    private val restClient =
        RestClient
            .builder()
            .baseUrl(BASE_URL)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT_MS)
                    setReadTimeout(READ_TIMEOUT_MS)
                },
            ).build()

    private val geminiRetry = GeminiRetry(geminiProperties.retry)

    override fun generate(request: AdminGeminiRequest): AdminGeminiResponse =
        geminiRetry.execute {
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
                    .body(AdminGeminiResponse::class.java)
            } catch (e: RestClientResponseException) {
                throw GeminiApiException.fromResponseError(e)
            } catch (e: ResourceAccessException) {
                throw GeminiApiException.upstreamError(e)
            } catch (e: RestClientException) {
                throw GeminiApiException.upstreamError(e)
            } ?: throw GeminiApiException.emptyResponse()
        }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com"
        private const val CONNECT_TIMEOUT_MS = 5_000

        // function calling 멀티턴이라도 단일 호출은 30초 안에 끝나도록 (AdminChatService 가 누적 시간을 별도 가드).
        private const val READ_TIMEOUT_MS = 30_000

        // API 키는 access log 노출 방지를 위해 쿼리 대신 헤더로 전달.
        private const val GEMINI_API_KEY_HEADER = "x-goog-api-key"
    }
}
