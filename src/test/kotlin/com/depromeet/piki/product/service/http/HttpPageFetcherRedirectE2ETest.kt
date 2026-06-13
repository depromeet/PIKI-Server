package com.depromeet.piki.product.service.http

import com.depromeet.piki.product.domain.ProductLink
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * 실제 외부 페이지 fetch (지그재그 same-domain redirect · 무신사 OneLink cross-domain redirect).
 * 비용·외부 의존성이 있어 기본 @Disabled. 검증 필요 시 수동 enable.
 */
@Disabled("실제 외부 페이지 fetch (지그재그 same-domain · 무신사 OneLink cross-domain). 검증 필요 시 수동 enable.")
class HttpPageFetcherRedirectE2ETest {
    private val dnsResolver = RequestScopedDnsResolver()
    private val fetcher =
        HttpPageFetcher(
            PageFetchHttpClientConfig().pageFetchRestClient(ObservationRegistry.NOOP, dnsResolver),
            dnsResolver,
        )

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `www 지그재그 URL 은 same-domain redirect 를 따라가 본문을 받는다`() {
        // www.zigzag.kr → zigzag.kr 301 redirect. 보강 전엔 emptyBody 로 실패하던 케이스.
        val link = ProductLink.parse("https://www.zigzag.kr/catalog/products/136677613")

        val page = fetcher.fetch(link)

        assertTrue(page.html.length > 1_000, "redirect 를 따라가 실제 본문을 받았어야 한다")
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `무신사 OneLink 단축링크는 cross-domain redirect 를 따라가 최종 상품 페이지 본문을 받는다`() {
        // musinsa.onelink.me(AppsFlyer) 가 301 로 www.musinsa.com/products/... 로 보낸다(등록도메인 변경).
        // PC UA 로 요청하는 우리 fetch 는 이 301 경로를 받는다. cross-domain 허용 후 따라가 최종 무신사 페이지에 도달해야 한다.
        val link = ProductLink.parse("https://musinsa.onelink.me/PvkC/lx16ha0g")

        val page = fetcher.fetch(link)

        assertTrue((page.finalUrl.value.host ?: "").endsWith("musinsa.com"), "최종 호스트가 musinsa.com 이어야 한다")
        assertTrue(page.html.contains("og:title"), "최종 무신사 상품 페이지(OG 메타태그)를 받았어야 한다")
    }
}
