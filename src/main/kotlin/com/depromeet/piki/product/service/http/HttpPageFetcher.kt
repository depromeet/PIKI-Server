package com.depromeet.piki.product.service.http

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.domain.ProductLinkException
import com.depromeet.piki.product.service.PageContent
import com.depromeet.piki.product.service.PageFetcher
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

// fetch 용 RestClient 와 host→IP 해석(dnsResolver)을 생성자로 주입받는다. 둘 다 밖에서 교체할 수 있어야
// 네트워크 없이 redirect 루프(3xx 따라가기·cross-domain 차단·hop 상한)를 단위 테스트로 검증할 수 있다.
//
// dnsResolver 는 PageFetchHttpClientConfig 의 RestClient 와 같은 인스턴스를 공유한다 — 가드가 검증한 IP 로 실제
// 연결도 이뤄지게(IP pin) 해 DNS rebinding/TOCTOU 를 닫는다. 한 fetch 가 끝나면 clear() 로 캐시를 비운다.
@Component
class HttpPageFetcher(
    private val restClient: RestClient,
    private val dnsResolver: RequestScopedDnsResolver,
) : PageFetcher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetch(link: ProductLink): PageContent =
        try {
            fetchFollowingRedirects(link)
        } finally {
            // 요청 스코프 DNS 캐시를 비워 다음 fetch 와 격리한다(이번 요청이 본 IP 가 다음 요청으로 새지 않게).
            dnsResolver.clear()
        }

    private fun fetchFollowingRedirects(link: ProductLink): PageContent {
        var current = link
        repeat(MAX_REDIRECTS + 1) {
            guardAgainstInternalHost(current)
            val response = request(current)
            // Location 을 가진 진짜 redirect 코드(301/302/303/307/308)만 따라간다.
            // 304 Not Modified·300 Multiple Choices·305 Use Proxy 같은 비-redirect 3xx 는 일반 응답처럼 body 로 처리한다.
            if (response.statusCode.value() in REDIRECT_CODES) {
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
        val location = redirectLocation(current, response)
        // 상대 Location(/path)도 원본 URI 에 resolve 해 절대 URI 로 만든다.
        val target = current.value.resolve(location)
        if (!RedirectPolicy.isSameRegistrableDomain(current.value.host, target.host)) {
            log.warn("[SSRF] blocked: cross-domain redirect url={}", current.safeLogString())
            throw PageFetchException.blockedHost()
        }
        // https 강제(http 다운그레이드 차단)와 형식 검증을 ProductLink.parse 가 한다. 같은 도메인이라도 https 가
        // 아니거나(다운그레이드) 형식이 깨진 redirect 는 따라가지 않는다.
        return try {
            ProductLink.parse(target.toString())
        } catch (e: ProductLinkException) {
            log.warn("link fetch redirect rejected (non-https or malformed) url={}", current.safeLogString())
            throw PageFetchException.blockedHost()
        }
    }

    // Location 헤더를 URI 로 읽는다. 외부 서버가 깨진 Location(잘못된 이스케이프 등)을 주면 Spring 의 getLocation 이
    // URI 파싱 단계에서 IllegalArgumentException 을 던지는데, 우리 버그가 아니라 대상 서버 문제이므로 upstreamError(502)로 귀결시킨다.
    private fun redirectLocation(
        current: ProductLink,
        response: ResponseEntity<String>,
    ): URI {
        val location =
            try {
                response.headers.location
            } catch (e: IllegalArgumentException) {
                log.warn("link fetch malformed Location url={}", current.safeLogString())
                throw PageFetchException.upstreamError(e)
            }
        return location ?: run {
            log.warn("link fetch redirect without Location url={}", current.safeLogString())
            throw PageFetchException.upstreamError(IllegalStateException("redirect without Location"))
        }
    }

    // host 가 외부 라우팅 불가 주소(loopback/사설/링크로컬/메타데이터/IPv6 ULA)로 resolve 되면 SSRF 위험으로 차단.
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
                dnsResolver.resolve(host)
            } catch (e: java.net.UnknownHostException) {
                log.info("link fetch unknown host url={}", link.safeLogString())
                throw PageFetchException.upstreamError(e)
            }
        if (addresses.any { isInternalAddress(it) }) {
            log.error("[SSRF] blocked: internal address resolved url={}", link.safeLogString())
            throw PageFetchException.blockedHost()
        }
    }

    // 외부 라우팅 불가 주소면 SSRF 위험으로 본다. Java 의 isSiteLocalAddress 는 IPv4 사설만 잡고 IPv6 ULA(fc00::/7)는
    // 못 잡으므로(GCP·Alibaba 등의 IPv6 메타데이터·내부망), 이를 별도로 차단한다.
    internal fun isInternalAddress(addr: InetAddress): Boolean =
        addr.isLoopbackAddress ||
            addr.isAnyLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isLinkLocalAddress ||
            addr.isMulticastAddress ||
            isUniqueLocalIpv6(addr) ||
            // AWS / GCP IPv4 메타데이터(169.254.169.254 는 link-local 이라 위에서 잡히지만 방어적으로 명시).
            addr.hostAddress == "169.254.169.254"

    // IPv6 Unique Local Address(fc00::/7). 첫 바이트의 상위 7비트가 1111110 (0xfc 또는 0xfd)이면 ULA 다.
    private fun isUniqueLocalIpv6(addr: InetAddress): Boolean =
        addr is Inet6Address && (((addr.address.firstOrNull()?.toInt() ?: 0) and 0xfe) == 0xfc)

    companion object {
        // 같은 회사 도메인 안에서의 www↔non-www·http→https 정도라 한두 번이면 충분. 무한·체인 redirect 는 여기서 끊는다.
        private const val MAX_REDIRECTS = 3

        // Location 을 가진 진짜 redirect 상태 코드. 304/300/305 등 다른 3xx 는 따라가지 않는다.
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        // LLM 토큰 비용 상한. 대형 쇼핑몰(쿠팡 등)은 1MB 가 넘는 경우도 있음.
        private const val MAX_HTML_CHARS = 200_000
    }
}
