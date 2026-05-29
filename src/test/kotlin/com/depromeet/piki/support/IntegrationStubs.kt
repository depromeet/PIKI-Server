package com.depromeet.piki.support

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.item.service.AsyncImageParsingWorker
import com.depromeet.piki.item.service.AsyncItemParsingWorker
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// 통합 테스트의 외부 호출 stub 빈을 한 곳에 모은다.
// IntegrationTestSupport 가 import 하므로 모든 통합 테스트가 같은 컨텍스트를 공유한다.
// 클래스별 @TestConfiguration / @Import 로 컨텍스트를 분기하면 캐시 적중률이 떨어지므로 금지.
//
// 각 stub 은 운영 @Component 빈(GeminiProductLinkExtractor·GeminiProductImageExtractor)과 타입이 같아
// 주입 후보가 2개가 된다. @Primary 로 stub 우선을 명시한다 — 빈 이름과 주입 지점
// 파라미터명이 우연히 일치하는 데 기대지 않으므로, 파라미터명을 리팩터링해도 격리가 깨지지 않는다.
@TestConfiguration(proxyBeanMethods = false)
class IntegrationStubs {
    @Bean
    @Primary
    fun productLinkExtractor(): StubProductLinkExtractor = StubProductLinkExtractor()

    @Bean
    @Primary
    fun productImageExtractor(): StubProductImageExtractor = StubProductImageExtractor()

    @Bean
    @Primary
    fun imageStorage(): StubImageStorage = StubImageStorage()

    // ItemParsingWorker·ImageParsingWorker 는 내부 비동기 워커를 래핑한 configurable stub.
    // enabled=true (기본): 실제 워커로 위임 — WishlistRegisterAsyncIntegrationTest 는 이 경로를 사용한다.
    // enabled=false: no-op — @Transactional 통합 테스트에서 미커밋 item 접근으로 발생하는 warn 로그 노이즈를
    //   없애려면 테스트 본문에서 false 로 설정한다(설정한 테스트가 직접 복원한다).
    @Bean
    @Primary
    fun itemParsingWorker(asyncItemParsingWorker: AsyncItemParsingWorker): StubItemParsingWorker =
        StubItemParsingWorker(asyncItemParsingWorker)

    @Bean
    @Primary
    fun imageParsingWorker(asyncImageParsingWorker: AsyncImageParsingWorker): StubImageParsingWorker =
        StubImageParsingWorker(asyncImageParsingWorker)

    // OAuth client 는 운영에서 아직 Bean 으로 등록되지 않은 상태 (Task 6 에서 OAuthClient
    // Bean 등록 예정 — epic #122). 주입 후보가 stub 하나뿐이라 @Primary 불필요.
    @Bean
    fun kakaoOAuthClient(): StubOAuthClient = StubOAuthClient(OAuthProvider.KAKAO)

    @Bean
    fun googleOAuthClient(): StubOAuthClient = StubOAuthClient(OAuthProvider.GOOGLE)

    // admin 챗봇의 Gemini function calling 외부 호출 stub. 운영 HttpAdminGeminiClient 와 타입이 같아
    // @Primary 로 우선시킨다. test 컨텍스트는 admin.enabled=true 라 운영 빈도 함께 뜨므로 @Primary 가 필요하다.
    @Bean
    @Primary
    fun adminGeminiClient(): StubAdminGeminiClient = StubAdminGeminiClient()
}
