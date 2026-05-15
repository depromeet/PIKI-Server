package com.depromeet.team3.auth.infrastructure.oauth.kakao.dto

data class KakaoUserInfoResponse(
    val id: Long,
    val kakao_account: KakaoAccount,
) {
    data class KakaoAccount(
        val email: String = "",
        val profile: Profile = Profile(),
    ) {
        data class Profile(
            val profile_image_url: String = "",
        )
    }
}
