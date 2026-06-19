package com.depromeet.piki.metrics.activity

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// 인증된 요청의 활성 유저를 "오늘(KST)" 단위 1행으로 기록한다(리텐션·DAU). best-effort — 기록 실패가 절대
// 요청을 막지 않는다. 인메모리 쓰로틀로 같은 유저의 같은 날 두 번째 호출부터는 DB 에 닿지조차 않아, 런칭날
// 트래픽에서도 유저당 ~1쓰기/일로 묶인다.
@Component
class DailyActivityRecorder(
    private val repository: UserDailyActivityRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // userId -> 마지막으로 기록한 KST 날짜. 같은 날 재호출을 DB 없이 걸러낸다. 크기는 활성 유저 수로 바운드되고
    // 날짜가 바뀌면 다음 첫 호출이 값을 덮어쓴다.
    private val recordedToday = ConcurrentHashMap<UUID, LocalDate>()

    fun record(userId: UUID) {
        val today = LocalDate.now(KST)
        if (recordedToday[userId] == today) return
        runCatching {
            repository.recordActive(userId, today)
            recordedToday[userId] = today
        }.onFailure { log.warn("일별 활동 기록 실패 — best-effort 라 요청에는 영향 없음", it) }
    }

    // 쓰로틀 맵은 "오늘 기록한 유저"만 의미가 있으므로 KST 자정에 통째로 비운다. 안 비우면 한 번이라도 활동한
    // 모든 유저 UUID 가 영구히 쌓여(유저 수만큼 무한 증가) 장기 운영 시 메모리가 샌다. 비운 직후엔 각 유저의
    // 그날 첫 요청이 다시 1회 기록하면 되므로(멱등) 손실 없다.
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun clearThrottle() {
        recordedToday.clear()
        log.info("일별 활동 쓰로틀 맵 초기화 (KST 자정)")
    }

    companion object {
        // JVM 기본 TZ 는 UTC 라 "활성한 날"은 KST 로 끊어야 런칭데이 경계와 일치한다(Announcement.KST 와 같은 결).
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
