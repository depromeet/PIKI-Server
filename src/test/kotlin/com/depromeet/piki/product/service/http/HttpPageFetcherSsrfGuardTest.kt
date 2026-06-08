package com.depromeet.piki.product.service.http

import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// SSRF 가드의 internal-address 판정을 검증한다. redirect 가 매 hop 새 host 를 허용하므로 이 판정이 보안의 최종 방어선이다.
// 특히 IPv6 ULA(fc00::/7)는 Java 의 isSiteLocalAddress 가 못 잡아 별도로 막는다.
class HttpPageFetcherSsrfGuardTest {
    private val fetcher = HttpPageFetcher(PageFetchHttpClientConfig().pageFetchRestClient(ObservationRegistry.NOOP))

    @ParameterizedTest
    @ValueSource(
        strings = [
            "127.0.0.1", // loopback
            "10.0.0.1", // 사설 A
            "192.168.0.1", // 사설 C
            "172.16.0.1", // 사설 B
            "169.254.0.1", // link-local
            "169.254.169.254", // 클라우드 메타데이터
            "0.0.0.0", // any-local
            "::1", // IPv6 loopback
            "fc00::1", // IPv6 ULA
            "fd00:ec2::254", // IPv6 ULA (클라우드 IPv6 메타데이터 대역)
        ],
    )
    fun `내부·메타데이터 주소는 차단된다`(ip: String) {
        assertTrue(fetcher.isInternalAddress(InetAddress.getByName(ip)), "$ip 는 차단되어야 함")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "8.8.8.8",
            "1.1.1.1",
            "93.184.216.34",
            "2606:4700:4700::1111", // 공인 IPv6 (Cloudflare)
        ],
    )
    fun `공인 라우팅 가능 주소는 허용된다`(ip: String) {
        assertFalse(fetcher.isInternalAddress(InetAddress.getByName(ip)), "$ip 는 허용되어야 함")
    }
}
