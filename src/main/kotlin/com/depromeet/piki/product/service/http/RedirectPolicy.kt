package com.depromeet.piki.product.service.http

import com.google.common.net.InternetDomainName

// redirect 를 따라가도 되는지 판정한다. SSRF 방어상 "같은 회사 도메인(eTLD+1)" 안에서의 redirect 만 허용한다
// (예: www.zigzag.kr ↔ zigzag.kr, m.29cm.co.kr ↔ 29cm.co.kr). 다른 회사 도메인으로의 점프는 차단한다.
//
// 같은 회사 도메인 판정은 Public Suffix List 가 필요해 guava InternetDomainName 을 쓴다. 단순 점 개수 어림(뒤 2개)은
// .co.kr 처럼 eTLD 가 여러 레이블인 경우 a.co.kr 과 b.co.kr 을 같은 회사로 오판해 SSRF 구멍을 만든다.
object RedirectPolicy {
    fun isSameRegistrableDomain(
        from: String?,
        to: String?,
    ): Boolean {
        val a = registrableDomainOrNull(from) ?: return false
        val b = registrableDomainOrNull(to) ?: return false
        return a == b
    }

    // host 의 eTLD+1(등록 가능한 최상위 사적 도메인). IP·public suffix 자체(co.kr 등)·구문 오류면 예외 → null(불허).
    private fun registrableDomainOrNull(host: String?): String? {
        host ?: return null
        return runCatching { InternetDomainName.from(host).topPrivateDomain().toString() }.getOrNull()
    }
}
