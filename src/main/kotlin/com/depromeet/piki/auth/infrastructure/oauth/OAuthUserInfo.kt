package com.depromeet.piki.auth.infrastructure.oauth

data class OAuthUserInfo(
    val provider: OAuthProvider,
    val socialId: String,
    // provider 가 프로필 이미지를 안 주거나(사용자 동의 거부 등) 빈 값이면 null.
    // 소비측(소셜 가입)에서 null 이면 기본 이미지로 대체한다.
    val profileImage: String?,
    // 소셜에서 수집한 email (#442, 마케팅·알림·복구용). 구글·애플만 파싱하고 카카오는 미수집(기본값 null).
    // 애플 Private Relay 거부·2회차 미제공 시에도 null. 소비측이 user_details 에 upsert 한다.
    val email: String? = null,
)
