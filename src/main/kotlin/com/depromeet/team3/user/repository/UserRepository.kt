package com.depromeet.team3.user.repository

import com.depromeet.team3.user.domain.User
import java.util.UUID

interface UserRepository {
    fun save(user: User): User

    fun findById(id: UUID): User?

    fun existsByNickname(nickname: String): Boolean
}
