package com.depromeet.piki.metrics.launch

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// 런칭데이 리캡 조립. 런칭 경계(KST 자정)를 created_at 저장 기준(UTC)으로 변환해 리포지토리에 넘기고, 집계 결과를
// 하나의 LaunchRecap 으로 묶는다. 외부 호출 없이 DB 읽기뿐이라 짧은 readOnly 트랜잭션 하나로 충분하다.
@Service
class LaunchMetricsService(
    private val repository: LaunchMetricsRepository,
) {
    @Transactional(readOnly = true)
    fun recap(launchDate: LocalDate): LaunchRecap {
        val boundaryUtc = launchDate.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        val boundaryPlus1dUtc =
            launchDate.plusDays(1).atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()

        val identityCounts = repository.countAfterByIdentityType(boundaryUtc)
        val (wishUrl, wishImage) = repository.countWishesBySource(boundaryUtc)
        val (parsedReady, parsedFailed) = repository.countParsing(boundaryUtc)
        val (notifTotal, notifRead) = repository.notificationReadApprox(boundaryUtc)
        val (deliverySuccess, deliveryFailure, deliverySkipped) = repository.announcementDelivery(boundaryUtc)
        val hourly = repository.hourlySignups(boundaryUtc, boundaryPlus1dUtc)

        return LaunchRecap(
            launchDate = launchDate,
            signup =
                LaunchRecap.Signup(
                    before = repository.countActiveUsersBefore(boundaryUtc),
                    after = repository.countActiveUsersAfter(boundaryUtc),
                    afterMembers = identityCounts["MEMBER"] ?: 0L,
                    afterGuests = identityCounts["GUEST"] ?: 0L,
                    byProvider = repository.countSignupsByProvider(boundaryUtc),
                    guestToMemberConversions = repository.countGuestToMemberConversions(boundaryUtc),
                ),
            wish =
                LaunchRecap.Wish(
                    total = repository.countWishes(boundaryUtc),
                    fromUrl = wishUrl,
                    fromImage = wishImage,
                    parsedReady = parsedReady,
                    parsedFailed = parsedFailed,
                ),
            tournament =
                LaunchRecap.Tournament(
                    created = repository.countTournamentsCreated(boundaryUtc),
                    participants = repository.countTournamentParticipants(boundaryUtc),
                    itemsAdded = repository.countTournamentItems(boundaryUtc),
                    completed = repository.countTournamentCompleted(boundaryUtc),
                    plays = repository.countTournamentPlays(boundaryUtc),
                ),
            pushReachableUsers = repository.countPushReachableUsers(),
            retention =
                LaunchRecap.Retention(
                    launchDaySignups = repository.countLaunchDaySignups(boundaryUtc, boundaryPlus1dUtc),
                    d1Returned = repository.countD1Returned(boundaryUtc, boundaryPlus1dUtc, launchDate.plusDays(1)),
                    dau = repository.dailyActiveUsers(),
                ),
            push =
                LaunchRecap.Push(
                    byType = repository.notificationsByType(boundaryUtc),
                    deliverySuccess = deliverySuccess,
                    deliveryFailure = deliveryFailure,
                    deliverySkipped = deliverySkipped,
                    notificationsTotal = notifTotal,
                    readApprox = notifRead,
                ),
            hourlySignups = (0..23).map { LaunchRecap.HourCount(it, hourly[it] ?: 0L) },
        )
    }

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")

        // 런칭데이 기본값 — date 파라미터로 덮어쓸 수 있다.
        val DEFAULT_LAUNCH_DATE: LocalDate = LocalDate.of(2026, 6, 20)
    }
}
