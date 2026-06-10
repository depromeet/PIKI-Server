package com.depromeet.piki.user.domain

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class UserDetailTest {
    @Test
    fun `updateEmail 은 새 값으로 갱신한다`() {
        val detail = UserDetail(UUID.randomUUID(), "GOOGLE", "social-1", email = null)

        detail.updateEmail("user@gmail.com")

        assertEquals("user@gmail.com", detail.email)
    }

    @Test
    fun `updateEmail 에 null 이 오면 기존 값을 유지한다 (provider 미제공 시 덮어쓰지 않음)`() {
        val detail = UserDetail(UUID.randomUUID(), "GOOGLE", "social-1", email = "old@gmail.com")

        detail.updateEmail(null)

        assertEquals("old@gmail.com", detail.email)
    }

    @Test
    fun `email 없이 생성하면 null 이다 (게스트·미동의 등 email 미제공)`() {
        val detail = UserDetail(UUID.randomUUID(), "KAKAO", "social-2")

        assertEquals(null, detail.email)
    }
}
