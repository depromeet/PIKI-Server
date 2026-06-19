package com.depromeet.piki.metrics.launch

import java.time.LocalDate
import java.time.LocalDateTime

// 런칭데이 리캡 화면(admin)의 단일 뷰모델. 모든 수치는 LaunchMetricsRepository 의 DB 집계에서 나오고,
// 비율·평균 등 파생값만 여기서 계산한다(파생값을 한 곳에 모아 템플릿이 계산을 안 하게 한다).
// from~to 는 조회 구간(KST). signup.before 는 구간 시작 전 누적, 나머지 수치는 구간 내([from, to)).
data class LaunchRecap(
    val from: LocalDateTime,
    val to: LocalDateTime,
    val signup: Signup,
    val wish: Wish,
    val tournament: Tournament,
    val pushReachableUsers: Long,
    val retention: Retention,
    val push: Push,
    val hourlySignups: List<HourCount>,
) {
    data class Signup(
        val before: Long,
        val after: Long,
        val afterMembers: Long,
        val afterGuests: Long,
        val byProvider: Map<String, Long>,
        val guestToMemberConversions: Long,
    )

    data class Wish(
        val total: Long,
        val fromUrl: Long,
        val fromImage: Long,
        val parsedReady: Long,
        val parsedFailed: Long,
    ) {
        val parseSuccessRate: Int get() = pct(parsedReady, parsedReady + parsedFailed)
    }

    data class Tournament(
        val created: Long,
        val participants: Long,
        val itemsAdded: Long,
        val completed: Long,
        val plays: Long,
    ) {
        val avgParticipants: String
            get() = if (created == 0L) "0" else "%.1f".format(participants.toDouble() / created)
    }

    data class Retention(
        val launchDaySignups: Long,
        val d1Returned: Long,
        val dau: List<DateCount>,
    ) {
        val d1Rate: Int get() = pct(d1Returned, launchDaySignups)
    }

    // 푸시 히스토리·도달률·근사 클릭률. ctrApprox 는 is_read 기반 근사다(푸시 탭 ∪ 알림센터 열람이 섞임) — UI 에 "근사" 명시.
    data class Push(
        val byType: Map<String, Long>,
        val deliverySuccess: Long,
        val deliveryFailure: Long,
        val deliverySkipped: Long,
        val notificationsTotal: Long,
        val readApprox: Long,
    ) {
        val ctrApproxPct: Int get() = pct(readApprox, notificationsTotal)
    }

    data class HourCount(val hour: Int, val count: Long)

    data class DateCount(val date: LocalDate, val count: Long)

    companion object {
        fun pct(numerator: Long, denominator: Long): Int = if (denominator == 0L) 0 else ((numerator * 100) / denominator).toInt()
    }
}
