package com.depromeet.piki.auth.infrastructure.oauth.google.dto

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.stream.Stream
import kotlin.test.assertEquals

class GoogleUserInfoResponseTest {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // email 은 빈 값(null·빈문자열·공백)이면 null 로 정규화하고, 값이 있으면 그대로 옮긴다.
    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("emailNormalizationCases")
    fun `email 정규화 - 빈 값은 null, 값이 있으면 그대로`(
        email: String?,
        expected: String?,
        @Suppress("UNUSED_PARAMETER") description: String,
    ) {
        val result = GoogleUserInfoResponse(id = "google-123", email = email).toOAuthUserInfo()

        assertEquals(expected, result.email)
    }

    @Test
    fun `응답은 provider·socialId·profileImage·email 을 모두 매핑한다`() {
        val result = GoogleUserInfoResponse(id = "google-123", picture = "https://img/p.png", email = "user@gmail.com").toOAuthUserInfo()

        assertEquals(OAuthProvider.GOOGLE, result.provider)
        assertEquals("google-123", result.socialId)
        assertEquals("https://img/p.png", result.profileImage)
        assertEquals("user@gmail.com", result.email)
    }

    @Test
    fun `picture 가 빈 문자열이면 profileImage 는 null 이다`() {
        assertEquals(null, GoogleUserInfoResponse(id = "google-123", picture = "").toOAuthUserInfo().profileImage)
    }

    // ---------- 실제 userinfo JSON 역직렬화 (키 매핑 + unknown 필드 허용) ----------
    // 단위 테스트가 객체를 직접 만들면 역직렬화 경로가 우회된다. Google 이 실제로 주는 모양
    // (verified_email·name 등 모델에 없는 필드 포함)으로 역직렬화를 고정해 Kakao·Apple 과 검증 수준을 통일한다.

    @Test
    fun `실제 userinfo JSON 을 역직렬화해 email 을 추출한다 (unknown 필드 허용)`() {
        val json =
            """
            {
              "id": "google-123",
              "email": "user@gmail.com",
              "verified_email": true,
              "name": "홍길동",
              "given_name": "길동",
              "family_name": "홍",
              "picture": "https://img/p.png",
              "locale": "ko"
            }
            """.trimIndent()

        val result = objectMapper.readValue<GoogleUserInfoResponse>(json).toOAuthUserInfo()

        assertEquals(OAuthProvider.GOOGLE, result.provider)
        assertEquals("google-123", result.socialId)
        assertEquals("https://img/p.png", result.profileImage)
        assertEquals("user@gmail.com", result.email)
    }

    @Test
    fun `email 미동의 userinfo JSON 은 email 이 null 이다`() {
        val json =
            """
            {
              "id": "google-123",
              "verified_email": false,
              "picture": "https://img/p.png"
            }
            """.trimIndent()

        assertEquals(null, objectMapper.readValue<GoogleUserInfoResponse>(json).toOAuthUserInfo().email)
    }

    companion object {
        @JvmStatic
        fun emailNormalizationCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("user@gmail.com", "user@gmail.com", "정상 email → 그대로"),
                Arguments.of(null, null, "email 부재(미동의) → null"),
                Arguments.of("", null, "빈 문자열 → null"),
                Arguments.of("   ", null, "공백 문자열 → null"),
            )
    }
}
