package com.depromeet.team3.guest.service

import com.depromeet.team3.guest.domain.Guest
import com.depromeet.team3.guest.repository.GuestRepository
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class GuestServiceTest {
    private class StubGuestRepository : GuestRepository {
        val saved = mutableListOf<UUID>()

        override fun save(id: UUID): Guest {
            saved.add(id)
            return Guest(id)
        }
    }

    private val repository = StubGuestRepository()
    private val guestService = GuestService(repository)

    @Test
    fun `issueGuestId 는 UUID 를 반환한다`() {
        val id = guestService.issueGuestId()

        assertEquals(listOf(id), repository.saved)
    }

    @Test
    fun `issueGuestId 를 여러 번 호출하면 매번 새로운 UUID 가 발급된다`() {
        val first = guestService.issueGuestId()
        val second = guestService.issueGuestId()
        val third = guestService.issueGuestId()

        val ids = setOf(first, second, third)
        assertEquals(3, ids.size)
        assertEquals(listOf(first, second, third), repository.saved)
    }
}
