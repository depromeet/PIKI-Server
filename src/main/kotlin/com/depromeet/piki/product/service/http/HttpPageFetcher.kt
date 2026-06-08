package com.depromeet.piki.product.service.http

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.domain.ProductLinkException
import com.depromeet.piki.product.service.PageContent
import com.depromeet.piki.product.service.PageFetcher
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.HttpURLConnection
import java.net.InetAddress

@Component
class HttpPageFetcher(
    observationRegistry: ObservationRegistry,
) : PageFetcher {
    private val log = LoggerFactory.getLogger(javaClass)

    // JDK 레벨 자동 redirect 추적은 끈다(instanceFollowRedirects=false). 자동 추적은 Location 점프 전에 SSRF·도메인
    // 검증을 끼울 수 없어 사설 IP·외부 도메인 우회를 연다. 대신 fetch() 가 3xx 를 직접 받아 같은 회사 도메인 + SSRF
    // 가드를 통과한 redirect 만 수동으로 따라간다.
    private val requestFactory =
        object : SimpleClientHttpRequestFactory() {
            override fun prepareConnection(
                connection: HttpURLConnection,
                httpMethod: String,
            ) {
                super.prepareConnection(connection, httpMethod)
                connection.instanceFollowRedirects = false
            }
        }.apply {
            setConnectTimeout(CONNECT_TIMEOUT_MS)
            setReadTimeout(READ_TIMEOUT_MS)
        }

    private val restClient =
        RestClient
            .builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,*/*;q=0.8")
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko,en;q=0.9")
            // 상품 페이지 fetch 가 trace 의 한 구간(HTTP client span)으로 잡히게 한다.
            .observationRegistry(observationRegistry)
            .build()

    override fun fetch(link: ProductLink): PageContent {
        var current = link
        repeat(MAX_REDIRECTS + 1) {
            guardAgainstInternalHost(current)
            val response = request(current)
            if (response.statusCode.is3xxRedirection) {
                current = nextRedirect(current, response)
                return@repeat
            }
            val body =
                response.body ?: run {
                    log.warn("link fetch empty body url={}", current.safeLogString())
                    throw PageFetchException.emptyBody()
                }
            val truncated = if (body.length > MAX_HTML_CHARS) body.substring(0, MAX_HTML_CHARS) else body
            // link 는 사용자가 등록한 원본 URL 을 유지하고, html 만 redirect 를 따라간 최종 페이지로 채운다.
            return PageContent(link = link, html = truncated)
        }
        log.warn("link fetch too many redirects url={}", link.safeLogString())
        throw PageFetchException.tooManyRedirects()
    }

    // String 오버로드는 URI 템플릿으로 해석되어 {q} 같은 쿼리가 변수로 치환될 수 있으므로 URI 로 명시 전달.
    // toEntity 로 3xx status·Location 에 접근한다(기본 onStatus 는 4xx/5xx 만 에러로 던지므로 3xx 는 정상 수신).
    private fun request(current: ProductLink): ResponseEntity<String> =
        try {
            restClient
                .get()
                .uri(current.value)
                .retrieve()
                .toEntity(String::class.java)
        } catch (e: RestClientResponseException) {
            log.warn("link fetch failed: status={} url={}", e.statusCode, current.safeLogString())
            throw when {
                e.statusCode.is5xxServerError -> PageFetchException.upstreamError(e)
                else -> PageFetchException.clientError(e)
            }
        } catch (e: ResourceAccessException) {
            log.warn("link fetch upstream error url={}", current.safeLogString())
            throw PageFetchException.upstreamError(e)
        }

    // 3xx 응답의 Location 을 절대 URI 로 만들고, 같은 회사 도메인 + https 면 다음 hop 으로, 아니면 SSRF 로 차단한다.
    private fun nextRedirect(
        current: ProductLink,
        response: ResponseEntity<String>,
    ): ProductLink {
        val location =
            response.headers.location ?: run {
                log.warn("link fetch redirect without Location url={}", current.safeLogString())
                throw PageFetchException.upstreamError(IllegalStateException("redirect without Location"))
            }
        // 상대 Location(/path)도 원본 URI 에 resolve 해 절대 URI 로 만든다.
        val target = current.value.resolve(location)
        if (!RedirectPolicy.isSameRegistrableDomain(current.value.host, target.host)) {
            log.warn("[SSRF] blocked: cross-domain redirect url={}", current.safeLogString())
            throw PageFetchException.blockedHost()
        }
        // https 강제(http 다운그레이드 차단)는 ProductLink.parse 가 담당한다.
        return try {
            ProductLink.parse(target.toString())
        } catch (e: ProductLinkException) {
            log.warn("link fetch redirect to non-https url={}", current.safeLogString())
            throw PageFetchException.blockedHost()
        }
    }

    // host 가 loopback / 사설 / 링크로컬 / 메타데이터 IP 로 resolve 되면 SSRF 위험으로 차단.
    // 외부 쇼핑몰만 fetch 하는 것이 본 컴포넌트의 책임이라 외부 라우팅 가능 IP 만 허용한다.
    // redirect 를 따라갈 때도 매 hop 의 새 host 에 대해 다시 호출해 점프 후 SSRF 를 막는다.
    private fun guardAgainstInternalHost(link: ProductLink) {
        val host =
            link.value.host ?: run {
                log.warn("[SSRF] blocked: missing host url={}", link.safeLogString())
                throw PageFetchException.blockedHost()
            }
        val addresses =
            try {
                InetAddress.getAllByName(host)
            } catch (e: java.net.UnknownHostException) {
                log.info("link fetch unknown host url={}", link.safeLogString())
                throw PageFetchException.upstreamError(e)
            }
        val anyInternal =
            addresses.any { addr ->
                addr.isLoopbackAddress ||
                    addr.isAnyLocalAddress ||
                    addr.isSiteLocalAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isMulticastAddress ||
                    // AWS / GCP 인스턴스 메타데이터. site-local 범위에 속하지 않아 별도 차단.
                    addr.hostAddress == "169.254.169.254"
            }
        if (anyInternal) {
            log.error("[SSRF] blocked: internal address resolved url={}", link.safeLogString())
            throw PageFetchException.blockedHost()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 15_000

        // 같은 회사 도메인 안에서의 www↔non-www·http→https 정도라 한두 번이면 충분. 무한·체인 redirect 는 여기서 끊는다.
        private const val MAX_REDIRECTS = 3

        // LLM 토큰 비용 상한. 대형 쇼핑몰(쿠팡 등)은 1MB 가 넘는 경우도 있음.
        private const val MAX_HTML_CHARS = 200_000

        // 기본 RestClient UA 는 일부 사이트에서 차단되므로 실제 브라우저 UA 로 위장.
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
