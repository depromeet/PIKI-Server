package com.depromeet.piki.admin.controller

import com.depromeet.piki.admin.chat.gemini.AdminGeminiResponse
import com.depromeet.piki.admin.chat.gemini.FunctionCall
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.product.service.gemini.GeminiApiException
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubAdminGeminiClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals

@Transactional
class AdminChatControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var stubAdminGeminiClient: StubAdminGeminiClient

    @Autowired
    private lateinit var itemRepository: ItemRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    @Test
    fun `미인증으로 백오피스에 접근하면 로그인 페이지로 리다이렉트된다`() {
        mockMvc()
            .perform(get("/admin"))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `로그인하면 백오피스 홈이 200 으로 렌더된다`() {
        mockMvc()
            .perform(get("/admin").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk)
    }

    @Test
    fun `로그인하면 AI 어시스턴트 채팅 페이지가 200 으로 렌더된다`() {
        mockMvc()
            .perform(get("/admin/chat").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk)
    }

    @Test
    fun `CSRF 토큰 없이 채팅 요청하면 403 이 반환된다`() {
        mockMvc()
            .perform(
                post("/admin/api/chat")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"message":"안녕"}"""),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `조회 도구 호출 후 모델이 최종 답변을 내면 MESSAGE 가 반환된다`() {
        val queue =
            ArrayDeque(
                listOf(
                    functionCallResponse("list_recent_items", mapOf("limit" to 5)),
                    textResponse("최근 등록된 항목이 없습니다."),
                ),
            )
        stubAdminGeminiClient.handler = { queue.removeFirst() }

        mockMvc()
            .perform(
                post("/admin/api/chat")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"message":"최근 5개 보여줘"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("MESSAGE"))
            .andExpect(jsonPath("$.text").value("최근 등록된 항목이 없습니다."))
    }

    @Test
    fun `추가 도구는 확인 카드를 거쳐 승인하면 DB 에 반영되고 MESSAGE 가 반환된다`() {
        val session = MockHttpSession()
        val chatQueue = ArrayDeque(listOf(functionCallResponse("insert_sample_items", mapOf("count" to 3))))
        stubAdminGeminiClient.handler = { chatQueue.removeFirst() }

        val chatResponse =
            mockMvc()
                .perform(
                    post("/admin/api/chat")
                        .session(session)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"message":"샘플 3개 추가해줘"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.type").value("CONFIRMATION"))
                .andReturn()

        val actionId = objectMapper.readTree(chatResponse.response.contentAsString).get("actionId").asText()

        val confirmQueue = ArrayDeque(listOf(textResponse("샘플 상품 3개를 추가했습니다.")))
        stubAdminGeminiClient.handler = { confirmQueue.removeFirst() }

        mockMvc()
            .perform(
                post("/admin/api/chat/confirm")
                    .session(session)
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actionId":"$actionId"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("MESSAGE"))
            .andExpect(jsonPath("$.text").value("샘플 상품 3개를 추가했습니다."))

        assertEquals(3, itemRepository.findRecent(10).size)
    }

    @Test
    fun `잘못된 actionId 로 확인 요청하면 ERROR 가 반환된다`() {
        mockMvc()
            .perform(
                post("/admin/api/chat/confirm")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actionId":"nonexistent"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("ERROR"))
    }

    @Test
    fun `Gemini 호출이 실패하면 ERROR 가 반환된다`() {
        stubAdminGeminiClient.handler = { throw GeminiApiException.upstreamError(RuntimeException("stub 실패")) }

        mockMvc()
            .perform(
                post("/admin/api/chat")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"message":"아무거나"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("ERROR"))
    }

    private fun textResponse(text: String): AdminGeminiResponse =
        responseWith(AdminGeminiResponse.ResponsePart(text = text))

    private fun functionCallResponse(
        name: String,
        args: Map<String, Any?>,
    ): AdminGeminiResponse = responseWith(AdminGeminiResponse.ResponsePart(functionCall = FunctionCall(name, args)))

    private fun responseWith(part: AdminGeminiResponse.ResponsePart): AdminGeminiResponse =
        AdminGeminiResponse(
            candidates =
                listOf(
                    AdminGeminiResponse.Candidate(
                        content = AdminGeminiResponse.ResponseContent(role = "model", parts = listOf(part)),
                    ),
                ),
        )
}
