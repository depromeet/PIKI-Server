package com.depromeet.piki.notification.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

class NotificationHistorySizeTest {
    // 미지정이면 기본 20, 1~50 범위를 벗어나면 양 끝으로 보정한다.
    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("sizeCases")
    fun `size 보정`(
        raw: Int?,
        expected: Int,
        @Suppress("UNUSED_PARAMETER") description: String,
    ) {
        assertEquals(expected, NotificationHistorySize.of(raw).value)
    }

    companion object {
        @JvmStatic
        fun sizeCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(null, 20, "미지정 → 기본 20"),
                Arguments.of(0, 1, "0 → 하한 1"),
                Arguments.of(-5, 1, "음수 → 하한 1"),
                Arguments.of(1, 1, "1 → 그대로"),
                Arguments.of(20, 20, "20 → 그대로"),
                Arguments.of(50, 50, "50 → 그대로"),
                Arguments.of(51, 50, "51 → 상한 50"),
                Arguments.of(1000, 50, "초과 → 상한 50"),
            )
    }
}
