package com.depromeet.team3.user.domain

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserTest {
    private fun guest() =
        User(
            id = UUID.randomUUID(),
            nickname = "테스트유저",
            profileImage = "https://example.com/img.png",
            identityType = IdentityType.GUEST,
        )

    @Test
    fun `GUEST 유저는 MEMBER 로 승격된다`() {
        val user = guest()
        user.promoteToMember()
        assertEquals(IdentityType.MEMBER, user.identityType)
    }

    @Test
    fun `이미 MEMBER 인 유저를 다시 승격하면 예외가 발생한다`() {
        val user = guest()
        user.promoteToMember()
        assertFailsWith<UserException> { user.promoteToMember() }
    }

    @Test
    fun `닉네임을 정상적으로 변경한다`() {
        val user = guest()
        user.updateNickname("새닉네임")
        assertEquals("새닉네임", user.nickname)
    }

    @Test
    fun `닉네임 16자는 허용된다`() {
        val user = guest()
        user.updateNickname("1234567890123456")
        assertEquals("1234567890123456", user.nickname)
    }

    @Test
    fun `닉네임 17자는 예외가 발생한다`() {
        val user = guest()
        assertFailsWith<UserException> { user.updateNickname("12345678901234567") }
    }

    @Test
    fun `빈 닉네임은 예외가 발생한다`() {
        val user = guest()
        assertFailsWith<UserException> { user.updateNickname("") }
    }

    @Test
    fun `공백만 있는 닉네임은 예외가 발생한다`() {
        val user = guest()
        assertFailsWith<UserException> { user.updateNickname("   ") }
    }

    @Test
    fun `softDelete 호출 시 deletedAt 이 설정된다`() {
        val user = guest()
        assertNull(user.deletedAt)
        user.softDelete()
        assertNotNull(user.deletedAt)
    }

    @Test
    fun `softDelete 는 멱등성을 보장한다`() {
        val user = guest()
        user.softDelete()
        val first = user.deletedAt
        user.softDelete()
        assertEquals(first, user.deletedAt)
    }
}
