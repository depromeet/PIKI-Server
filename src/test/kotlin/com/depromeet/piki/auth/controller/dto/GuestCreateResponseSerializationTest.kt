package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.domain.IdentityType
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// TokenCarrying DTO 의 직렬화 contract 를 검증한다:
// - 내부필드(tokenPair·bodyTokensIncluded)는 절대 노출되지 않는다 (@get:JsonIgnore)
// - APP(기본): body 에 토큰 / WEB(withoutBodyTokens): 토큰 null
class GuestCreateResponseSerializationTest {
    private val mapper = jacksonObjectMapper()

    private fun user(): UserResponse =
        UserResponse(
            id = UUID.fromString("8f1a3c2b-9d44-4e2a-9b12-1a2b3c4d5e6f"),
            nickname = "닉",
            profileImage = "img",
            identityType = IdentityType.GUEST,
        )

    @Suppress("UNCHECKED_CAST")
    private fun toMap(any: Any): Map<String, Any?> =
        mapper.readValue(mapper.writeValueAsString(any), Map::class.java) as Map<String, Any?>

    @Test
    fun `APP 응답은 body 에 토큰을 싣고 내부필드는 노출하지 않는다`() {
        val map = toMap(GuestCreateResponse(user = user(), tokenPair = TokenPair("at", "rt")))

        assertEquals("at", map["accessToken"])
        assertEquals("rt", map["refreshToken"])
        assertTrue(map.containsKey("user"))
        assertTrue("tokenPair" !in map, "tokenPair 가 직렬화에 노출되면 안 된다")
        assertTrue("bodyTokensIncluded" !in map, "bodyTokensIncluded 가 직렬화에 노출되면 안 된다")
    }

    @Test
    fun `WEB 응답(withoutBodyTokens)은 토큰을 null 로 비우고 user 는 유지한다`() {
        val web = GuestCreateResponse(user = user(), tokenPair = TokenPair("at", "rt")).withoutBodyTokens()
        val map = toMap(web)

        assertTrue(map.containsKey("accessToken"))
        assertNull(map["accessToken"])
        // refreshToken 도 "키는 존재 + 값은 null" 까지 본다 — 필드가 통째로 빠져도 통과하는 회귀를 막기 위함.
        assertTrue(map.containsKey("refreshToken"))
        assertNull(map["refreshToken"])
        assertTrue(map.containsKey("user"))
        assertTrue("tokenPair" !in map)
    }
}
