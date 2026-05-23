package com.depromeet.piki.auth.infrastructure.oauth.kakao.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoUserInfoResponse(
    val id: Long,
    @JsonProperty("kakao_account") val kakaoAccount: KakaoAccount,
) {
    data class KakaoAccount(
        val email: String = "",
        val profile: Profile = Profile(),
    ) {
        data class Profile(
            @JsonProperty("profile_image_url") val profileImageUrl: String = "",
        )
    }
}
