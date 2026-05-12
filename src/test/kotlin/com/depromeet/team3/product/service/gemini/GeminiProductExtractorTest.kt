package com.depromeet.team3.product.service.gemini

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.support.IntegrationTestSupport
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull

/**
 * 실제 Gemini API 를 호출하는 통합 테스트.
 *
 * 비용·외부 의존성이 있으므로 기본은 @Disabled. 호출 경로 검증이 필요할 때만 명시적으로 enable.
 * GEMINI_API_KEY 가 환경에 있다고 가정한다.
 */
@Disabled("실제 Gemini API 호출. 검증 필요 시 수동으로 enable 후 실행.")
class GeminiProductExtractorTest : IntegrationTestSupport() {
    @Autowired
    lateinit var extractor: GeminiProductExtractor

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    fun `Gemini end-to-end 호출이 살아 있고 응답을 구조화해 돌려준다`() {
        // 호출 경로(인증·스키마·직렬화·모델) 가 살아 있는지 확인하는 생존성 테스트.
        // 어떤 종류의 실패도 회귀 신호로 간주해 fail 시킨다 — ProductExtractionException 분기를
        // 정상으로 인정하면 키 만료·모델 변경·응답 파싱 깨짐도 사일런트로 통과한다.
        // 비상품 판정 분기 자체의 동작은 stub 기반 단위 테스트로 별도 검증한다.
        val link = ProductLink.parse("https://www.apple.com/shop/buy-iphone")

        val product = extractor.extract(link)

        assertNotNull(product.name, "Gemini 가 상품명을 추출했어야 한다")
    }
}
