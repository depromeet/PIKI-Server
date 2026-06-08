package com.depromeet.piki.product.service.http

import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.test.assertEquals

// 요청 스코프 DNS 캐시가 "한 fetch 동안 host 당 실제 조회 1회"를 보장하는지 검증한다.
// 이게 IP pin 의 핵심 계약 — 가드와 연결이 같은 IP 를 보게 해 DNS rebinding(두 조회가 다른 IP)을 닫는다.
class RequestScopedDnsResolverTest {
    @Test
    fun `같은 host 를 여러 번 resolve 해도 실제 조회는 한 번뿐이다`() {
        var calls = 0
        val resolver =
            RequestScopedDnsResolver {
                calls++
                arrayOf(InetAddress.getByName("93.184.216.34"))
            }

        resolver.resolve("zigzag.kr")
        resolver.resolve("zigzag.kr")
        resolver.resolve("zigzag.kr")

        assertEquals(1, calls, "한 요청 안에서는 host 당 한 번만 실제 DNS 를 타야 가드·연결이 같은 IP 를 본다")
    }

    @Test
    fun `clear 후에는 다시 실제 조회한다`() {
        var calls = 0
        val resolver =
            RequestScopedDnsResolver {
                calls++
                arrayOf(InetAddress.getByName("93.184.216.34"))
            }

        resolver.resolve("zigzag.kr")
        resolver.clear()
        resolver.resolve("zigzag.kr")

        assertEquals(2, calls, "clear 로 요청이 격리되어야 다음 fetch 가 새로 조회한다")
    }

    @Test
    fun `다른 host 는 각각 실제 조회한다`() {
        var calls = 0
        val resolver =
            RequestScopedDnsResolver {
                calls++
                arrayOf(InetAddress.getByName("93.184.216.34"))
            }

        resolver.resolve("zigzag.kr")
        resolver.resolve("29cm.co.kr")

        assertEquals(2, calls)
    }
}
