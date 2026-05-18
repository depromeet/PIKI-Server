package com.depromeet.team3.ocr.controller.dto

import com.depromeet.team3.common.domain.BoundingBox
import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.common.domain.Product.Field
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OcrResponseTest {

    @Test
    fun `Field_NotFound 는 응답에서 null 로 매핑된다`() {
        val product = Product(
            name = Field.NotFound,
            price = Field.NotFound,
            category = Field.NotFound,
        )

        val response = OcrResponse.from(product)

        assertNull(response.name)
        assertNull(response.price)
        assertNull(response.category)
    }

    @Test
    fun `Field_Inferred 는 boundingBox 가 null 이고 isInferred 가 true 로 매핑된다`() {
        val product = Product(
            name = Field.Inferred("우유"),
            price = Field.NotFound,
            category = Field.NotFound,
        )

        val response = OcrResponse.from(product)

        val name = assertNotNull(response.name)
        assertEquals("우유", name.value)
        assertNull(name.boundingBox)
        assertTrue(name.isInferred)
    }

    @Test
    fun `Field_Extracted 는 value 와 boundingBox 가 모두 채워지고 isInferred 가 false 로 매핑된다`() {
        val product = Product(
            name = Field.Extracted(
                value = "우유",
                boundingBox = BoundingBox(yMin = 10, xMin = 20, yMax = 30, xMax = 40),
            ),
            price = Field.NotFound,
            category = Field.NotFound,
        )

        val response = OcrResponse.from(product)

        val name = assertNotNull(response.name)
        assertEquals("우유", name.value)
        assertFalse(name.isInferred)
        val box = assertNotNull(name.boundingBox)
        assertEquals(10, box.yMin)
        assertEquals(20, box.xMin)
        assertEquals(30, box.yMax)
        assertEquals(40, box.xMax)
    }
}
