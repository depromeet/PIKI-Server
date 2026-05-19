package com.depromeet.team3.ocr.controller.dto

import com.depromeet.team3.common.domain.Product
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OcrResponseTest {

    @Test
    fun `Product 의 값이 그대로 응답으로 매핑된다`() {
        val product = Product(name = "우유", price = 3500, category = "음료")

        val response = OcrResponse.from(product)

        assertEquals("우유", response.name)
        assertEquals(3500, response.price)
        assertEquals("음료", response.category)
    }

    @Test
    fun `추출 실패한 필드는 응답에서 null 로 매핑된다`() {
        val product = Product(name = null, price = null, category = null)

        val response = OcrResponse.from(product)

        assertNull(response.name)
        assertNull(response.price)
        assertNull(response.category)
    }
}
