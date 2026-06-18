package com.depromeet.piki.common.imageproxy

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.user.domain.IdentityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class ImageProxyControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Test
    fun `토큰 없이 호출하면 401 응답이 반환된다`() {
        buildMockMvc()
            .perform(
                get("/api/v1/image-proxy")
                    .param("url", "https://msscdn.net/some/image.jpg"),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `허용되지 않은 도메인이면 400 blockedDomain 응답이 반환된다`() {
        val token = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.GUEST)

        buildMockMvc()
            .perform(
                get("/api/v1/image-proxy")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .param("url", "https://not-allowed-domain.com/image.jpg"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value(ImageProxyException.blockedDomain().message))
    }

    private fun buildMockMvc() =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
}
