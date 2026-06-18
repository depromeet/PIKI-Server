package com.depromeet.piki.common.imageproxy

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ImageProxyFetcherTest {
    private val fetcher =
        ImageProxyFetcher(
            ImageProxyProperties(
                allowedDomains = listOf("msscdn.net", "pstatic.net"),
                maxBytes = 5 * 1024 * 1024,
                timeoutSeconds = 10,
            ),
        )

    @Test
    fun `허용되지 않은 도메인이면 blockedDomain 예외를 던진다`() {
        assertFailsWith<ImageProxyException> {
            fetcher.fetch("https://evil.com/image.jpg")
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
        // img.pstatic.net → allowedDomains 에 pstatic.net 이 있으므로 도메인 체크는 통과
        // 실제 fetch 는 네트워크 오류로 fetchFailed() 로 변환됨
        val ex =
            assertFailsWith<ImageProxyException> {
                fetcher.fetch("https://img.pstatic.net/nonexistent.jpg")
            }
        assert(ex.httpStatus.value() == 502) { "fetchFailed 이어야 하지만 ${ex.httpStatus}" }
    }

    @Test
    fun `허용 도메인과 정확히 일치하는 host 는 통과되고 네트워크 오류는 fetchFailed 예외를 던진다`() {
        val ex =
            assertFailsWith<ImageProxyException> {
                fetcher.fetch("https://pstatic.net/nonexistent.jpg")
            }
        assert(ex.httpStatus.value() == 502) { "fetchFailed 이어야 하지만 ${ex.httpStatus}" }
    }
}
