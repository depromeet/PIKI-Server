package com.depromeet.piki.user.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubImageStorage
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.domain.IdentityType
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@Transactional
class WithdrawalIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var stubImageStorage: StubImageStorage

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun token(
        userId: UUID,
        identityType: IdentityType,
    ): String = jwtProvider.generateAccessToken(userId, identityType)

    private fun insertUser(
        userId: UUID,
        nickname: String,
        identityType: IdentityType,
    ) {
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, profile_image, identity_type, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            nickname,
            "https://example.com/img.png",
            identityType.name,
        )
    }

    private fun insertUserDetail(userId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO user_details (user_id, provider, social_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            "KAKAO",
            "social_${userId.toString().take(8)}",
        )
    }

    private fun insertUserDevice(userId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO user_devices (user_id, device_id, fcm_token, created_at, updated_at) " +
                "VALUES (?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            "device_${userId.toString().take(8)}",
            "fcm_${userId.toString().take(8)}",
        )
    }

    private fun insertWish(userId: UUID): Long {
        jdbcTemplate.update(
            "INSERT INTO wishes (user_id, item_id, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            1L,
        )
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun insertNotification(userId: UUID): Long {
        jdbcTemplate.update(
            "INSERT INTO notifications (user_id, type, title, body, ref_id, is_read, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, FALSE, NOW(6), NOW(6))",
            uuidToBytes(userId),
            "TOURNAMENT_JOINED",
            "제목",
            "본문",
            1L,
        )
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    @Test
    fun `DELETE users me - MEMBER 가 탈퇴하면 200 과 data null 이 반환된다`() {
        val userId = UUID.randomUUID()
        insertUser(userId, "멤버닉네임", IdentityType.MEMBER)

        mockMvc()
            .perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.MEMBER)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data").value(null))
            .andExpect(jsonPath("$.detail").value("정상적으로 처리되었습니다."))
    }

    @Test
    fun `DELETE users me - 인증 헤더 없으면 401 이 반환된다`() {
        mockMvc()
            .perform(delete("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `DELETE users me - 게스트가 호출하면 403 이 반환된다`() {
        val userId = UUID.randomUUID()
        insertUser(userId, "게스트닉네임", IdentityType.GUEST)

        mockMvc()
            .perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.GUEST)}"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.detail").value("게스트는 탈퇴할 수 없습니다."))
            .andExpect(jsonPath("$.data").value(null))
    }

    @Test
    fun `DELETE users me - cascade 로 PII 가 파기되고 콘텐츠가 soft-delete 되며 닉네임이 익명화된다`() {
        val userId = UUID.randomUUID()
        insertUser(userId, "멤버닉네임", IdentityType.MEMBER)
        insertUserDetail(userId)
        insertUserDevice(userId)
        val wishId = insertWish(userId)
        val notificationId = insertNotification(userId)

        mockMvc()
            .perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.MEMBER)}"),
            ).andExpect(status().isOk)

        // cascade(엔티티 remove + tombstone UPDATE)는 테스트 트랜잭션 세션에 버퍼만 된 상태다.
        // jdbcTemplate raw SELECT 는 세션을 경유하지 않으므로, flush 로 DB 에 먼저 내린다.
        entityManager.flush()
        entityManager.clear()

        // user_details 하드삭제
        val detailCount =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_details WHERE user_id = ?",
                Long::class.java,
                uuidToBytes(userId),
            )
        assertEquals(0L, detailCount)

        // user_devices 하드삭제
        val deviceCount =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_devices WHERE user_id = ?",
                Long::class.java,
                uuidToBytes(userId),
            )
        assertEquals(0L, deviceCount)

        // wishes soft-delete (행은 남고 deleted_at 채워짐)
        val wishDeletedAt =
            jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM wishes WHERE id = ?",
                java.sql.Timestamp::class.java,
                wishId,
            )
        assertNotNull(wishDeletedAt, "wish 가 soft-delete 되어야 한다")

        // notifications soft-delete
        val notiDeletedAt =
            jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM notifications WHERE id = ?",
                java.sql.Timestamp::class.java,
                notificationId,
            )
        assertNotNull(notiDeletedAt, "notification 이 soft-delete 되어야 한다")

        // users tombstone — 행은 남고 닉네임이 익명화됨
        val nickname =
            jdbcTemplate.queryForObject(
                "SELECT nickname FROM users WHERE id = ?",
                String::class.java,
                uuidToBytes(userId),
            )
        assertNotEquals("멤버닉네임", nickname)
        assertEquals("탈퇴" + userId.toString().replace("-", "").take(8), nickname)
        val userDeletedAt =
            jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM users WHERE id = ?",
                java.sql.Timestamp::class.java,
                uuidToBytes(userId),
            )
        assertNotNull(userDeletedAt, "user 가 tombstone 으로 soft-delete 되어야 한다")
    }

    @Test
    fun `DELETE users me - 공유 토너먼트 출전 아이템은 보존된다`() {
        val userId = UUID.randomUUID()
        insertUser(userId, "멤버닉네임", IdentityType.MEMBER)
        // 이 유저가 출전시킨 tournament_item 1건 (공유 토너먼트 자산).
        jdbcTemplate.update(
            "INSERT INTO tournament_items (tournament_id, item_id, user_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, NOW(6), NOW(6))",
            999L,
            1L,
            uuidToBytes(userId),
        )

        mockMvc()
            .perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.MEMBER)}"),
            ).andExpect(status().isOk)

        // tournament_items 는 절대 건드리지 않는다 — 행이 그대로 남아야 한다(userId 는 tombstone 으로 "탈퇴회원" 으로 풀림).
        val tiCount =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tournament_items WHERE user_id = ? AND deleted_at IS NULL",
                Long::class.java,
                uuidToBytes(userId),
            )
        assertEquals(1L, tiCount)
    }

    @Test
    fun `DELETE users me - 탈퇴 시 S3 프로필 prefix 가 삭제 호출된다`() {
        val userId = UUID.randomUUID()
        insertUser(userId, "멤버닉네임", IdentityType.MEMBER)

        mockMvc()
            .perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.MEMBER)}"),
            ).andExpect(status().isOk)

        // 탈퇴 시 유저 프로필 이미지(얼굴 등 PII)를 S3 에서 prefix 통째로 파기한다.
        assertContains(stubImageStorage.deletedPrefixes, "profiles/$userId/")
    }

    @Test
    fun `DELETE users me - 두 번 호출해도 멱등하게 200 이 반환된다`() {
        val userId = UUID.randomUUID()
        insertUser(userId, "멤버닉네임", IdentityType.MEMBER)

        repeat(2) {
            mockMvc()
                .perform(
                    delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.MEMBER)}"),
                ).andExpect(status().isOk)
        }

        // tombstone UPDATE 가 세션에 버퍼만 된 상태이므로 jdbcTemplate 검증 전 flush 한다.
        entityManager.flush()
        entityManager.clear()

        val nickname =
            jdbcTemplate.queryForObject(
                "SELECT nickname FROM users WHERE id = ?",
                String::class.java,
                uuidToBytes(userId),
            )
        assertEquals("탈퇴" + userId.toString().replace("-", "").take(8), nickname)
    }
}
