package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserJpaRepository : JpaRepository<User, UUID> {
    fun existsByNickname(nickname: String): Boolean

    fun existsByNicknameAndIdNot(
        nickname: String,
        id: UUID,
    ): Boolean

    @Query("SELECT u.nickname FROM User u WHERE u.nickname IN :nicknames")
    fun findNicknamesIn(
        @Param("nicknames") nicknames: Collection<String>,
    ): List<String>
}
