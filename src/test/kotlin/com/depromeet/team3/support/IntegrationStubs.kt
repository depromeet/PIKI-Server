package com.depromeet.team3.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// 통합 테스트의 외부 호출 stub 빈을 한 곳에 모은다.
// IntegrationTestSupport 가 import 하므로 모든 통합 테스트가 같은 컨텍스트를 공유한다.
// 클래스별 @TestConfiguration / @Import 로 컨텍스트를 분기하면 캐시 적중률이 떨어지므로 금지.
//
// 각 stub 은 운영 @Component 빈(GeminiProductExtractor·GeminiOcrClient)과 타입이 같아
// 주입 후보가 2개가 된다. @Primary 로 stub 우선을 명시한다 — 빈 이름과 주입 지점
// 파라미터명이 우연히 일치하는 데 기대지 않으므로, 파라미터명을 리팩터링해도 격리가 깨지지 않는다.
@TestConfiguration(proxyBeanMethods = false)
class IntegrationStubs {
    @Bean
    @Primary
    fun productExtractor(): StubProductExtractor = StubProductExtractor()

    @Bean
    @Primary
    fun ocrClient(): StubOcrClient = StubOcrClient()
}
