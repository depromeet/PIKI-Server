package com.depromeet.piki.product.service.http

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.PageContent
import com.depromeet.piki.product.service.PageFetcher
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
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

    // 외부 페이지 fetch 라 redirect 를 따라가지 않는다. 따라가면 1차 host 검증을
    // 통과해도 사설 IP 로 Location 점프하는 SSRF 우회가 가능하다.
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
        guardAgainstInternalHost(link)

        // String 오버로드는 URI 템플릿으로 해석되어 {q} 같은 쿼리가 변수로 치환될 수 있으므로 URI 로 명시 전달.
        val body =
            try {
                restClient
                    .get()
                    .uri(link.value)
                    .retrieve()
                    .body(String::class.java)
            } catch (e: RestClientResponseException) {
                log.warn("link fetch failed: status={} url={}", e.statusCode, link.safeLogString())
                throw when {
                    e.statusCode.is5xxServerError -> PageFetchException.upstreamError(e)
                    else -> PageFetchException.clientError(e)
                }
            } catch (e: ResourceAccessException) {
                log.warn("link fetch upstream error url={}", link.safeLogString())
                throw PageFetchException.upstreamError(e)
            } ?: run {
                log.warn("link fetch empty body url={}", link.safeLogString())
                throw PageFetchException.emptyBody()
            }

        val truncated = if (body.length > MAX_FETCH_CHARS) body.substring(0, MAX_FETCH_CHARS) else body
        return PageContent(link = link, html = truncated)
    }

    // host 가 loopback / 사설 / 링크로컬 / 메타데이터 IP 로 resolve 되면 SSRF 위험으로 차단.
    // 외부 쇼핑몰만 fetch 하는 것이 본 컴포넌트의 책임이라 외부 라우팅 가능 IP 만 허용한다.
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

        // fetch 본문의 보관·파싱 비용을 막는 안전 상한이다. LLM 토큰 상한이 아니다 — 그건 Gemini 입력 직전
        // GeminiHtmlExtractor 가 정리(sanitize)된 HTML 에 따로 적용한다. 이 상한을 넉넉히 둬야 구조화 추출
        // (JSON-LD/OpenGraph)이 페이지 전체를 본다: JSON-LD·가격이 본문 뒤쪽에 있는 사이트(아마존 약 1.65MB,
        // 나이키 약 0.75MB 실측)는 앞부분만 보면 가격을 놓친다. 동시 파싱(워커 풀) 시 메모리(상한 x 동시 수)를
        // 감안해 무제한이 아니라 3MB 로 바운드한다. 무거운 한국 메이저 몰(쿠팡·G마켓·옥션)은 403 으로 막혀
        // 애초에 fetch 되지 않으므로 이 상한과 무관하다.
        private const val MAX_FETCH_CHARS = 3_000_000

        // 기본 RestClient UA 는 일부 사이트에서 차단되므로 실제 브라우저 UA 로 위장.
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
