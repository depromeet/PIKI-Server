package com.depromeet.piki.admin.announcement

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// SSRF IP 차단 분류기 단위테스트(#561). 리터럴 IP 는 DNS 조회 없이 InetAddress 로 파싱되므로 결정적이다.
class AnnouncementImageFetcherTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "127.0.0.1", // loopback
            "0.0.0.0", // anyLocal
            "10.0.0.1", // private 10/8
            "172.16.5.4", // private 172.16/12
            "192.168.1.10", // private 192.168/16
            "169.254.169.254", // link-local (AWS IMDS)
            "224.0.0.1", // multicast
            "::1", // IPv6 loopback
            "fe80::1", // IPv6 link-local
            "fc00::1", // IPv6 unique-local
            "fd12:3456::1", // IPv6 unique-local
        ],
    )
    fun `사설·내부 주소는 차단한다`(ip: String) {
        assertTrue(isBlockedImageHostAddress(InetAddress.getByName(ip)), "차단되어야 함: $ip")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "8.8.8.8", // public IPv4
            "1.1.1.1", // public IPv4
            "93.184.216.34", // public IPv4 (example.com)
            "2606:4700:4700::1111", // public IPv6 (Cloudflare)
        ],
    )
    fun `공개 주소는 허용한다`(ip: String) {
        assertFalse(isBlockedImageHostAddress(InetAddress.getByName(ip)), "허용되어야 함: $ip")
    }
}
