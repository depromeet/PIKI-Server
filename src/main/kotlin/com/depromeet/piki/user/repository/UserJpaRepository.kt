package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserJpaRepository : JpaRepository<User, UUID> {
    fun existsByNickname(nickname: String): Boolean

    fun existsByNicknameAndIdNot(
        nickname: String,
        id: UUID,
    ): Boolean
}
