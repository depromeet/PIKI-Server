package com.depromeet.piki.metrics.launch

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

// 런칭데이 리캡 집계 전용 읽기 저장소. 모든 시각 컬럼(created_at 등)은 JVM 기본 TZ(UTC)로 저장되므로,
// 서비스가 "런칭 경계(KST)"를 UTC LocalDateTime 으로 변환해 넘긴다(boundary 는 UTC 값이다). KST 시간대별 집계는
// created_at 에 +9h 를 더해 버킷팅한다. user_daily_activity.active_date 만은 이미 KST 날짜로 적재돼 그대로 쓴다.
@Repository
class LaunchMetricsRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    // ---- 가입자 ----
    fun countActiveUsersBefore(boundaryUtc: LocalDateTime): Long =
        count("SELECT COUNT(*) FROM users WHERE created_at < ? AND deleted_at IS NULL", ts(boundaryUtc))

    fun countActiveUsersAfter(boundaryUtc: LocalDateTime): Long =
        count("SELECT COUNT(*) FROM users WHERE created_at >= ? AND deleted_at IS NULL", ts(boundaryUtc))

    fun countAfterByIdentityType(boundaryUtc: LocalDateTime): Map<String, Long> =
        keyCounts(
            "SELECT identity_type, COUNT(*) FROM users WHERE created_at >= ? AND deleted_at IS NULL GROUP BY identity_type",
            ts(boundaryUtc),
        )

    fun countSignupsByProvider(boundaryUtc: LocalDateTime): Map<String, Long> =
        keyCounts(
            "SELECT provider, COUNT(*) FROM user_details WHERE created_at >= ? GROUP BY provider",
            ts(boundaryUtc),
        )

    // 게스트→회원 전환 근사 — 소셜 연결(user_details) 시각이 가입(users.created_at)보다 1분 이상 늦으면, 게스트로 먼저
    // 존재하다 런칭 중 연결한 것으로 본다. 동시 신규가입(거의 동시각)과 구분한다.
    fun countGuestToMemberConversions(boundaryUtc: LocalDateTime): Long =
        count(
            """
            SELECT COUNT(*) FROM user_details ud JOIN users u ON u.id = ud.user_id
            WHERE ud.created_at >= ? AND u.created_at < ud.created_at - INTERVAL 1 MINUTE
            """.trimIndent(),
            ts(boundaryUtc),
        )

    // ---- 위시 ----
    fun countWishes(boundaryUtc: LocalDateTime): Long =
        count("SELECT COUNT(*) FROM wishes WHERE created_at >= ?", ts(boundaryUtc))

    // source_url IS NULL = 이미지 등록, NOT NULL = URL 등록. wish 는 snapshot_id 로 정규화돼 item 정체성은
    // item_snapshots.item_id 를 거쳐 items 에 도달한다(item_id 컬럼 제거됨).
    fun countWishesBySource(boundaryUtc: LocalDateTime): Pair<Long, Long> {
        val byImage =
            keyCounts(
                """
                SELECT i.source_url IS NULL AS is_image, COUNT(*)
                FROM wishes w
                JOIN item_snapshots s ON s.id = w.snapshot_id
                JOIN items i ON i.id = s.item_id
                WHERE w.created_at >= ? GROUP BY is_image
                """.trimIndent(),
                ts(boundaryUtc),
            )
        val url = byImage["0"] ?: 0L
        val image = byImage["1"] ?: 0L
        return url to image
    }

    fun countParsing(boundaryUtc: LocalDateTime): Pair<Long, Long> {
        val byStatus =
            keyCounts(
                "SELECT status, COUNT(*) FROM item_snapshots WHERE created_at >= ? AND status IN ('READY','FAILED') GROUP BY status",
                ts(boundaryUtc),
            )
        return (byStatus["READY"] ?: 0L) to (byStatus["FAILED"] ?: 0L)
    }

    // ---- 토너먼트 ----
    fun countTournamentsCreated(boundaryUtc: LocalDateTime): Long =
        count("SELECT COUNT(*) FROM tournaments WHERE created_at >= ?", ts(boundaryUtc))

    fun countTournamentParticipants(boundaryUtc: LocalDateTime): Long =
        count("SELECT COUNT(DISTINCT user_id) FROM tournament_users WHERE created_at >= ?", ts(boundaryUtc))

    fun countTournamentItems(boundaryUtc: LocalDateTime): Long =
        count("SELECT COUNT(*) FROM tournament_items WHERE created_at >= ?", ts(boundaryUtc))

    // tournaments 에 상태 전이 시각이 없어, 완료는 tournament_users.completed_at 으로 센다(참가자별 완료).
    fun countTournamentCompleted(boundaryUtc: LocalDateTime): Long =
        count("SELECT COUNT(*) FROM tournament_users WHERE completed_at >= ?", ts(boundaryUtc))

    // 플레이 활동량 = 라운드 픽 1건당 history 1행.
    fun countTournamentPlays(boundaryUtc: LocalDateTime): Long =
        count("SELECT COUNT(*) FROM tournament_histories WHERE created_at >= ?", ts(boundaryUtc))

    // ---- 푸시 도달 가능 ----
    fun countPushReachableUsers(): Long =
        count("SELECT COUNT(DISTINCT user_id) FROM user_devices WHERE deleted_at IS NULL")

    // ---- 리텐션 / DAU ----
    fun countLaunchDaySignups(
        boundaryUtc: LocalDateTime,
        boundaryPlus1dUtc: LocalDateTime,
    ): Long = count("SELECT COUNT(*) FROM users WHERE created_at >= ? AND created_at < ?", ts(boundaryUtc), ts(boundaryPlus1dUtc))

    // 런칭날 가입자 중 다음날(KST)에 활동 기록이 있는 수 = D1 재방문.
    fun countD1Returned(
        boundaryUtc: LocalDateTime,
        boundaryPlus1dUtc: LocalDateTime,
        nextDayKst: LocalDate,
    ): Long =
        count(
            """
            SELECT COUNT(*) FROM user_daily_activity uda
            WHERE uda.active_date = ?
              AND uda.user_id IN (SELECT id FROM users WHERE created_at >= ? AND created_at < ?)
            """.trimIndent(),
            java.sql.Date.valueOf(nextDayKst),
            ts(boundaryUtc),
            ts(boundaryPlus1dUtc),
        )

    fun dailyActiveUsers(): List<LaunchRecap.DateCount> =
        jdbcTemplate.query(
            "SELECT active_date, COUNT(*) FROM user_daily_activity GROUP BY active_date ORDER BY active_date",
        ) { rs, _ -> LaunchRecap.DateCount(rs.getDate(1).toLocalDate(), rs.getLong(2)) }

    // ---- 푸시 히스토리 / CTR 근사 ----
    fun notificationsByType(boundaryUtc: LocalDateTime): Map<String, Long> =
        keyCounts("SELECT type, COUNT(*) FROM notifications WHERE created_at >= ? GROUP BY type", ts(boundaryUtc))

    // (total, is_read 합) — CTR 근사 계산용.
    fun notificationReadApprox(boundaryUtc: LocalDateTime): Pair<Long, Long> =
        jdbcTemplate.query(
            "SELECT COUNT(*), COALESCE(SUM(is_read), 0) FROM notifications WHERE created_at >= ?",
            { rs, _ -> rs.getLong(1) to rs.getLong(2) },
            ts(boundaryUtc),
        ).first()

    // 공지 발송 도달 집계(성공/실패/미도달) — 런칭 중 발송된 공지 합산.
    fun announcementDelivery(boundaryUtc: LocalDateTime): Triple<Long, Long, Long> =
        jdbcTemplate.query(
            """
            SELECT COALESCE(SUM(success_count),0), COALESCE(SUM(failure_count),0), COALESCE(SUM(skipped_count),0)
            FROM announcements WHERE sent_at >= ?
            """.trimIndent(),
            { rs, _ -> Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3)) },
            ts(boundaryUtc),
        ).first()

    // ---- 시간대별(KST) ----
    fun hourlySignups(
        boundaryUtc: LocalDateTime,
        boundaryPlus1dUtc: LocalDateTime,
    ): Map<Int, Long> {
        val result = linkedMapOf<Int, Long>()
        jdbcTemplate.query(
            """
            SELECT HOUR(created_at + INTERVAL 9 HOUR) AS h, COUNT(*)
            FROM users WHERE created_at >= ? AND created_at < ?
            GROUP BY h ORDER BY h
            """.trimIndent(),
            { rs, _ -> result[rs.getInt(1)] = rs.getLong(2) },
            ts(boundaryUtc),
            ts(boundaryPlus1dUtc),
        )
        return result
    }

    private fun count(
        sql: String,
        vararg args: Any,
    ): Long = jdbcTemplate.queryForObject(sql, Long::class.java, *args) ?: 0L

    private fun keyCounts(
        sql: String,
        vararg args: Any,
    ): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        jdbcTemplate.query(sql, { rs, _ -> result[rs.getString(1)] = rs.getLong(2) }, *args)
        return result
    }

    private fun ts(value: LocalDateTime): Timestamp = Timestamp.valueOf(value)
}
