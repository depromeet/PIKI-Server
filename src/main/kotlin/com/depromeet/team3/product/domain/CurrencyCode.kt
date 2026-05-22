package com.depromeet.team3.product.domain

// 통화 코드 정규화. LLM 추출값은 "krw" · " KRW " · "원" · "$" 처럼 형식이 제각각이라,
// ISO 4217 3자리 대문자로 정규화하고 형식에 안 맞으면 null 로 떨어뜨려 가격의 통화를
// 신뢰 가능한 형태로만 저장한다. URL 추출·OCR 추출이 공유하는 방어 지점.
object CurrencyCode {
    private val ISO_4217_FORMAT = Regex("^[A-Z]{3}$")

    fun normalizeOrNull(raw: String?): String? {
        val normalized = raw?.trim()?.uppercase() ?: return null
        return normalized.takeIf { ISO_4217_FORMAT.matches(it) }
    }
}
