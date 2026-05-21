package com.depromeet.team3.ocr.service.gemini

import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.ocr.domain.OcrImage
import com.depromeet.team3.ocr.service.OcrClient
import com.depromeet.team3.product.service.gemini.GeminiApiException
import com.depromeet.team3.product.service.gemini.GeminiProperties
import com.depromeet.team3.product.service.gemini.GeminiRetry
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.Base64

@Component
class GeminiOcrClient(
    private val objectMapper: ObjectMapper,
    private val geminiProperties: GeminiProperties,
) : OcrClient {
    private val geminiRetry = GeminiRetry(geminiProperties.retry)

    private val restClient = RestClient
        .builder()
        .baseUrl("https://generativelanguage.googleapis.com")
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(CONNECT_TIMEOUT_MS)
                setReadTimeout(READ_TIMEOUT_MS)
            },
        )
        .build()

    override fun analyzeImage(image: OcrImage): Product {
        val base64Image = Base64
            .getEncoder()
            .encodeToString(image.bytes)

        val request = GeminiOcrRequest.forImageAnalysis(
            base64Image,
            image.mimeType,
        )

        return geminiRetry.execute {
            val response = try {
                restClient
                    .post()
                    .uri {
                        it
                            .path("/v1beta/models/{model}:generateContent")
                            .build(geminiProperties.model)
                    }
                    .header(GEMINI_API_KEY_HEADER, geminiProperties.apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body<GeminiOcrResponse>()
            } catch (e: RestClientResponseException) {
                throw GeminiApiException.fromResponseError(e)
            } catch (e: ResourceAccessException) {
                throw GeminiApiException.upstreamError(e)
            } ?: throw GeminiApiException.emptyResponse()

            val text = response.extractText()
            val ocrResult = try {
                objectMapper.readValue<GeminiOcrResult>(text)
            } catch (e: Exception) {
                throw GeminiApiException.parseError(e)
            }
            ocrResult.toProduct()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 30_000

        // API 키를 URL 쿼리파라미터 대신 헤더로 전달해 access log 등에 키가 남지 않도록 함.
        // https://ai.google.dev/gemini-api/docs/api-key#provide-api-key-explicitly
        private const val GEMINI_API_KEY_HEADER = "x-goog-api-key"
    }
}
