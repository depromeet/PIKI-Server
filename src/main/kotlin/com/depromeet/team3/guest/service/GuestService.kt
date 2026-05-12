package com.depromeet.team3.guest.service

import com.depromeet.team3.guest.repository.GuestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GuestService(
    private val guestRepository: GuestRepository,
) {
    @Transactional
    fun issueGuestId(): UUID =
        guestRepository
            .save(UUID.randomUUID())
            .getId()
}
