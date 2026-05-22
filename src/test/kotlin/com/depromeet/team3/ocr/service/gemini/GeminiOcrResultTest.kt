package com.depromeet.team3.ocr.service.gemini

import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeminiOcrResultTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // ---------- toProduct 매핑 ----------

    @Test
    fun `toProduct 는 값을 그대로 Product 로 옮긴다`() {
        val result = GeminiOcrResult(name = "우유", price = 3500, category = "음료", currency = "KRW")

        val product = result.toProduct()

        assertEquals("우유", product.name)
        assertEquals(3500, product.price)
        assertEquals("음료", product.category)
        assertEquals("KRW", product.currency)
    }

    @Test
    fun `toProduct 는 null 필드를 그대로 옮긴다`() {
        val result = GeminiOcrResult(name = null, price = null, category = null, currency = null)

        val product = result.toProduct()

        assertNull(product.name)
        assertNull(product.price)
        assertNull(product.category)
        assertNull(product.currency)
    }

    // ---------- wire format 역직렬화 ----------

    @Test
    fun `GeminiOcrResult 를 wire format JSON 에서 역직렬화할 수 있다`() {
        val json = """{"name": "우유", "price": 3500, "category": "음료", "currency": "KRW"}"""

        val result = objectMapper.readValue<GeminiOcrResult>(json)

        assertEquals("우유", result.name)
        assertEquals(3500, result.price)
        assertEquals("음료", result.category)
        assertEquals("KRW", result.currency)
    }

    @Test
    fun `필드가 누락된 JSON 은 해당 필드가 null 로 역직렬화된다`() {
        val json = """{"name": "우유"}"""

        val result = objectMapper.readValue<GeminiOcrResult>(json)

        assertEquals("우유", result.name)
        assertNull(result.price)
        assertNull(result.category)
        assertNull(result.currency)
    }
}
