package com.depromeet.piki.product.service.http

import com.depromeet.piki.product.domain.ProductLink
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.InetAddress
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// redirect 루프(3xx 따라가기·cross-domain 차단·다운그레이드 차단·hop 상한·비-redirect 3xx)를 네트워크 없이 검증한다.
// 응답은 MockRestServiceServer 로 제어하고, DNS 는 가짜 공인 IP 로 주입해 SSRF 가드를 통과시켜 redirect 로직만 격리한다.
class HttpPageFetcherRedirectTest {
    // 모든 host 를 공인 IP 로 해석해 가드를 통과시킨다(여기선 redirect 따라가기·도메인 차단 로직만 검증).
    private val publicIp: (String) -> Array<InetAddress> = { arrayOf(InetAddress.getByName("93.184.216.34")) }

    private fun fetcherWith(configure: (MockRestServiceServer) -> Unit): HttpPageFetcher {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        configure(server)
        return HttpPageFetcher(builder.build(), publicIp)
    }

    @Test
    fun `same-domain redirect 를 따라가 최종 페이지 본문을 받는다`() {
        val fetcher =
            fetcherWith { server ->
                server
                    .expect(requestTo("https://www.zigzag.kr/p"))
                    .andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY).location(URI("https://zigzag.kr/p")))
                server
                    .expect(requestTo("https://zigzag.kr/p"))
                    .andRespond(withSuccess("<html>product</html>", MediaType.TEXT_HTML))
            }

        val page = fetcher.fetch(ProductLink.parse("https://www.zigzag.kr/p"))

        assertEquals("<html>product</html>", page.html)
    }

    @Test
    fun `cross-domain redirect 는 차단한다`() {
        val fetcher =
            fetcherWith { server ->
                server
                    .expect(requestTo("https://www.zigzag.kr/p"))
                    .andRespond(withStatus(HttpStatus.FOUND).location(URI("https://evil.com/p")))
            }

        assertFailsWith<PageFetchException> {
            fetcher.fetch(ProductLink.parse("https://www.zigzag.kr/p"))
        }
    }

    @Test
    fun `https 에서 http 로 다운그레이드하는 redirect 는 차단한다`() {
        val fetcher =
            fetcherWith { server ->
                server
                    .expect(requestTo("https://www.zigzag.kr/p"))
                    .andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY).location(URI("http://zigzag.kr/p")))
            }

        assertFailsWith<PageFetchException> {
            fetcher.fetch(ProductLink.parse("https://www.zigzag.kr/p"))
        }
    }

    @Test
    fun `redirect 가 상한을 넘으면 tooManyRedirects 로 끊는다`() {
        val fetcher =
            fetcherWith { server ->
                // 같은 도메인 self-redirect 를 hop 상한+1 만큼. 끝내 페이지에 도달하지 못한다.
                repeat(4) {
                    server
                        .expect(requestTo("https://zigzag.kr/loop"))
                        .andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY).location(URI("https://zigzag.kr/loop")))
                }
            }

        assertFailsWith<PageFetchException> {
            fetcher.fetch(ProductLink.parse("https://zigzag.kr/loop"))
        }
    }

    @Test
    fun `상대 경로 Location 도 원본 URI 에 resolve 해 따라간다`() {
        val fetcher =
            fetcherWith { server ->
                server
                    .expect(requestTo("https://zigzag.kr/old"))
                    .andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY).location(URI("/new")))
                server
                    .expect(requestTo("https://zigzag.kr/new"))
                    .andRespond(withSuccess("<html>moved</html>", MediaType.TEXT_HTML))
            }

        val page = fetcher.fetch(ProductLink.parse("https://zigzag.kr/old"))

        assertEquals("<html>moved</html>", page.html)
    }

    @Test
    fun `redirect 없이 200 이면 바로 본문을 받는다`() {
        val fetcher =
            fetcherWith { server ->
                server
                    .expect(requestTo("https://zigzag.kr/p"))
                    .andRespond(withSuccess("<html>direct</html>", MediaType.TEXT_HTML))
            }

        val page = fetcher.fetch(ProductLink.parse("https://zigzag.kr/p"))

        assertEquals("<html>direct</html>", page.html)
    }
}
