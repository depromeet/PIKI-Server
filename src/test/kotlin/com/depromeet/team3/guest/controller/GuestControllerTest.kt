package com.depromeet.team3.guest.controller

import com.depromeet.team3.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.assertNotEquals

@Transactional
class GuestControllerTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `POST api v1 guests 는 UUID 형식의 guestId 를 반환한다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()

        val response =
            mockMvc
                .perform(post("/api/v1/guests"))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.guestId").exists())
                .andReturn()
                .response.contentAsString

        val guestId =
            objectMapper
                .readTree(response)
                .path("data")
                .path("guestId")
                .asString()
        UUID.fromString(guestId)
    }

    @Test
    fun `POST api v1 guests 는 매 요청마다 서로 다른 UUID 를 발급한다`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()

        val first =
            mockMvc
                .perform(post("/api/v1/guests"))
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
                .let {
                    objectMapper
                        .readTree(it)
                        .path("data")
                        .path("guestId")
                        .asString()
                }

        val second =
            mockMvc
                .perform(post("/api/v1/guests"))
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
                .let {
                    objectMapper
                        .readTree(it)
                        .path("data")
                        .path("guestId")
                        .asString()
                }

        assertNotEquals(first, second)
    }
}
