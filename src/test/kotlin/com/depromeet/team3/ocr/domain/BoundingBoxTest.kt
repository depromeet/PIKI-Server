package com.depromeet.team3.ocr.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoundingBoxTest {
    @Test
    fun `정상 좌표면 BoundingBox 를 만든다`() {
        val box = BoundingBox.ofNormalizedOrNull(yMin = 100, xMin = 200, yMax = 800, xMax = 700)

        assertEquals(100, box?.yMin)
        assertEquals(200, box?.xMin)
        assertEquals(800, box?.yMax)
        assertEquals(700, box?.xMax)
    }

    @Test
    fun `좌표가 하나라도 null 이면 null`() {
        assertNull(BoundingBox.ofNormalizedOrNull(yMin = null, xMin = 200, yMax = 800, xMax = 700))
        assertNull(BoundingBox.ofNormalizedOrNull(yMin = 100, xMin = null, yMax = 800, xMax = 700))
        assertNull(BoundingBox.ofNormalizedOrNull(yMin = 100, xMin = 200, yMax = null, xMax = 700))
        assertNull(BoundingBox.ofNormalizedOrNull(yMin = 100, xMin = 200, yMax = 800, xMax = null))
    }

    @Test
    fun `0~1000 범위를 벗어나면 null`() {
        assertNull(BoundingBox.ofNormalizedOrNull(yMin = -1, xMin = 200, yMax = 800, xMax = 700))
        assertNull(BoundingBox.ofNormalizedOrNull(yMin = 100, xMin = 200, yMax = 1001, xMax = 700))
    }

    @Test
    fun `min 이 max 이상이면(순서 역전·영폭) null`() {
        assertNull(BoundingBox.ofNormalizedOrNull(yMin = 800, xMin = 200, yMax = 100, xMax = 700))
        assertNull(BoundingBox.ofNormalizedOrNull(yMin = 100, xMin = 700, yMax = 800, xMax = 200))
        assertNull(BoundingBox.ofNormalizedOrNull(yMin = 100, xMin = 200, yMax = 100, xMax = 700))
    }
}
