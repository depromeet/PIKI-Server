package com.depromeet.piki.auth.infrastructure.oauth.kakao.dto

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.kakao.dto.KakaoUserInfoResponse.KakaoAccount
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.stream.Stream
import kotlin.test.assertEquals

class KakaoUserInfoResponseTest {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private fun response(
        email: String? = null,
        isEmailValid: Boolean = false,
        isEmailVerified: Boolean = false,
        profileImageUrl: String = "https://img/p.png",
    ) = KakaoUserInfoResponse(
        id = 123L,
        kakaoAccount =
            KakaoAccount(
                profile = KakaoAccount.Profile(profileImageUrl = profileImageUrl),
                email = email,
                isEmailValid = isEmailValid,
                isEmailVerified = isEmailVerified,
            ),
    )

    // 유효(미휴면)·인증된 email 만 수집하고, 그 외(부재·빈값·휴면·미인증·조합)는 모두 null.
    @ParameterizedTest(name = "[{index}] {4}")
    @MethodSource("emailGatingCases")
    fun `email 게이팅 - 유효·인증된 값만 채우고 그 외는 null`(
        email: String?,
        isEmailValid: Boolean,
        isEmailVerified: Boolean,
        expected: String?,
        @Suppress("UNUSED_PARAMETER") description: String,
    ) {
        val result = response(email = email, isEmailValid = isEmailValid, isEmailVerified = isEmailVerified).toOAuthUserInfo()

        assertEquals(expected, result.email)
    }

    @Test
    fun `유효하고 인증된 응답은 provider·socialId·profileImage·email 을 모두 매핑한다`() {
        val result = response(email = "user@kakao.com", isEmailValid = true, isEmailVerified = true).toOAuthUserInfo()

        assertEquals(OAuthProvider.KAKAO, result.provider)
        assertEquals("123", result.socialId)
        assertEquals("https://img/p.png", result.profileImage)
        assertEquals("user@kakao.com", result.email)
    }

    @Test
    fun `profile_image_url 이 빈 문자열이면 profileImage 는 null 이다`() {
        assertEquals(null, response(profileImageUrl = "").toOAuthUserInfo().profileImage)
    }

    // ---------- 실제 /v2/user/me JSON 역직렬화 (snake_case 키 매핑 + unknown 필드 허용) ----------
    // 단위 테스트가 객체를 직접 만들면 @JsonProperty 키 매핑이 우회된다. 키 이름이 틀어지면 운영에선
    // 조용히 null 이 되는 사각이라, 카카오가 실제로 주는 모양(모델에 없는 필드 포함)으로 역직렬화를 고정한다.

    @Test
    fun `동의·인증된 카카오 응답 JSON 을 역직렬화해 email 을 추출한다`() {
        val json =
            """
            {
              "id": 123456789,
              "connected_at": "2026-06-08T00:00:00Z",
              "kakao_account": {
                "profile_needs_agreement": false,
                "profile": { "profile_image_url": "https://k.kakaocdn.net/img.jpg", "is_default_image": false },
                "has_email": true,
                "email_needs_agreement": false,
                "is_email_valid": true,
                "is_email_verified": true,
                "email": "user@kakao.com"
              }
            }
            """.trimIndent()

        val result = objectMapper.readValue<KakaoUserInfoResponse>(json).toOAuthUserInfo()

        assertEquals(OAuthProvider.KAKAO, result.provider)
        assertEquals("123456789", result.socialId)
        assertEquals("https://k.kakaocdn.net/img.jpg", result.profileImage)
        assertEquals("user@kakao.com", result.email)
    }

    @Test
    fun `email 미동의 카카오 응답 JSON 은 email 이 null 이다`() {
        val json =
            """
            {
              "id": 123456789,
              "kakao_account": {
                "profile": { "profile_image_url": "https://k.kakaocdn.net/img.jpg" },
                "has_email": true,
                "email_needs_agreement": true
              }
            }
            """.trimIndent()

        assertEquals(null, objectMapper.readValue<KakaoUserInfoResponse>(json).toOAuthUserInfo().email)
    }

    companion object {
        @JvmStatic
        fun emailGatingCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("user@kakao.com", true, true, "user@kakao.com", "유효·인증 → 수집"),
                Arguments.of(null, true, true, null, "email 부재(미동의) → null"),
                Arguments.of("", true, true, null, "빈 문자열 → null"),
                Arguments.of("   ", true, true, null, "공백 문자열 → null"),
                Arguments.of("user@kakao.com", false, true, null, "휴면(is_email_valid=false) → null"),
                Arguments.of("user@kakao.com", true, false, null, "미인증(is_email_verified=false) → null"),
                Arguments.of("user@kakao.com", false, false, null, "휴면+미인증 → null"),
            )
    }
}
