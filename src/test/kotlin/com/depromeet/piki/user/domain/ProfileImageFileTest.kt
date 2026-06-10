package com.depromeet.piki.user.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfileImageFileTest {
    @ParameterizedTest
    @MethodSource("validImages")
    fun `지원 형식은 시그니처가 맞으면 통과하고 확장자를 매핑한다`(
        mimeType: String,
        bytes: ByteArray,
        expectedExtension: String,
    ) {
        val image = ProfileImageFile.of(bytes, mimeType)

        assertEquals(mimeType, image.mimeType)
        assertEquals(expectedExtension, image.extension)
    }

    @ParameterizedTest
    @CsvSource(
        "'IMAGE/JPEG', image/jpeg",
        "'image/jpeg; charset=utf-8', image/jpeg",
        "'image/JPEG', image/jpeg",
    )
    fun `MIME 타입의 대소문자와 파라미터를 정규화한다`(
        raw: String,
        expected: String,
    ) {
        assertEquals(expected, ProfileImageFile.of(JPEG, raw).mimeType)
    }

    @Test
    fun `MIME 타입 앞뒤 공백을 제거한다`() {
        assertEquals("image/jpeg", ProfileImageFile.of(JPEG, "  image/jpeg  ").mimeType)
    }

    @ParameterizedTest
    @ValueSource(strings = ["image/gif", "image/svg+xml", "image/bmp", "image/tiff", "application/pdf", "text/plain"])
    fun `미지원 형식은 시그니처와 무관하게 UserException 을 던진다`(mimeType: String) {
        // 바디가 유효한 JPEG 라도 화이트리스트에서 먼저 걸린다.
        assertFailsWith<UserException> { ProfileImageFile.of(JPEG, mimeType) }
    }

    @Test
    fun `contentType 이 null 이면 UserException 을 던진다`() {
        assertFailsWith<UserException> { ProfileImageFile.of(JPEG, null) }
    }

    @Test
    fun `빈 바이트는 UserException 을 던진다`() {
        assertFailsWith<UserException> { ProfileImageFile.of(ByteArray(0), "image/jpeg") }
    }

    @Test
    fun `선언한 MIME 과 실제 시그니처가 어긋나면 UserException 을 던진다`() {
        // image/png 로 선언했지만 실제 바이트는 JPEG 시그니처 (Content-Type 위조 시나리오).
        assertFailsWith<UserException> { ProfileImageFile.of(JPEG, "image/png") }
    }

    @Test
    fun `이미지 시그니처가 없는 바이트는 UserException 을 던진다`() {
        val notAnImage = ByteArray(16) { it.toByte() }
        assertFailsWith<UserException> { ProfileImageFile.of(notAnImage, "image/png") }
    }

    // 합성 시그니처가 아니라 실제 인코딩된 파일로 검증한다. 특히 HEIC 의 실제 brand(heic)·WebP 의 RIFF/WEBP 가
    // 매직바이트 교차검증을 통과하는지 — 화이트리스트가 정상 파일을 거부하지 않음을 보장하는 회귀 가드.
    @ParameterizedTest
    @CsvSource(
        "test-product.png, image/png, png",
        "test-product.jpg, image/jpeg, jpg",
        "test-profile.webp, image/webp, webp",
        "test-profile.heic, image/heic, heic",
    )
    fun `실제 인코딩된 이미지 파일은 매직바이트 교차검증을 통과한다`(
        resourceName: String,
        mimeType: String,
        expectedExtension: String,
    ) {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/$resourceName")).readBytes()

        val image = ProfileImageFile.of(bytes, mimeType)

        assertEquals(mimeType, image.mimeType)
        assertEquals(expectedExtension, image.extension)
    }

    @Test
    fun `bytes 는 방어적 복사본을 반환해 외부 변경이 내부 상태를 깨지 않는다`() {
        val image = ProfileImageFile.of(JPEG, "image/jpeg")

        image.bytes[0] = 0

        assertEquals(JPEG[0], image.bytes[0])
    }

    companion object {
        private fun bytesOf(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

        private fun ascii(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)

        private val PNG = bytesOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        private val JPEG = bytesOf(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0)
        private val WEBP = ascii("RIFF") + bytesOf(0, 0, 0, 0) + ascii("WEBP") + bytesOf(0, 0, 0, 0)
        private val HEIC = bytesOf(0, 0, 0, 0x20) + ascii("ftyp") + ascii("heic") + bytesOf(0, 0, 0, 0)
        private val HEIF = bytesOf(0, 0, 0, 0x20) + ascii("ftyp") + ascii("mif1") + bytesOf(0, 0, 0, 0)

        @JvmStatic
        fun validImages(): List<Arguments> =
            listOf(
                Arguments.of("image/png", PNG, "png"),
                Arguments.of("image/jpeg", JPEG, "jpg"),
                Arguments.of("image/webp", WEBP, "webp"),
                Arguments.of("image/heic", HEIC, "heic"),
                Arguments.of("image/heif", HEIF, "heif"),
            )
    }
}
