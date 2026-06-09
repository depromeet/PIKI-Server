package com.depromeet.piki.product.service.http

import org.springframework.stereotype.Component
import java.net.InetAddress

// SSRF 가드와 실제 HTTP 연결이 "같은 IP 를 본다"를 보장하는 요청 스코프 DNS 캐시.
//
// 가드(guardAgainstInternalHost)가 host→IP 를 한 번 조회해 사설/내부 IP 를 차단한 뒤, 실제 연결(HttpClient5 의
// DnsResolver)이 또 조회하면 그 사이 DNS 응답이 바뀔 수 있다(DNS rebinding). 그러면 가드는 공인 IP 로 통과시켰는데
// 연결은 내부 IP 로 가는 TOCTOU 구멍이 생긴다. 이 캐시는 한 fetch 동안 같은 host 를 단 한 번만 실제 조회하고
// 그 결과를 가드와 연결이 공유하게 해, "검증한 그 IP 로만 연결"을 코드 계약으로 박는다.
//
// fetch 한 번이 끝나면 clear() 로 비워 다음 요청과 격리한다. 동기 호출(HttpComponents)이라 가드와 연결이 같은
// 스레드에서 돌므로 ThreadLocal 로 요청 스코프를 표현한다.
@Component
class RequestScopedDnsResolver(
    private val delegate: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) },
) {
    private val cache = ThreadLocal.withInitial { mutableMapOf<String, Array<InetAddress>>() }

    fun resolve(host: String): Array<InetAddress> = cache.get().getOrPut(host) { delegate(host) }

    fun clear() = cache.get().clear()
}
