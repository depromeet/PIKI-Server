package com.depromeet.piki.metrics.launch

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// 런칭데이 리캡 조립. 조회 구간(KST from~to)을 created_at 저장 기준(UTC)으로 변환해 리포지토리에 넘기고, 집계 결과를
// 하나의 LaunchRecap 으로 묶는다. from·to 미지정이면 런칭일 00:00 ~ 지금(KST)으로 기본 구간을 잡는다.
// 외부 호출 없이 DB 읽기뿐이라 짧은 readOnly 트랜잭션 하나로 충분하다.
@Service
class LaunchMetricsService(
    private val repository: LaunchMetricsRepository,
) {
    @Transactional(readOnly = true)
    fun recap(
        from: LocalDateTime?,
        to: LocalDateTime?,
    ): LaunchRecap {
        val fromKst = from ?: DEFAULT_LAUNCH_DATE.atStartOfDay()
        val toKst = to ?: LocalDateTime.now(KST)
        val fromUtc = fromKst.atZone(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        val toUtc = toKst.atZone(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        // 리텐션 "다음날"은 구간 시작일(KST) + 1 — 그 구간에 가입한 코호트가 다음날 돌아왔는가.
        val nextDayKst = fromKst.toLocalDate().plusDays(1)

        val identityCounts = repository.countWithinByIdentityType(fromUtc, toUtc)
        val (wishUrl, wishImage) = repository.countWishesBySource(fromUtc, toUtc)
        val (parsedReady, parsedFailed) = repository.countParsing(fromUtc, toUtc)
        val (notifTotal, notifRead) = repository.notificationReadApprox(fromUtc, toUtc)
        val (deliverySuccess, deliveryFailure, deliverySkipped) = repository.announcementDelivery(fromUtc, toUtc)
        val hourly = repository.hourlySignups(fromUtc, toUtc)

        return LaunchRecap(
            from = fromKst,
            to = toKst,
            signup =
                LaunchRecap.Signup(
                    before = repository.countActiveUsersBefore(fromUtc),
                    after = repository.countActiveUsersWithin(fromUtc, toUtc),
                    afterMembers = identityCounts["MEMBER"] ?: 0L,
                    afterGuests = identityCounts["GUEST"] ?: 0L,
                    byProvider = repository.countSignupsByProvider(fromUtc, toUtc),
                    guestToMemberConversions = repository.countGuestToMemberConversions(fromUtc, toUtc),
                ),
            wish =
                LaunchRecap.Wish(
                    total = repository.countWishes(fromUtc, toUtc),
                    fromUrl = wishUrl,
                    fromImage = wishImage,
                    parsedReady = parsedReady,
                    parsedFailed = parsedFailed,
                ),
            tournament =
                LaunchRecap.Tournament(
                    created = repository.countTournamentsCreated(fromUtc, toUtc),
                    participants = repository.countTournamentParticipants(fromUtc, toUtc),
                    itemsAdded = repository.countTournamentItems(fromUtc, toUtc),
                    completed = repository.countTournamentCompleted(fromUtc, toUtc),
                    plays = repository.countTournamentPlays(fromUtc, toUtc),
                ),
            pushReachableUsers = repository.countPushReachableUsers(),
            retention =
                LaunchRecap.Retention(
                    launchDaySignups = repository.countSignupsInWindow(fromUtc, toUtc),
                    d1Returned = repository.countD1Returned(fromUtc, toUtc, nextDayKst),
                    dau = repository.dailyActiveUsers(),
                ),
            push =
                LaunchRecap.Push(
                    byType = repository.notificationsByType(fromUtc, toUtc),
                    deliverySuccess = deliverySuccess,
                    deliveryFailure = deliveryFailure,
                    deliverySkipped = deliverySkipped,
                    notificationsTotal = notifTotal,
                    readApprox = notifRead,
                ),
            hourlySignups = hourBuckets(fromKst, toKst, hourly),
        )
    }

    // 구간 내 KST 시간대 막대. 같은 날 구간이면 [from.hour, to 끝시) 만, 여러 날이면 0~23 전체.
    private fun hourBuckets(
        from: LocalDateTime,
        to: LocalDateTime,
        counts: Map<Int, Long>,
    ): List<LaunchRecap.HourCount> {
        val hours =
            if (from.toLocalDate() == to.toLocalDate()) {
                val onHour = to.minute == 0 && to.second == 0 && to.nano == 0
                val end = (if (onHour) to.hour else to.hour + 1).coerceAtMost(24)
                (from.hour until end).toList()
            } else {
                (0..23).toList()
            }
        return hours.map { LaunchRecap.HourCount(it, counts[it] ?: 0L) }
    }

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")

        // 런칭일 기본값 — from 미지정 시 이 날 00:00(KST)부터.
        val DEFAULT_LAUNCH_DATE: LocalDate = LocalDate.of(2026, 6, 20)
    }
}
