package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.User
import java.util.UUID

interface UserRepository {
    fun save(user: User): User

    fun findById(id: UUID): User?

    fun findByIds(ids: Collection<UUID>): List<User>

    fun existsByNickname(nickname: String): Boolean
}
