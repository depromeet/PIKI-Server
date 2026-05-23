package com.depromeet.piki.common.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BoundingBoxTest {
    @Test
    fun `0~1000 범위 안의 정상 좌표로 생성된다`() {
        val box = BoundingBox(yMin = 0, xMin = 100, yMax = 1000, xMax = 500)

        assertEquals(0, box.yMin)
        assertEquals(100, box.xMin)
        assertEquals(1000, box.yMax)
        assertEquals(500, box.xMax)
    }

    @ParameterizedTest
    @CsvSource(
        "-1, 0, 10, 10",
        "0, -1, 10, 10",
        "0, 0, 1001, 10",
        "0, 0, 10, 1001",
    )
    fun `좌표가 0~1000 범위를 벗어나면 생성자에서 예외가 발생한다`(
        yMin: Int,
        xMin: Int,
        yMax: Int,
        xMax: Int,
    ) {
        assertFailsWith<IllegalArgumentException> {
            BoundingBox(yMin = yMin, xMin = xMin, yMax = yMax, xMax = xMax)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "50, 0, 10, 100",
        "0, 100, 100, 50",
    )
    fun `최소 좌표가 최대 좌표보다 크면 생성자에서 예외가 발생한다`(
        yMin: Int,
        xMin: Int,
        yMax: Int,
        xMax: Int,
    ) {
        assertFailsWith<IllegalArgumentException> {
            BoundingBox(yMin = yMin, xMin = xMin, yMax = yMax, xMax = xMax)
        }
    }

    @Test
    fun `ofOrNull 은 정상 좌표면 BoundingBox 를 만든다`() {
        val box = BoundingBox.ofOrNull(yMin = 10, xMin = 20, yMax = 30, xMax = 40)

        val nonNull = assertNotNull(box)
        assertEquals(10, nonNull.yMin)
        assertEquals(20, nonNull.xMin)
        assertEquals(30, nonNull.yMax)
        assertEquals(40, nonNull.xMax)
    }

    @Test
    fun `ofOrNull 은 좌표 중 하나라도 null 이면 null 을 반환한다`() {
        assertNull(BoundingBox.ofOrNull(yMin = null, xMin = 20, yMax = 30, xMax = 40))
        assertNull(BoundingBox.ofOrNull(yMin = 10, xMin = null, yMax = 30, xMax = 40))
        assertNull(BoundingBox.ofOrNull(yMin = 10, xMin = 20, yMax = null, xMax = 40))
        assertNull(BoundingBox.ofOrNull(yMin = 10, xMin = 20, yMax = 30, xMax = null))
    }

    @ParameterizedTest
    @CsvSource(
        "-1, 20, 30, 40",
        "10, 20, 1001, 40",
    )
    fun `ofOrNull 은 좌표가 0~1000 범위를 벗어나면 null 을 반환한다`(
        yMin: Int,
        xMin: Int,
        yMax: Int,
        xMax: Int,
    ) {
        assertNull(BoundingBox.ofOrNull(yMin = yMin, xMin = xMin, yMax = yMax, xMax = xMax))
    }

    @ParameterizedTest
    @CsvSource(
        "50, 0, 10, 100",
        "0, 100, 100, 50",
    )
    fun `ofOrNull 은 최소 좌표가 최대 좌표보다 크면 null 을 반환한다`(
        yMin: Int,
        xMin: Int,
        yMax: Int,
        xMax: Int,
    ) {
        assertNull(BoundingBox.ofOrNull(yMin = yMin, xMin = xMin, yMax = yMax, xMax = xMax))
    }
}
