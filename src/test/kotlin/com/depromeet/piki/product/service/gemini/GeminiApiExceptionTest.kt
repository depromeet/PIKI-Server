package com.depromeet.piki.product.service.gemini

import com.depromeet.piki.common.exception.ErrorCategory
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import kotlin.test.assertEquals

class GeminiApiExceptionTest {
    @ParameterizedTest
    @ValueSource(ints = [500, 502, 503, 504])
    fun `5xx 응답은 RETRYABLE 로 분류된다`(status: Int) {
        val responseError = HttpServerErrorException(HttpStatus.valueOf(status))

        val exception = GeminiApiException.fromResponseError(responseError)

        assertEquals(ErrorCategory.RETRYABLE, exception.category)
    }

    @Test
    fun `429 Too Many Requests 는 RETRYABLE 로 분류된다`() {
        val responseError = HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)

        val exception = GeminiApiException.fromResponseError(responseError)

        assertEquals(ErrorCategory.RETRYABLE, exception.category)
    }

    @Test
    fun `408 Request Timeout 은 RETRYABLE 로 분류된다`() {
        val responseError = HttpClientErrorException(HttpStatus.REQUEST_TIMEOUT)

        val exception = GeminiApiException.fromResponseError(responseError)

        assertEquals(ErrorCategory.RETRYABLE, exception.category)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404])
    fun `429·408 을 제외한 4xx 응답은 SERVER_ERROR 로 분류된다`(status: Int) {
        val responseError = HttpClientErrorException(HttpStatus.valueOf(status))

        val exception = GeminiApiException.fromResponseError(responseError)

        assertEquals(ErrorCategory.SERVER_ERROR, exception.category)
    }
}
