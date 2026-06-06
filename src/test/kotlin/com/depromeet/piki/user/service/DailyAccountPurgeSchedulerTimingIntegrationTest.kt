package com.depromeet.piki.user.service

import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.domain.IdentityType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// 스케줄러는 별도 트랜잭션으로 콘텐츠를 하드삭제하므로 @Transactional 자동 롤백을 쓰지 않는다(별 트랜잭션 진행이 본질).
// 데이터 격리는 각 테스트가 만든 행을 @AfterEach 에서 자기가 만든 식별자로 명시 정리한다.
class DailyAccountPurgeSchedulerTimingIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var scheduler: DailyAccountPurgeScheduler

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var properties: AccountWithdrawalProperties

    private val createdUserIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanup() {
        createdUserIds.forEach { id ->
            jdbcTemplate.update("DELETE FROM wishes WHERE user_id = ?", uuidToBytes(id))
            jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", uuidToBytes(id))
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", uuidToBytes(id))
        }
        createdUserIds.clear()
    }

    private fun insertTombstoneMember(deletedDaysAgo: Long): UUID {
        val id = UUID.randomUUID()
        createdUserIds.add(id)
        val deletedAt = LocalDateTime.now().minusDays(deletedDaysAgo)
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, profile_image, identity_type, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, ?, ?, NOW(6), NOW(6), ?)",
            uuidToBytes(id),
            "탈퇴" + id.toString().replace("-", "").take(8),
            "https://example.com/img.png",
            IdentityType.MEMBER.name,
            deletedAt,
        )
        // soft-delete 된 콘텐츠 1건씩
        jdbcTemplate.update(
            "INSERT INTO wishes (user_id, item_id, created_at, updated_at, deleted_at) VALUES (?, ?, NOW(6), NOW(6), ?)",
            uuidToBytes(id),
            1L,
            deletedAt,
        )
        jdbcTemplate.update(
            "INSERT INTO notifications " +
                "(user_id, type, title, body, ref_id, is_read, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, ?, ?, ?, FALSE, NOW(6), NOW(6), ?)",
            uuidToBytes(id),
            "TOURNAMENT_JOINED",
            "제목",
            "본문",
            1L,
            deletedAt,
        )
        return id
    }

    private fun contentCount(userId: UUID): Long {
        val wishes =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wishes WHERE user_id = ?",
                Long::class.java,
                uuidToBytes(userId),
            )!!
        val notifications =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?",
                Long::class.java,
                uuidToBytes(userId),
            )!!
        return wishes + notifications
    }

    private fun userExists(userId: UUID): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ?",
            Long::class.java,
            uuidToBytes(userId),
        )!! > 0

    private fun contentPurgedAt(userId: UUID): LocalDateTime? =
        jdbcTemplate.queryForObject(
            "SELECT content_purged_at FROM users WHERE id = ?",
            LocalDateTime::class.java,
            uuidToBytes(userId),
        )

    @Test
    fun `graceDays 를 넘긴 tombstone 의 콘텐츠는 하드삭제되고 tombstone 행은 보존되며 파기 표식이 찍힌다`() {
        assertEquals(30L, properties.graceDays)
        val old = insertTombstoneMember(deletedDaysAgo = 31)

        scheduler.purge()

        assertEquals(0L, contentCount(old))
        // tombstone users 행 자체는 영구 보존(공유 토너먼트 참조 유지).
        assertEquals(true, userExists(old))
        // 파기 완료 표식이 찍혀 다음 스캔에서 제외된다.
        assertNotNull(contentPurgedAt(old))
    }

    @Test
    fun `파기된 tombstone 은 재실행에서 다시 처리되지 않는다`() {
        val old = insertTombstoneMember(deletedDaysAgo = 31)

        scheduler.purge()
        val firstPurgedAt = contentPurgedAt(old)
        assertNotNull(firstPurgedAt)

        // 두 번째 실행 — content_purged_at 이 이미 찍혀 스캔 대상에서 제외되므로 재파기·표식 갱신이 일어나지 않는다.
        scheduler.purge()

        assertEquals(0L, contentCount(old))
        // 표식이 멱등하게 유지된다(첫 파기 시각 보존 → 재처리되지 않았음을 증명).
        assertEquals(firstPurgedAt, contentPurgedAt(old))
    }

    @Test
    fun `graceDays 안쪽 tombstone 의 콘텐츠는 아직 보존된다`() {
        val recent = insertTombstoneMember(deletedDaysAgo = 29)

        scheduler.purge()

        assertEquals(2L, contentCount(recent))
        assertEquals(true, userExists(recent))
    }
}
