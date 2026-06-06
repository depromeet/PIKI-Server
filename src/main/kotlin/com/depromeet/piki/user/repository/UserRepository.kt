package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import java.time.LocalDateTime
import java.util.UUID

interface UserRepository {
    fun save(user: User): User

    fun findById(id: UUID): User?

    // 30일 파기 스케줄러 — grace 기간을 넘긴 MEMBER tombstone id 를 limit 개까지 오래된 순으로 조회.
    fun findIdsToPurge(
        cutoff: LocalDateTime,
        identityType: IdentityType,
        limit: Int,
    ): List<UUID>

    fun findByIds(ids: Collection<UUID>): List<User>

    fun existsByNickname(nickname: String): Boolean

    // 본인 user 를 제외한 중복 검사. 닉네임 유지·자기 닉네임으로 다시 변경하는 흐름에서
    // 본인까지 잡아 409 를 내던 결을 막기 위한 변형 (#230).
    fun existsByNicknameAndIdNot(
        nickname: String,
        excludeUserId: UUID,
    ): Boolean

    fun findNicknamesIn(candidates: Collection<String>): List<String>
}
