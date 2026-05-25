package com.depromeet.piki.auth.infrastructure.jwt

// claimValue 를 enum 이름과 분리해 둔다. 영구 토큰이 발급된 상태에서 코드 측 enum 이름을
// 리팩터링해도 기존 토큰의 type claim 과 호환이 유지되도록 안정성을 보장.
enum class TokenType(
    val claimValue: String,
) {
    ACCESS("access"),
    REFRESH("refresh"),
    ;

    companion object {
        fun fromClaim(value: String?): TokenType? = entries.firstOrNull { it.claimValue == value }
    }
}
