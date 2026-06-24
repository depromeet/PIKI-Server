package com.depromeet.piki.announcement.domain

import kotlin.test.Test
import kotlin.test.assertEquals

// 본문 마크다운 이미지 URL 추출·치환 단위테스트(#561). Spring·DB 없이 순수 함수만 검증한다.
class AnnouncementBodyImagesTest {
    @Test
    fun `이미지 URL 들을 등장 순서대로 추출한다`() {
        val body =
            """
            # 제목
            ![첫 이미지](https://ext.example.com/a.png)
            본문 텍스트
            ![](https://ext.example.com/b.gif)
            """.trimIndent()

        assertEquals(
            listOf("https://ext.example.com/a.png", "https://ext.example.com/b.gif"),
            AnnouncementBodyImages.urls(body),
        )
    }

    @Test
    fun `title 이 붙은 이미지도 url 만 추출한다`() {
        val body = """![로고](https://ext.example.com/logo.webp "PiKi 로고")"""
        assertEquals(listOf("https://ext.example.com/logo.webp"), AnnouncementBodyImages.urls(body))
    }

    @Test
    fun `이미지가 없으면 빈 목록이다`() {
        assertEquals(emptyList(), AnnouncementBodyImages.urls("이미지 없는 **마크다운** 본문\n[링크](https://ext.example.com)"))
    }

    @Test
    fun `링크는 이미지로 잡지 않는다`() {
        // `[text](url)` 은 링크, `![text](url)` 만 이미지다.
        assertEquals(emptyList(), AnnouncementBodyImages.urls("[자세히](https://ext.example.com/page)"))
    }

    @Test
    fun `transform 으로 특정 URL 만 치환하고 나머지는 둔다`() {
        val body =
            """
            ![a](https://ext.example.com/a.png)
            ![b](https://cdn.piki.day/announcement/1/x.png)
            """.trimIndent()

        val rewritten =
            AnnouncementBodyImages.rewrite(body) { url ->
                if (url == "https://ext.example.com/a.png") "https://cdn.piki.day/announcement/1/new.png" else null
            }

        assertEquals(
            """
            ![a](https://cdn.piki.day/announcement/1/new.png)
            ![b](https://cdn.piki.day/announcement/1/x.png)
            """.trimIndent(),
            rewritten,
        )
    }

    @Test
    fun `alt 텍스트가 url 과 같아도 url 위치만 치환한다`() {
        val body = "![https://ext.example.com/a.png](https://ext.example.com/a.png)"
        val rewritten = AnnouncementBodyImages.rewrite(body) { "https://cdn.piki.day/x.png" }
        assertEquals("![https://ext.example.com/a.png](https://cdn.piki.day/x.png)", rewritten)
    }

    @Test
    fun `title 이 붙은 이미지도 url 만 치환하고 title 은 보존한다`() {
        val body = """![로고](https://ext.example.com/logo.webp "PiKi 로고")"""
        val rewritten = AnnouncementBodyImages.rewrite(body) { "https://cdn.piki.day/announcement/1/logo.webp" }
        assertEquals("""![로고](https://cdn.piki.day/announcement/1/logo.webp "PiKi 로고")""", rewritten)
    }
}
