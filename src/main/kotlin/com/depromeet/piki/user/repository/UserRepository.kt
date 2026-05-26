package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.User
import java.util.UUID

interface UserRepository {
    fun save(user: User): User

    fun findById(id: UUID): User?

    fun findByIds(ids: Collection<UUID>): List<User>

    fun findAll(): List<User>

    fun existsByNickname(nickname: String): Boolean

    // 본인 user 를 제외한 중복 검사. 닉네임 유지·자기 닉네임으로 다시 변경하는 흐름에서
    // 본인까지 잡아 409 를 내던 결을 막기 위한 변형 (#230).
    fun existsByNicknameAndIdNot(
        nickname: String,
        excludeUserId: UUID,
    ): Boolean
}
