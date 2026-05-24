package com.depromeet.piki.ocr.domain

/**
 * OCR 분석 대상 이미지를 표현하는 값 객체.
 *
 * 빈 바이트 · 미지정 / 미지원 MIME 타입을 [of] 팩토리에서 차단한다.
 * 입력 형식 검증을 도메인 경계에 모아, GeminiProductImageExtractor 같은 외부 어댑터는
 * 항상 유효한 이미지만 받는다는 것을 시그니처 수준에서 보장한다.
 *
 * 현재는 업로드된 이미지 바이트를 메모리에 그대로 보관한다.
 * 추후 이미지 저장 방식이 S3 경로 참조로 대체될 수 있다.
 */
class OcrImage private constructor(
    private val rawBytes: ByteArray,
    val mimeType: String,
) {
    // ByteArray 는 가변이므로 방어적 복사본을 노출한다.
    // 호출자가 받은 배열을 변경해도 OcrImage 내부 상태는 불변으로 유지된다.
    val bytes: ByteArray
        get() = rawBytes.copyOf()

    companion object {
        // OCR 이 받아들이는 이미지 형식. Gemini Vision 지원 목록 기준.
        // https://ai.google.dev/gemini-api/docs/vision
        val SUPPORTED_MIME_TYPES: Set<String> =
            setOf(
                "image/png",
                "image/jpeg",
                "image/webp",
                "image/heic",
                "image/heif",
            )

        fun of(
            bytes: ByteArray,
            mimeType: String?,
        ): OcrImage {
            require(bytes.isNotEmpty()) { "빈 이미지 파일은 처리할 수 없습니다." }
            // media type 은 RFC 상 대소문자를 가리지 않고 `;` 뒤에 파라미터가 붙을 수 있다.
            // (예: "IMAGE/JPEG", "image/jpeg; charset=utf-8") 정규화 후 비교한다.
            val type =
                requireNotNull(mimeType) { "이미지 타입이 지정되지 않았습니다." }
                    .substringBefore(';')
                    .trim()
                    .lowercase()
            require(type in SUPPORTED_MIME_TYPES) { unsupportedMimeTypeMessage(type) }
            return OcrImage(bytes.copyOf(), type)
        }

        // 미지원 형식 예외 메시지를 한 곳에서 만든다. OpenAPI example 의 에러 detail 이
        // 같은 문구를 평문으로 복제하지 않고 이 함수를 참조하므로, 지원 목록이 바뀌어도
        // 실제 응답과 example 이 같은 소스에서 함께 갱신된다.
        fun unsupportedMimeTypeMessage(mimeType: String): String =
            "지원하지 않는 이미지 형식입니다: $mimeType (지원: ${SUPPORTED_MIME_TYPES.joinToString()})"
    }
}
