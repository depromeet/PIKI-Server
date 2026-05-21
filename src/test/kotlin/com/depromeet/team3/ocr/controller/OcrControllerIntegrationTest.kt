package com.depromeet.team3.ocr.controller

import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.product.service.gemini.GeminiApiException
import com.depromeet.team3.support.IntegrationTestSupport
import com.depromeet.team3.support.StubOcrClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// OCR 흐름은 DB 를 거치지 않으므로 @Transactional(자동 롤백)이 불필요하다.
class OcrControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var stubOcrClient: StubOcrClient

    @Test
    fun `이미지를 올리면 200 과 함께 추출 결과가 contract 모양으로 응답된다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        stubOcrClient.analyze = {
            Product(name = "나이키 에어포스", price = 99_000, category = null)
        }
        val image = MockMultipartFile("image", "product.png", "image/png", byteArrayOf(1, 2, 3))

        mockMvc
            .perform(multipart("/api/v1/ocr").file(image))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.name").value("나이키 에어포스"))
            .andExpect(jsonPath("$.data.price").value(99_000))
            .andExpect(jsonPath("$.data.category").isEmpty)
    }

    @Test
    fun `빈 이미지 파일을 올리면 400 BAD_REQUEST 가 반환된다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        val emptyImage = MockMultipartFile("image", "empty.png", "image/png", ByteArray(0))

        mockMvc
            .perform(multipart("/api/v1/ocr").file(emptyImage))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.data").isEmpty)
    }

    @Test
    fun `지원하지 않는 이미지 형식을 올리면 400 BAD_REQUEST 가 반환된다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        val gif = MockMultipartFile("image", "product.gif", "image/gif", byteArrayOf(1, 2, 3))

        mockMvc
            .perform(multipart("/api/v1/ocr").file(gif))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.data").isEmpty)
    }

    @Test
    fun `Gemini 호출이 실패하면 502 BAD_GATEWAY 가 반환된다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        stubOcrClient.analyze = {
            throw GeminiApiException.upstreamError(RuntimeException("connection timeout"))
        }
        val image = MockMultipartFile("image", "product.png", "image/png", byteArrayOf(1, 2, 3))

        mockMvc
            .perform(multipart("/api/v1/ocr").file(image))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.code").value("BAD_GATEWAY"))
            .andExpect(jsonPath("$.data").isEmpty)
    }
}
