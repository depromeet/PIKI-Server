package com.depromeet.piki.common.response

import com.depromeet.piki.common.exception.ErrorCategory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import kotlin.test.assertEquals
import kotlin.test.assertNull

// ApiResponseBody 가 요청 추적 ID(MDC traceId)를 응답에 싣는 로직의 단위 검증.
// 운영에선 Micrometer Tracing(Brave)이 매 요청 스코프에서 MDC 에 traceId 를 넣고, 모든 응답 생성
// 경로(컨트롤러·예외 핸들러·Security 핸들러)가 ok/created/fail 팩토리를 거쳐 그 값을 싣는다.
class ApiResponseBodyTest {
    // MDC 는 스레드로컬 전역이라 테스트 간 누수를 차단한다 (각 테스트는 필요한 상태를 본문에서 명시 set).
    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `MDC 에 traceId 가 있으면 모든 팩토리(ok·created·fail)가 그 값을 응답 traceId 로 싣는다`() {
        MDC.put("traceId", "65b2e1f0c3a94d77")

        assertEquals("65b2e1f0c3a94d77", ApiResponseBody.ok<Unit>().traceId)
        assertEquals("65b2e1f0c3a94d77", ApiResponseBody.created<Unit>().traceId)
        assertEquals("65b2e1f0c3a94d77", ApiResponseBody.fail<Unit>(ErrorCategory.SERVER_ERROR).traceId)
    }

    @Test
    fun `MDC 에 traceId 가 없으면 traceId 는 null 이다`() {
        MDC.remove("traceId")

        assertNull(ApiResponseBody.ok<Unit>().traceId)
        assertNull(ApiResponseBody.fail<Unit>(ErrorCategory.INVALID_INPUT).traceId)
    }
}
