package com.depromeet.piki.auth.service.dto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenPairTest {
    // data class 자동 toString 이 raw 토큰을 노출하면, 이 객체를 품은 DTO 를 log("{}", dto) 로 찍는 순간
    // 토큰이 샌다. toString 마스킹으로 그 경로를 닫는다.
    @Test
    fun `toString 은 raw 토큰을 노출하지 않고 마스킹한다`() {
        val rendered = TokenPair(accessToken = "secret-access-token", refreshToken = "secret-refresh-token").toString()

        assertFalse(rendered.contains("secret-access-token"), "access 토큰이 toString 에 노출되면 안 된다")
        assertFalse(rendered.contains("secret-refresh-token"), "refresh 토큰이 toString 에 노출되면 안 된다")
        assertTrue(rendered.contains("***"))
    }
}
