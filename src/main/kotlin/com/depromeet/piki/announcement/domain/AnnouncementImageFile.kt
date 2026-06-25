package com.depromeet.piki.announcement.domain

/**
 * 공지 본문에 들어가는 이미지 파일 값 객체(#561 rehost).
 *
 * 빈 바이트·미지원 MIME·선언과 실제 내용(매직바이트) 불일치를 [of] 팩토리에서 [AnnouncementImageException] 으로 차단해,
 * 저장 어댑터(ImageStorage)는 항상 유효한 이미지만 받는다는 것을 시그니처로 보장한다.
 *
 * ProfileImageFile 과 달리 **gif 를 허용**한다 — 공지(패치노트)는 애니메이션 데모를 담을 수 있다.
 * heic/heif 는 웹에서 붙여넣는 외부 이미지로는 드물어 제외하고, svg 는 XSS 벡터라 제외한다.
 * 프로필 허용 정책과 독립이라 별도 값 객체로 둔다(한쪽이 바뀌어도 끌고 가지 않게).
 */
class AnnouncementImageFile private constructor(
    private val rawBytes: ByteArray,
    val mimeType: String,
) {
    // ByteArray 는 가변이라 방어적 복사본을 노출한다.
    val bytes: ByteArray
        get() = rawBytes.copyOf()

    // S3 object key 확장자. of() 가 SUPPORTED_MIME_TYPES 를 보장하므로 else 는 도달 불가한 불변식 위반이다.
    val extension: String
        get() =
            when (mimeType) {
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                else -> error("지원하지 않는 MIME 타입의 확장자를 요청했다: $mimeType")
            }

    companion object {
        // 공지 본문 이미지로 허용하는 형식. gif(애니메이션) 허용, svg(XSS)·heic 제외.
        val SUPPORTED_MIME_TYPES: Set<String> =
            setOf(
                "image/png",
                "image/jpeg",
                "image/gif",
                "image/webp",
            )

        // 매직바이트 시그니처 — 각 포맷 명세가 박아 둔 불변 상수.
        private val PNG_SIGNATURE = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val JPEG_SIGNATURE = intArrayOf(0xFF, 0xD8, 0xFF)
        private const val GIF = "GIF8" // GIF87a / GIF89a 공통 prefix (offset 0)
        private const val RIFF = "RIFF" // WebP 컨테이너 시작 (offset 0)
        private const val WEBP = "WEBP" // WebP form type (offset 8)

        fun of(
            bytes: ByteArray,
            contentType: String?,
        ): AnnouncementImageFile {
            if (bytes.isEmpty()) throw AnnouncementImageException.malformed()
            // media type 은 대소문자 무관 + `;` 뒤 파라미터가 붙을 수 있다("IMAGE/PNG", "image/gif; charset=..").
            val type =
                contentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                    ?: throw AnnouncementImageException.unsupportedType()
            if (type !in SUPPORTED_MIME_TYPES) throw AnnouncementImageException.unsupportedType()
            // Content-Type 은 위조 가능하므로 실제 바이트 시그니처와 교차검증한다.
            if (!matchesSignature(type, bytes)) throw AnnouncementImageException.malformed()
            return AnnouncementImageFile(bytes.copyOf(), type)
        }

        private fun matchesSignature(
            mimeType: String,
            bytes: ByteArray,
        ): Boolean =
            when (mimeType) {
                "image/png" -> bytes.startsWith(PNG_SIGNATURE)
                "image/jpeg" -> bytes.startsWith(JPEG_SIGNATURE)
                // GIF8 + ('7'|'9') + 'a' → GIF87a / GIF89a
                "image/gif" ->
                    bytes.asciiAt(0, GIF) &&
                        bytes.size >= 6 &&
                        (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
                        bytes[5] == 'a'.code.toByte()
                "image/webp" -> bytes.asciiAt(0, RIFF) && bytes.asciiAt(8, WEBP)
                else -> false // of() 가 화이트리스트를 보장하므로 도달 불가
            }

        private fun ByteArray.startsWith(signature: IntArray): Boolean {
            if (size < signature.size) return false
            return signature.indices.all { this[it] == signature[it].toByte() }
        }

        private fun ByteArray.asciiAt(
            offset: Int,
            ascii: String,
        ): Boolean {
            if (size < offset + ascii.length) return false
            return ascii.indices.all { this[offset + it] == ascii[it].code.toByte() }
        }
    }
}
