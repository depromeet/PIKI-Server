package com.depromeet.team3.wishlist.controller

import com.depromeet.team3.product.domain.Product
import com.depromeet.team3.support.IntegrationTestSupport
import com.depromeet.team3.support.StubProductExtractor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Transactional
class WishlistControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var stubExtractor: StubProductExtractor

    @Test
    fun `정상 등록 - 201 과 함께 추출 결과가 응답에 박힌다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        val url = "https://shop.example.com/products/42"
        val guestId = UUID.randomUUID()
        stubExtractor.build = { link ->
            Product(
                link = link,
                name = "나이키 에어포스",
                currentPrice = 99_000,
                currency = "KRW",
                imageUrl = "https://cdn.example.com/p/42.jpg",
            )
        }
        val body = objectMapper.writeValueAsString(mapOf("url" to url, "guestId" to guestId))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.code").value("CREATED"))
            .andExpect(jsonPath("$.data.wishId").isNumber)
            .andExpect(jsonPath("$.data.name").value("나이키 에어포스"))
            .andExpect(jsonPath("$.data.currentPrice").value(99_000))
            .andExpect(jsonPath("$.data.currency").value("KRW"))
            .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/p/42.jpg"))
    }

    @Test
    fun `같은 guest 가 같은 URL 을 두 번 등록하면 409 CONFLICT 가 반환된다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        val url = "https://shop.example.com/products/42"
        val guestId = UUID.randomUUID()
        stubExtractor.build = { Product(link = it, name = "기본 상품") }
        val body = objectMapper.writeValueAsString(mapOf("url" to url, "guestId" to guestId))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    @Test
    fun `다른 guest 가 같은 URL 을 등록하면 둘 다 201 로 등록된다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        val url = "https://shop.example.com/products/42"
        stubExtractor.build = { Product(link = it, name = "기본 상품") }
        val firstBody = objectMapper.writeValueAsString(mapOf("url" to url, "guestId" to UUID.randomUUID()))
        val secondBody = objectMapper.writeValueAsString(mapOf("url" to url, "guestId" to UUID.randomUUID()))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(firstBody),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(secondBody),
            ).andExpect(status().isCreated)
    }

    @Test
    fun `url 이 빈 문자열이면 400 BAD_REQUEST 가 반환된다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        val body = objectMapper.writeValueAsString(mapOf("url" to "", "guestId" to UUID.randomUUID()))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isBadRequest)
    }
}
