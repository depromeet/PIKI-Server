package com.depromeet.piki.auth.infrastructure.oauth

import com.depromeet.piki.auth.infrastructure.oauth.google.GoogleOAuthClient
import com.depromeet.piki.auth.infrastructure.oauth.google.GoogleProperties
import com.depromeet.piki.auth.infrastructure.oauth.kakao.KakaoOAuthClient
import com.depromeet.piki.auth.infrastructure.oauth.kakao.KakaoProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 운영 OAuthClient 빈 배선. 클라이언트는 Properties(env)만 의존하는 단순 객체라 @Component 대신 명시 @Bean.
// 통합 테스트는 oauth.client.enabled=false 로 이 설정을 꺼서, IntegrationStubs 의 stub 빈
// (googleOAuthClient/kakaoOAuthClient)만 컨텍스트에 남게 한다 (외부 호출 격리, 빈 충돌 없음).
@Configuration
@ConditionalOnProperty(name = ["oauth.client.enabled"], havingValue = "true", matchIfMissing = true)
class OAuthClientConfig {
    @Bean
    fun googleOAuthClient(googleProperties: GoogleProperties): OAuthClient = GoogleOAuthClient(googleProperties)

    @Bean
    fun kakaoOAuthClient(kakaoProperties: KakaoProperties): OAuthClient = KakaoOAuthClient(kakaoProperties)
}
