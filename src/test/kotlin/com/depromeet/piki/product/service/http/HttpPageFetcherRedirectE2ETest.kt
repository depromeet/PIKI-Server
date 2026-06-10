package com.depromeet.piki.product.service.http

import com.depromeet.piki.product.domain.ProductLink
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * 실제 외부 페이지 fetch (지그재그 www redirect). 비용·외부 의존성이 있어 기본 @Disabled. 검증 필요 시 수동 enable.
 */
@Disabled("실제 외부 페이지 fetch (지그재그 www redirect). 검증 필요 시 수동 enable.")
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
}
