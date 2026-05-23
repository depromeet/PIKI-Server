package com.depromeet.piki.user.domain

enum class IdentityType(
    val description: String,
) {
    GUEST("게스트"),
    MEMBER("회원"),
}
