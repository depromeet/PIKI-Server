package com.depromeet.piki.auth.infrastructure.oauth

data class OAuthUserInfo(
    val provider: OAuthProvider,
    val socialId: String,
    // provider 가 프로필 이미지를 안 주거나(사용자 동의 거부 등) 빈 값이면 null.
    // 소비측(소셜 가입)에서 null 이면 기본 이미지로 대체한다.
    val profileImage: String?,
    // 소셜에서 수집한 email (#442·#451, 마케팅·알림·복구용). 구글·애플·카카오 모두 파싱한다(기본값 null).
    // 미동의(구글·카카오)·애플 2회차 미제공·카카오 미인증/휴면 시 null. 소비측이 user_details 에 upsert 한다.
    val email: String? = null,
)
