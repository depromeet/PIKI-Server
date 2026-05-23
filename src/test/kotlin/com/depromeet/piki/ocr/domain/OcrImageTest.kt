package com.depromeet.piki.ocr.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OcrImageTest {

    @ParameterizedTest
    @ValueSource(strings = ["image/png", "image/jpeg", "image/webp", "image/heic", "image/heif"])
    fun `지원하는 MIME 타입이면 OcrImage 가 생성된다`(mimeType: String) {
        val bytes = byteArrayOf(1, 2, 3)

        val image = OcrImage.of(bytes, mimeType)

        assertEquals(mimeType, image.mimeType)
        assertContentEquals(bytes, image.bytes)
    }

    @ParameterizedTest
    @ValueSource(strings = ["IMAGE/JPEG", "Image/Png", "image/jpeg; charset=utf-8", "  image/webp  "])
    fun `대소문자·파라미터가 섞인 MIME 타입도 정규화되어 생성된다`(mimeType: String) {
        val image = OcrImage.of(byteArrayOf(1, 2, 3), mimeType)

        assertEquals(mimeType.substringBefore(';').trim().lowercase(), image.mimeType)
    }

    @Test
    fun `생성에 쓰인 원본 배열을 변경해도 OcrImage 내부는 영향받지 않는다`() {
        val original = byteArrayOf(1, 2, 3)
        val image = OcrImage.of(original, "image/png")

        original[0] = 99

        assertContentEquals(byteArrayOf(1, 2, 3), image.bytes)
    }

    @Test
    fun `bytes 로 반환된 배열을 변경해도 OcrImage 내부는 영향받지 않는다`() {
        val image = OcrImage.of(byteArrayOf(1, 2, 3), "image/png")

        image.bytes[0] = 99

        assertContentEquals(byteArrayOf(1, 2, 3), image.bytes)
    }

    @Test
    fun `빈 바이트 배열이면 예외가 발생한다`() {
        assertFailsWith<IllegalArgumentException> {
            OcrImage.of(ByteArray(0), "image/png")
        }
    }

    @Test
    fun `MIME 타입이 null 이면 예외가 발생한다`() {
        assertFailsWith<IllegalArgumentException> {
            OcrImage.of(byteArrayOf(1), null)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["image/gif", "image/bmp", "image/svg+xml", "application/pdf", "text/plain", ""])
    fun `지원하지 않는 MIME 타입이면 예외가 발생한다`(mimeType: String) {
        assertFailsWith<IllegalArgumentException> {
            OcrImage.of(byteArrayOf(1), mimeType)
        }
    }
}
