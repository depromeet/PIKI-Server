package com.depromeet.piki.announcement.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 공지 이미지 값 객체 검증 단위테스트(#561) — 형식 화이트리스트·매직바이트 교차검증·gif 허용.
class AnnouncementImageFileTest {
    private fun bytesOf(vararg ints: Int) = ByteArray(ints.size) { ints[it].toByte() }

    // 각 포맷의 유효한 최소 매직바이트 (뒤는 padding)
    private val png = bytesOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
    private val jpeg = bytesOf(0xFF, 0xD8, 0xFF, 0xE0, 0, 0)
    private val gif89 = "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0)
    private val gif87 = "GIF87a".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0)
    private val webp = "RIFF".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray(Charsets.US_ASCII)

    @Test
    fun `png jpeg gif webp 는 허용되고 확장자가 매핑된다`() {
        assertEquals("png", AnnouncementImageFile.of(png, "image/png").extension)
        assertEquals("jpg", AnnouncementImageFile.of(jpeg, "image/jpeg").extension)
        assertEquals("gif", AnnouncementImageFile.of(gif89, "image/gif").extension)
        assertEquals("gif", AnnouncementImageFile.of(gif87, "image/gif").extension)
        assertEquals("webp", AnnouncementImageFile.of(webp, "image/webp").extension)
    }

    @Test
    fun `content-type 에 파라미터가 붙고 대문자여도 정규화해 허용한다`() {
        assertEquals("gif", AnnouncementImageFile.of(gif89, "IMAGE/GIF; charset=binary").extension)
    }

    @Test
    fun `svg 는 거부한다 - XSS 벡터라 제외`() {
        assertFailsWith<AnnouncementImageException> {
            AnnouncementImageFile.of("<svg></svg>".toByteArray(), "image/svg+xml")
        }
    }

    @Test
    fun `content-type 이 없으면 거부한다`() {
        assertFailsWith<AnnouncementImageException> { AnnouncementImageFile.of(png, null) }
    }

    @Test
    fun `빈 바이트는 거부한다`() {
        assertFailsWith<AnnouncementImageException> { AnnouncementImageFile.of(ByteArray(0), "image/png") }
    }

    @Test
    fun `선언한 형식과 실제 매직바이트가 다르면 거부한다`() {
        // gif 라고 선언했지만 실제 바이트는 png
        assertFailsWith<AnnouncementImageException> { AnnouncementImageFile.of(png, "image/gif") }
    }

    @Test
    fun `gif 시그니처가 GIF87a GIF89a 가 아니면 거부한다`() {
        val fakeGif = "GIF88a".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0)
        assertFailsWith<AnnouncementImageException> { AnnouncementImageFile.of(fakeGif, "image/gif") }
    }
}
