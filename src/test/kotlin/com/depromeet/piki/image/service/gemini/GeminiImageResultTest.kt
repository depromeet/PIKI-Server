package com.depromeet.piki.image.service.gemini

import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeminiImageResultTest {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // ---------- toProductSnapshot 매핑 ----------

    @Test
    fun `toProductSnapshot 은 link 없이 name·price·currency 를 옮기고 category 는 버린다`() {
        val result = GeminiImageResult(name = "우유", price = 3500, category = "음료", currency = "KRW")

        val snapshot = result.toImageExtraction().snapshot

        assertNull(snapshot.link)
        assertEquals("우유", snapshot.name)
        assertEquals(3500, snapshot.currentPrice)
        assertEquals("KRW", snapshot.currency)
        assertNull(snapshot.imageUrl)
    }

    @Test
    fun `toProductSnapshot 은 null 필드를 그대로 옮긴다`() {
        val result = GeminiImageResult(name = null, price = null, category = null, currency = null)

        val snapshot = result.toImageExtraction().snapshot

        assertNull(snapshot.name)
        assertNull(snapshot.currentPrice)
        assertNull(snapshot.currency)
    }

    @Test
    fun `toProductSnapshot 은 currency 대소문자·공백을 ISO 4217 로 정규화한다`() {
        val result = GeminiImageResult(name = "우유", price = 3500, category = "음료", currency = " krw ")

        assertEquals("KRW", result.toImageExtraction().snapshot.currency)
    }

    @Test
    fun `toProductSnapshot 은 ISO 4217 형식이 아닌 currency 를 null 로 떨어뜨린다`() {
        val result = GeminiImageResult(name = "우유", price = 3500, category = "음료", currency = "원")

        assertNull(result.toImageExtraction().snapshot.currency)
    }

    // ---------- boundingBox 매핑 ----------

    @Test
    fun `toImageExtraction 은 정상 좌표 boundingBox 를 매핑한다`() {
        val result =
            GeminiImageResult(
                name = "우유",
                price = 3500,
                category = null,
                currency = null,
                boundingBox = GeminiImageResult.BoundingBoxDto(yMin = 100, xMin = 200, yMax = 800, xMax = 700),
            )

        val bbox = result.toImageExtraction().boundingBox

        assertEquals(100, bbox?.yMin)
        assertEquals(200, bbox?.xMin)
        assertEquals(800, bbox?.yMax)
        assertEquals(700, bbox?.xMax)
    }

    @Test
    fun `toImageExtraction 은 boundingBox 가 없거나 비정상이면 null 이다`() {
        val noBox = GeminiImageResult(name = "우유", price = 3500, category = null, currency = null, boundingBox = null)
        assertNull(noBox.toImageExtraction().boundingBox)

        // yMax < yMin (순서 역전) → null
        val invalid =
            GeminiImageResult(
                name = "우유",
                price = 3500,
                category = null,
                currency = null,
                boundingBox = GeminiImageResult.BoundingBoxDto(yMin = 800, xMin = 200, yMax = 100, xMax = 700),
            )
        assertNull(invalid.toImageExtraction().boundingBox)
    }

    // ---------- wire format 역직렬화 ----------

    @Test
    fun `GeminiImageResult 를 wire format JSON 에서 역직렬화할 수 있다`() {
        val json = """{"name": "우유", "price": 3500, "category": "음료", "currency": "KRW"}"""

        val result = objectMapper.readValue<GeminiImageResult>(json)

        assertEquals("우유", result.name)
        assertEquals(3500, result.price)
        assertEquals("음료", result.category)
        assertEquals("KRW", result.currency)
    }

    @Test
    fun `필드가 누락된 JSON 은 해당 필드가 null 로 역직렬화된다`() {
        val json = """{"name": "우유"}"""

        val result = objectMapper.readValue<GeminiImageResult>(json)

        assertEquals("우유", result.name)
        assertNull(result.price)
        assertNull(result.category)
        assertNull(result.currency)
    }
}
