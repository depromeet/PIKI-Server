package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
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

    // 30일 파기 스케줄러 스캔용 — grace 기간을 넘긴, 아직 콘텐츠를 파기하지 않은 MEMBER tombstone id 를
    // chunk 단위(Limit)로 읽는다. content_purged_at IS NULL 로 이미 파기한 행을 제외해 매일 영구 재스캔되는 것을 막는다.
    // idx_users_deleted_at(Flyway) 가 deleted_at 범위 스캔을 받친다.
    @Query(
        "SELECT u.id FROM User u WHERE u.deletedAt IS NOT NULL AND u.deletedAt < :cutoff " +
            "AND u.identityType = :identityType AND u.contentPurgedAt IS NULL ORDER BY u.deletedAt",
    )
    fun findIdsByDeletedAtBeforeAndIdentityType(
        @Param("cutoff") cutoff: LocalDateTime,
        @Param("identityType") identityType: IdentityType,
        limit: Limit,
    ): List<UUID>
}
