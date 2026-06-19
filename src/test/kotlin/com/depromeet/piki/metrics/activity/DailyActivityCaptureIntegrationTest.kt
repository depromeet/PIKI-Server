package com.depromeet.piki.metrics.activity

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.domain.IdentityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.sql.Date
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals

// Part A — 일별 활동 캡처가 인증 요청에서 실제로 1행을 남기는지(인터셉터·SecurityContext principal·UUID 바인딩·
// INSERT IGNORE 전체 경로)를 실제 HTTP 흐름 + Testcontainers MySQL 로 검증한다. 각 테스트는 격리된 randomUUID
// 유저를 써 인메모리 쓰로틀의 클래스 간 누수를 피한다(CLAUDE.md 공유 mutable 상태 규약). user_daily_activity 는
// FK 가 없어 users 행 없이도 기록되므로, 인증 토큰만으로 충분하다.
@Transactional
class DailyActivityCaptureIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    private fun mockMvc() =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun activityRowCount(userId: UUID): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_daily_activity WHERE user_id = ?",
            Int::class.java,
            uuidToBytes(userId),
        )!!

    @Test
    fun `인증된 요청은 그 유저의 오늘(KST) 활동을 1행 기록한다`() {
        val userId = UUID.randomUUID()
        val token = jwtProvider.generateAccessToken(userId, IdentityType.MEMBER)

        mockMvc()
            .perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)

        assertEquals(1, activityRowCount(userId))
        val activeDate =
            jdbcTemplate
                .queryForObject(
                    "SELECT active_date FROM user_daily_activity WHERE user_id = ?",
                    Date::class.java,
                    uuidToBytes(userId),
                )!!
                .toLocalDate()
        assertEquals(LocalDate.now(ZoneId.of("Asia/Seoul")), activeDate)
    }

    @Test
    fun `같은 유저가 같은 날 두 번 요청해도 1행만 남는다`() {
        val userId = UUID.randomUUID()
        val token = jwtProvider.generateAccessToken(userId, IdentityType.MEMBER)
        val mvc = mockMvc()

        repeat(2) {
            mvc
                .perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
        }

        assertEquals(1, activityRowCount(userId))
    }

    @Test
    fun `미인증 요청은 활동을 기록하지 않는다`() {
        // 전체 행 수는 다른 비-@Transactional 통합테스트가 커밋한 활동 때문에 0 이 아닐 수 있으므로,
        // "미인증 요청이 행을 늘리지 않는다"(before == after)로 단언한다.
        val before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_daily_activity", Long::class.java)!!

        mockMvc()
            .perform(get("/api/v1/notifications"))
            .andExpect(status().isUnauthorized)

        val after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_daily_activity", Long::class.java)!!
        assertEquals(before, after)
    }
}
