package com.depromeet.piki.product.service.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 같은 회사 도메인(eTLD+1) 판정은 redirect 추적의 핵심 보안 로직이라 순수 함수로 망라한다.
// guava InternetDomainName(Public Suffix List) 기반이라 .co.kr 처럼 eTLD 가 여러 레이블인 경우도 정확히 가른다.
class RedirectPolicyTest {
    @ParameterizedTest
    @CsvSource(
        "www.zigzag.kr, zigzag.kr",
        "zigzag.kr, www.zigzag.kr",
        "zigzag.kr, zigzag.kr",
        "m.29cm.co.kr, 29cm.co.kr",
        "shop.musinsa.com, musinsa.com",
    )
    fun `같은 회사 도메인이면 true`(
        from: String,
        to: String,
    ) {
        assertTrue(RedirectPolicy.isSameRegistrableDomain(from, to))
    }

    @ParameterizedTest
    @CsvSource(
        "zigzag.kr, evil.com",
        "www.zigzag.kr, zigzag.com",
        // .co.kr 은 eTLD 가 두 레이블 — 단순 점 개수 어림이면 같은 회사로 오판할 수 있는 SSRF 함정.
        "a.co.kr, b.co.kr",
    )
    fun `다른 회사 도메인이면 false`(
        from: String,
        to: String,
    ) {
        assertFalse(RedirectPolicy.isSameRegistrableDomain(from, to))
    }

    @Test
    fun `host 가 null 이면 false`() {
        assertFalse(RedirectPolicy.isSameRegistrableDomain(null, "zigzag.kr"))
        assertFalse(RedirectPolicy.isSameRegistrableDomain("zigzag.kr", null))
    }

    @Test
    fun `public suffix 자체(co_kr)는 등록 도메인이 아니라 false`() {
        assertFalse(RedirectPolicy.isSameRegistrableDomain("co.kr", "co.kr"))
    }

    @Test
    fun `IP 주소는 도메인이 아니라 false`() {
        assertFalse(RedirectPolicy.isSameRegistrableDomain("1.2.3.4", "1.2.3.4"))
    }
}
