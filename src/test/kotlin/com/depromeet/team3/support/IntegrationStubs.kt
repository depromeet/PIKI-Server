package com.depromeet.team3.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

// 통합 테스트의 외부 호출 stub 빈을 한 곳에 모은다.
// IntegrationTestSupport 가 import 하므로 모든 통합 테스트가 같은 컨텍스트를 공유한다.
// 클래스별 @TestConfiguration / @Import 로 컨텍스트를 분기하면 캐시 적중률이 떨어지므로 금지.
@TestConfiguration(proxyBeanMethods = false)
class IntegrationStubs {
    @Bean
    fun productExtractor(): StubProductExtractor = StubProductExtractor()

    // 빈 이름을 OcrService 의 생성자 파라미터명(ocrClient)과 맞춘다.
    // GeminiOcrClient(@Component, 빈 이름 geminiOcrClient) 와 타입이 같아 후보가 2개가 되는데,
    // Spring 은 이때 주입 지점 파라미터명과 일치하는 빈을 선택하므로 stub 이 우선 적용된다.
    @Bean
    fun ocrClient(): StubOcrClient = StubOcrClient()
}
