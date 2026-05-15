package com.depromeet.team3.support

import com.depromeet.team3.auth.infrastructure.oauth.OAuthProvider
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

// 통합 테스트의 외부 호출 stub 빈을 한 곳에 모은다.
// IntegrationTestSupport 가 import 하므로 모든 통합 테스트가 같은 컨텍스트를 공유한다.
// 클래스별 @TestConfiguration / @Import 로 컨텍스트를 분기하면 캐시 적중률이 떨어지므로 금지.
@TestConfiguration(proxyBeanMethods = false)
class IntegrationStubs {
    @Bean
    fun productExtractor(): StubProductExtractor = StubProductExtractor()

    @Bean
    fun kakaoOAuthClient(): StubOAuthClient = StubOAuthClient(OAuthProvider.KAKAO)

    @Bean
    fun googleOAuthClient(): StubOAuthClient = StubOAuthClient(OAuthProvider.GOOGLE)
}
