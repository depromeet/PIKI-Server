package com.depromeet.piki.common.imageproxy

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ImageProxyFetcherTest {
    // test.invalid 는 RFC 6761 에서 DNS 가 항상 NXDOMAIN 을 반환하도록 예약된 TLD.
    // 외부망 없이 결정론적으로 실패하므로 "도메인 체크 통과 후 fetchFailed" 케이스에 사용한다.
    private val fetcher =
        DefaultImageProxyFetcher(
            ImageProxyProperties(
                allowedDomains = listOf("msscdn.net", "test.invalid"),
                maxBytes = 5 * 1024 * 1024,
                timeoutSeconds = 3,
            ),
        )

    @Test
    fun `허용되지 않은 도메인이면 blockedDomain 예외를 던진다`() {
        assertFailsWith<ImageProxyException> {
            fetcher.fetch("https://evil.com/image.jpg")
        }
    }

    @Test
    fun `http 스킴이면 blockedDomain 예외를 던진다`() {
        assertFailsWith<ImageProxyException> {
            fetcher.fetch("http://msscdn.net/image.jpg")
        }
    }

    @Test
    fun `file 스킴이면 blockedDomain 예외를 던진다`() {
        assertFailsWith<ImageProxyException> {
            fetcher.fetch("file:///etc/passwd")
        }
    }

    @Test
    fun `URL 형식이 잘못되면 blockedDomain 예외를 던진다`() {
        assertFailsWith<ImageProxyException> {
            fetcher.fetch("not-a-valid-url")
        }
    }

    @Test
    fun `host 가 없는 URL 이면 blockedDomain 예외를 던진다`() {
        assertFailsWith<ImageProxyException> {
            fetcher.fetch("https:///image.jpg")
        }
    }

    @Test
    fun `허용 도메인의 서브도메인은 통과되고 네트워크 오류는 fetchFailed 예외를 던진다`() {
        // sub.test.invalid → allowedDomains 에 test.invalid 가 있으므로 서브도메인 체크 통과.
        // .invalid TLD 는 RFC 6761 예약 도메인이라 DNS NXDOMAIN 으로 즉시 실패해 fetchFailed() 로 변환됨.
        val ex =
            assertFailsWith<ImageProxyException> {
                fetcher.fetch("https://sub.test.invalid/image.jpg")
            }
        assert(ex.httpStatus.value() == 502) { "fetchFailed 이어야 하지만 ${ex.httpStatus}" }
    }

    @Test
    fun `허용 도메인과 정확히 일치하는 host 는 통과되고 네트워크 오류는 fetchFailed 예외를 던진다`() {
        val ex =
            assertFailsWith<ImageProxyException> {
                fetcher.fetch("https://test.invalid/image.jpg")
            }
        assert(ex.httpStatus.value() == 502) { "fetchFailed 이어야 하지만 ${ex.httpStatus}" }
    }
}
