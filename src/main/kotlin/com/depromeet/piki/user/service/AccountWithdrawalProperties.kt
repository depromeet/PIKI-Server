package com.depromeet.piki.user.service

import org.springframework.boot.context.properties.ConfigurationProperties

// 회원 탈퇴 정책 설정. @ConfigurationPropertiesScan(PikiApplication)으로 자동 등록된다.
// grace-days: tombstone 전이 후 soft-delete 된 콘텐츠를 영구 파기하기까지의 유예일(기본 30일).
@ConfigurationProperties(prefix = "account.withdrawal")
data class AccountWithdrawalProperties(
    val graceDays: Long = DEFAULT_GRACE_DAYS,
) {
    init {
        require(graceDays >= 1) { "account.withdrawal.grace-days 는 1 이상이어야 한다." }
    }

    companion object {
        private const val DEFAULT_GRACE_DAYS = 30L
    }
}
