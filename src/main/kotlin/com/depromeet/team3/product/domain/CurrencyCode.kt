package com.depromeet.team3.product.domain

import java.util.Currency

// 통화 코드 정규화. LLM 추출값은 "krw" · " KRW " · "원" · "$" 처럼 형식이 제각각이라,
// trim·대문자화 후 java.util.Currency 로 실제 ISO 4217 코드 집합에 있는지까지 검증한다.
// 형식만 보면 "ZZZ" 같은 가짜도 통과하므로 실존 코드 검증으로 막는다. 안 맞으면 null.
// URL 추출·OCR 추출이 공유하는 방어 지점.
// (uppercase() 는 Kotlin stdlib 함수라 Locale.ROOT 불변 — Java toUpperCase() 와 달리 로케일 비의존.)
object CurrencyCode {
    fun normalizeOrNull(raw: String?): String? {
        val normalized = raw?.trim()?.uppercase() ?: return null
        return runCatching { Currency.getInstance(normalized).currencyCode }.getOrNull()
    }
}
