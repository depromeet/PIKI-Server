package com.depromeet.piki.user.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountWithdrawalPropertiesTest {
    @Test
    fun `graceDays 기본값은 30이다`() {
        assertEquals(30L, AccountWithdrawalProperties().graceDays)
    }

    @Test
    fun `graceDays 가 1 미만이면 부팅 시점에 깨진다`() {
        assertFailsWith<IllegalArgumentException> { AccountWithdrawalProperties(graceDays = 0) }
    }
}
