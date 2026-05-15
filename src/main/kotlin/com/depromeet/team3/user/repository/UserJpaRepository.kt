package com.depromeet.team3.user.repository

import com.depromeet.team3.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserJpaRepository : JpaRepository<User, UUID> {
    fun existsByNickname(nickname: String): Boolean
}
