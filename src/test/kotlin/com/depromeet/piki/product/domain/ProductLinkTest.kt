package com.depromeet.piki.product.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProductLinkTest {
    @Test
    fun `빈 문자열 또는 공백만 있으면 거부한다`() {
        listOf("", "   ", "\t\n").forEach { raw ->
            val ex =
                assertFailsWith<ProductLinkException>("'$raw' 는 거부되어야 함") {
                    ProductLink.parse(raw)
                }
            assertEquals("링크를 입력해 주세요.", ex.message)
        }
    }

    @Test
    fun `https 가 아닌 scheme 은 모두 거부한다`() {
        val cases =
            listOf(
                "http://example.com",
                "ftp://example.com",
                "file:///etc/passwd",
                "javascript:alert(1)",
                "ws://example.com/socket",
            )
        cases.forEach { raw ->
            val ex =
                assertFailsWith<ProductLinkException>("$raw 는 거부되어야 함") {
                    ProductLink.parse(raw)
                }
            assertEquals("https 링크만 등록할 수 있어요.", ex.message)
        }
    }

    @Test
    fun `URI 파싱 자체가 실패하는 raw 는 형식 오류로 거부한다`() {
        // 예: data: URI 의 본문에 illegal character 가 들어가면 URI_create 단계에서 IllegalArgumentException
        val ex =
            assertFailsWith<ProductLinkException> {
                ProductLink.parse("data:text/html,<h1>x</h1>")
            }
        assertEquals("올바른 링크 형식이 아니에요. 다시 확인해 주세요.", ex.message)
    }

    @Test
    fun `예외 메시지에 원본 raw 는 포함되지 않는다`() {
        // GlobalExceptionHandler 가 message 를 응답 detail · 로그에 그대로 박는 구조라
        // 쿼리스트링에 섞일 수 있는 토큰 · 세션이 새지 않도록 message 단계에서 원본을 제거한다.
        val rawWithSecret = "data:text/html,<token=SHOULD_NOT_LEAK>"
        val ex =
            assertFailsWith<ProductLinkException> {
                ProductLink.parse(rawWithSecret)
            }
        assertEquals("올바른 링크 형식이 아니에요. 다시 확인해 주세요.", ex.message)
    }

    @Test
    fun `scheme 없이 호스트만 있는 raw 는 거부한다`() {
        val ex =
            assertFailsWith<ProductLinkException> {
                ProductLink.parse("example.com/product")
            }
        assertEquals("https 링크만 등록할 수 있어요.", ex.message)
    }

    @Test
    fun `대문자 scheme 도 case-insensitive 로 통과한다`() {
        val link = ProductLink.parse("HTTPS://shop.example.com/items/42")
        assertTrue(link.value.scheme.equals("https", ignoreCase = true))
    }

    @Test
    fun `포트 query fragment 가 포함된 URL 은 그대로 보존한 채 통과한다`() {
        val link = ProductLink.parse("https://shop.example.com:8443/p/42?ref=home&q=1#desc")

        assertEquals("shop.example.com", link.value.host)
        assertEquals(8443, link.value.port)
        assertEquals("/p/42", link.value.path)
        assertEquals("ref=home&q=1", link.value.query)
        assertEquals("desc", link.value.fragment)
    }

    @Test
    fun `앞뒤 공백을 제거한 채 parse 된다`() {
        val link = ProductLink.parse("  https://x.com/y  ")
        assertEquals("https://x.com/y", link.value.toString())
    }

    @Test
    fun `같은 raw 입력으로 parse 한 ProductLink 들은 동등하다`() {
        val a = ProductLink.parse("https://x.com/y")
        val b = ProductLink.parse("https://x.com/y")
        assertEquals(a, b)
    }

    @Test
    fun `toString 은 raw URL 을 그대로 노출한다`() {
        val raw = "https://shop.example.com/items/42"
        val link = ProductLink.parse(raw)
        assertEquals(raw, link.toString())
    }
}
