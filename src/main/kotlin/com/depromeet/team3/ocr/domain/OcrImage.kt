package com.depromeet.team3.ocr.domain

/**
 * OCR 분석 대상 이미지를 표현하는 값 객체.
 *
 * 빈 바이트 · 미지정 / 미지원 MIME 타입을 [of] 팩토리에서 차단한다.
 * 입력 형식 검증을 도메인 경계에 모아, GeminiOcrClient 같은 외부 어댑터는
 * 항상 유효한 이미지만 받는다는 것을 시그니처 수준에서 보장한다.
 */
class OcrImage private constructor(
    val bytes: ByteArray,
    val mimeType: String,
) {
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
            val type = requireNotNull(mimeType) { "이미지 타입이 지정되지 않았습니다." }
            require(type in SUPPORTED_MIME_TYPES) { unsupportedMimeTypeMessage(type) }
            return OcrImage(bytes, type)
        }

        // 미지원 형식 예외 메시지를 한 곳에서 만든다. OpenAPI example 의 에러 detail 이
        // 같은 문구를 평문으로 복제하지 않고 이 함수를 참조하므로, 지원 목록이 바뀌어도
        // 실제 응답과 example 이 같은 소스에서 함께 갱신된다.
        fun unsupportedMimeTypeMessage(mimeType: String): String =
            "지원하지 않는 이미지 형식입니다: $mimeType (지원: ${SUPPORTED_MIME_TYPES.joinToString()})"
    }
}
