package com.depromeet.piki.metrics.activity

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.nio.ByteBuffer
import java.sql.Date
import java.time.LocalDate
import java.util.UUID

// 일별 활성 유저 기록 전용 저장소. INSERT IGNORE 로 (user_id, active_date) 유니크 충돌을 멱등 무시한다 —
// 인메모리 쓰로틀을 비켜간 동시 첫 요청·인스턴스 재시작 등으로 중복 INSERT 가 와도 1행만 남고 예외도 안 난다.
// user_id 는 users.id(BINARY(16))와 같은 big-endian 16바이트로 인코딩해 동일 유저로 join·집계된다.
@Repository
class UserDailyActivityRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun recordActive(
        userId: UUID,
        activeDate: LocalDate,
    ) {
        jdbcTemplate.update(
            "INSERT IGNORE INTO user_daily_activity (user_id, active_date, created_at) VALUES (?, ?, NOW(6))",
            uuidToBytes(userId),
            Date.valueOf(activeDate),
        )
    }

    private fun uuidToBytes(uuid: UUID): ByteArray =
        ByteBuffer
            .allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
}
