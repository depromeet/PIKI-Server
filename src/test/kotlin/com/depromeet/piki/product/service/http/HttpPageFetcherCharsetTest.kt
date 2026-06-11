package com.depromeet.piki.product.service.http

import com.depromeet.piki.product.domain.ProductLink
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.InetAddress
import kotlin.test.assertTrue

// 응답 charset 디코딩 검증: 응답 Content-Type charset → HTML meta charset → UTF-8 순.
// RestClient 의 기본 String 변환은 Content-Type 에 charset 이 없으면 ISO-8859-1 로 떨어져 UTF-8 한글이 깨졌다(카카오).
// 바이트로 받아 직접 charset 을 정해 디코딩하는지 본다. 네트워크 없이 MockRestServiceServer 로 응답을 제어한다.
class HttpPageFetcherCharsetTest {
    // 모든 host 를 공인 IP 로 해석해 SSRF 가드를 통과시킨다(여기선 charset 디코딩만 검증).
    private val publicIp: (String) -> Array<InetAddress> = { arrayOf(InetAddress.getByName("93.184.216.34")) }

    private fun fetcherReturning(
        bytes: ByteArray,
        contentType: MediaType,
    ): HttpPageFetcher {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://shop.example.com/p")).andRespond(withSuccess(bytes, contentType))
        return HttpPageFetcher(builder.build(), RequestScopedDnsResolver(publicIp))
    }

    @Test
    fun `Content-Type 에 charset 이 없는 UTF-8 페이지의 한글이 깨지지 않는다`() {
        // 카카오류: UTF-8 인데 Content-Type 에 charset 이 없는 페이지. 헤더 charset null → meta 없음 → UTF-8 폴백.
        val html = """<html><head><meta property="og:title" content="나이키 운동화"></head><body></body></html>"""
        val fetcher = fetcherReturning(html.toByteArray(Charsets.UTF_8), MediaType.TEXT_HTML)

        val page = fetcher.fetch(ProductLink.parse("https://shop.example.com/p"))

        assertTrue(page.html.contains("나이키 운동화"), "charset 없는 UTF-8 응답이 UTF-8 로 디코딩돼 한글이 보존돼야 한다")
    }

    @Test
    fun `Content-Type 의 charset(EUC-KR)을 따라 디코딩한다`() {
        val euckr = charset("EUC-KR")
        val html = "<html><body>운동화</body></html>"
        val fetcher = fetcherReturning(html.toByteArray(euckr), MediaType.parseMediaType("text/html;charset=EUC-KR"))

        val page = fetcher.fetch(ProductLink.parse("https://shop.example.com/p"))

        assertTrue(page.html.contains("운동화"), "응답 Content-Type 의 EUC-KR charset 으로 디코딩돼야 한다")
    }

    @Test
    fun `Content-Type 에 charset 이 없으면 HTML meta charset(EUC-KR)을 따른다`() {
        val euckr = charset("EUC-KR")
        val html = """<html><head><meta charset="euc-kr"></head><body>장바구니</body></html>"""
        // 헤더에 charset 없음(text/html) → HTML meta 의 euc-kr 을 감지해 디코딩해야 한다.
        val fetcher = fetcherReturning(html.toByteArray(euckr), MediaType.TEXT_HTML)

        val page = fetcher.fetch(ProductLink.parse("https://shop.example.com/p"))

        assertTrue(page.html.contains("장바구니"), "헤더 charset 이 없으면 HTML meta charset(euc-kr)으로 디코딩돼야 한다")
    }
}
