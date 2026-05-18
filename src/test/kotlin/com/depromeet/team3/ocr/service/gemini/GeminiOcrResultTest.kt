package com.depromeet.team3.ocr.service.gemini

import com.depromeet.team3.common.domain.Product.Field
import com.depromeet.team3.ocr.service.gemini.GeminiOcrResult.GeminiBoundingBox
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GeminiOcrResultTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // ---------- toProduct / toField 분기 ----------

    @Test
    fun `value 가 없으면 Field_NotFound 로 매핑된다`() {
        val result = GeminiOcrResult(
            name = null,
            price = null,
            category = null,
            nameBoundingBox = null,
            priceBoundingBox = null,
            categoryBoundingBox = null,
        )

        val product = result.toProduct()

        assertIs<Field.NotFound>(product.name)
        assertIs<Field.NotFound>(product.price)
        assertIs<Field.NotFound>(product.category)
    }

    @Test
    fun `value 는 있지만 boundingBox 가 없으면 Field_Inferred 로 매핑된다`() {
        val result = GeminiOcrResult(
            name = "우유",
            price = 3500,
            category = "음료",
            nameBoundingBox = null,
            priceBoundingBox = null,
            categoryBoundingBox = null,
        )

        val product = result.toProduct()

        val name = assertIs<Field.Inferred<String>>(product.name)
        val price = assertIs<Field.Inferred<Int>>(product.price)
        val category = assertIs<Field.Inferred<String>>(product.category)
        assertEquals("우유", name.value)
        assertEquals(3500, price.value)
        assertEquals("음료", category.value)
    }

    @Test
    fun `value 와 boundingBox 가 모두 있으면 Field_Extracted 로 매핑된다`() {
        val box = GeminiBoundingBox(yMin = 10, xMin = 20, yMax = 30, xMax = 40)
        val result = GeminiOcrResult(
            name = "우유",
            price = null,
            category = null,
            nameBoundingBox = box,
            priceBoundingBox = null,
            categoryBoundingBox = null,
        )

        val product = result.toProduct()

        val name = assertIs<Field.Extracted<String>>(product.name)
        assertEquals("우유", name.value)
        assertEquals(10, name.boundingBox.yMin)
        assertEquals(20, name.boundingBox.xMin)
        assertEquals(30, name.boundingBox.yMax)
        assertEquals(40, name.boundingBox.xMax)
    }

    @Test
    fun `필드별로 서로 다른 상태가 섞여 있어도 독립적으로 매핑된다`() {
        val box = GeminiBoundingBox(yMin = 1, xMin = 2, yMax = 3, xMax = 4)
        val result = GeminiOcrResult(
            name = "우유",
            price = 3500,
            category = null,
            nameBoundingBox = box,
            priceBoundingBox = null,
            categoryBoundingBox = null,
        )

        val product = result.toProduct()

        assertIs<Field.Extracted<String>>(product.name)
        assertIs<Field.Inferred<Int>>(product.price)
        assertIs<Field.NotFound>(product.category)
    }

    // ---------- @JsonProperty wire format 매핑 ----------

    @Test
    fun `GeminiBoundingBox 는 Gemini wire format(ymin, xmin) 을 camelCase 필드로 역직렬화한다`() {
        val json = """{"ymin": 100, "xmin": 200, "ymax": 300, "xmax": 400}"""

        val box = objectMapper.readValue<GeminiBoundingBox>(json)

        assertEquals(100, box.yMin)
        assertEquals(200, box.xMin)
        assertEquals(300, box.yMax)
        assertEquals(400, box.xMax)
    }

    @Test
    fun `GeminiOcrResult 전체를 wire format JSON 에서 역직렬화할 수 있다`() {
        val json = """
            {
              "name": "우유",
              "price": 3500,
              "category": "음료",
              "nameBoundingBox": {"ymin": 1, "xmin": 2, "ymax": 3, "xmax": 4},
              "priceBoundingBox": null,
              "categoryBoundingBox": null
            }
        """.trimIndent()

        val result = objectMapper.readValue<GeminiOcrResult>(json)

        assertEquals("우유", result.name)
        assertEquals(3500, result.price)
        assertEquals("음료", result.category)
        assertEquals(GeminiBoundingBox(yMin = 1, xMin = 2, yMax = 3, xMax = 4), result.nameBoundingBox)
        assertNull(result.priceBoundingBox)
        assertNull(result.categoryBoundingBox)
    }
}
