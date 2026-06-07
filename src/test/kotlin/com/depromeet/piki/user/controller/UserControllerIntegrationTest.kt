package com.depromeet.piki.user.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.common.storage.ImageStorageException
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubImageStorage
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.controller.dto.NicknameCheckRequest
import com.depromeet.piki.user.controller.dto.UserUpdateRequest
import com.depromeet.piki.user.domain.IdentityType
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Transactional
class UserControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var stubImageStorage: StubImageStorage

    private fun insertUser(
        userId: UUID,
        nickname: String = userId.toString().take(10),
        identityType: IdentityType = IdentityType.GUEST,
    ) {
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, profile_image, identity_type, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            nickname,
            "https://example.com/img.png",
            identityType.name,
        )
    }

    private fun insertUserDetail(
        userId: UUID,
        provider: String = "GOOGLE",
        socialId: String = userId.toString(),
        email: String? = null,
    ) {
        jdbcTemplate.update(
            "INSERT INTO user_details (user_id, provider, social_id, email, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            provider,
            socialId,
            email,
        )
    }

    private fun token(
        userId: UUID,
        identityType: IdentityType = IdentityType.GUEST,
    ): String = jwtProvider.generateAccessToken(userId, identityType)

    // 매직바이트 교차검증을 통과하는 최소 유효 시그니처 (실제 픽셀 데이터는 검증과 무관).
    private fun jpegBytes(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0)

    @Test
    fun `GET users me - 게스트가 자기 정보를 200 으로 조회한다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, nickname = "테스트닉네임", identityType = IdentityType.GUEST)

        mockMvc
            .perform(
                get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.GUEST)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(userId.toString()))
            .andExpect(jsonPath("$.data.nickname").value("테스트닉네임"))
            .andExpect(jsonPath("$.data.identityType").value("GUEST"))
            .andExpect(jsonPath("$.data.profileImage").isString)
            // 게스트는 소셜 계정(user_details)이 없으므로 email 은 null 로 내려온다.
            .andExpect(jsonPath("$.data.email").value(null))
    }

    @Test
    fun `GET users me - 소셜 회원은 수집된 email 을 함께 받는다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, nickname = "회원닉네임", identityType = IdentityType.MEMBER)
        insertUserDetail(userId, provider = "GOOGLE", email = "member@gmail.com")

        mockMvc
            .perform(
                get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.MEMBER)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.identityType").value("MEMBER"))
            // user_details 의 email 이 마이페이지 응답으로 내려오는지 contract 고정.
            .andExpect(jsonPath("$.data.email").value("member@gmail.com"))
    }

    @Test
    fun `GET users me - email 미동의 회원은 email 이 null 로 내려온다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, identityType = IdentityType.MEMBER)
        // 소셜 계정은 있지만 email 미수집(애플 Private Relay 거부 등) — user_details.email 이 null.
        insertUserDetail(userId, provider = "APPLE", email = null)

        mockMvc
            .perform(
                get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.MEMBER)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.email").value(null))
    }

    @Test
    fun `GET users me - 인증 헤더 없으면 401 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `PATCH users me - 게스트가 닉네임을 수정하면 200 과 변경된 닉네임이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, nickname = "초기닉네임", identityType = IdentityType.GUEST)
        val body = objectMapper.writeValueAsString(mapOf("nickname" to "새닉네임"))

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.GUEST)}")
                    .content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
            // 수정 응답도 조회와 동일한 내 정보 모양 — 게스트는 email 칸이 null 로 항상 포함된다.
            .andExpect(jsonPath("$.data.email").value(null))
    }

    @Test
    fun `PATCH users me - 소셜 회원이 닉네임을 수정하면 email 도 함께 내려온다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, nickname = "초기닉네임", identityType = IdentityType.MEMBER)
        insertUserDetail(userId, provider = "GOOGLE", email = "member@gmail.com")
        val body = objectMapper.writeValueAsString(mapOf("nickname" to "수정닉네임"))

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.MEMBER)}")
                    .content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("수정닉네임"))
            // 수정 응답이 조회(GET /me)와 동일하게 email 을 포함하는지 contract 고정.
            .andExpect(jsonPath("$.data.email").value("member@gmail.com"))
    }

    @Test
    fun `PATCH users me - 닉네임 11자는 400 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)
        val body = objectMapper.writeValueAsString(mapOf("nickname" to "12345678901"))

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}")
                    .content(body),
            ).andExpect(status().isBadRequest)
            // @RequestBody 검증 실패 응답 detail 이 OpenAPI example(UserApiExamples updateMe 400)과 같은 형식인지 고정.
            .andExpect(jsonPath("$.detail").value("nickname: ${UserUpdateRequest.NICKNAME_SIZE_MESSAGE}"))
    }

    @Test
    fun `PATCH users me - 이미 사용 중인 닉네임으로 변경하면 409 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val otherUserId = UUID.randomUUID()
        insertUser(otherUserId, nickname = "점유닉네임")
        val myUserId = UUID.randomUUID()
        insertUser(myUserId, nickname = "내닉네임")
        val body = objectMapper.writeValueAsString(mapOf("nickname" to "점유닉네임"))

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(myUserId)}")
                    .content(body),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `GET users nickname check - 사용 가능한 닉네임이면 available true 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)

        mockMvc
            .perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "안쓰는닉네임")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.available").value(true))
    }

    @Test
    fun `GET users nickname check - 다른 user 가 이미 점유한 닉네임이면 available false 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val otherUserId = UUID.randomUUID()
        insertUser(otherUserId, nickname = "점유닉네임")
        val myUserId = UUID.randomUUID()
        insertUser(myUserId, nickname = "내닉네임")

        mockMvc
            .perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "점유닉네임")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(myUserId)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.available").value(false))
    }

    @Test
    fun `GET users nickname check - 본인이 자기 닉네임으로 호출하면 본인 제외로 available true 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, nickname = "내닉네임")

        mockMvc
            .perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "내닉네임")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.available").value(true))
    }

    @Test
    fun `GET users nickname check - JWT 없이도 200 을 반환한다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "안쓰는닉네임"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.available").value(true))
    }

    @Test
    fun `PATCH users me - 본인이 자기 닉네임 그대로 보내면 200 으로 통과한다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, nickname = "내닉네임")
        val body = objectMapper.writeValueAsString(mapOf("nickname" to "내닉네임"))

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}")
                    .content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("내닉네임"))
    }

    @Test
    fun `GET users nickname check - 11자 닉네임은 400 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)

        mockMvc
            .perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "12345678901")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isBadRequest)
            // 쿼리 파라미터 POJO(암묵 @ModelAttribute) 검증 실패도 같은 "필드명: 메시지" 형식인지 실측·고정.
            .andExpect(jsonPath("$.detail").value("nickname: ${NicknameCheckRequest.NICKNAME_SIZE_MESSAGE}"))
    }

    @Test
    fun `POST users me profile-image - 이미지를 업로드하면 200 과 갱신된 프로필 URL 을 반환한다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)
        val image = MockMultipartFile("image", "photo.jpg", "image/jpeg", jpegBytes())

        mockMvc
            .perform(
                multipart("/api/v1/users/me/profile-image")
                    .file(image)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isOk)
            // key 규칙 profiles/{userId}/{uuid}.jpg 로 업로드되어 stub URL 이 내려온다.
            .andExpect(
                jsonPath("$.data.profileImage")
                    .value(startsWith("${StubImageStorage.BASE_URL}/profiles/$userId/")),
            )
            // 수정 응답도 조회와 동일한 내 정보 모양 — 게스트는 email 칸이 null 로 항상 포함된다.
            .andExpect(jsonPath("$.data.email").value(null))
    }

    @Test
    fun `POST users me profile-image - gif 는 지원하지 않아 400 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)
        val gif = MockMultipartFile("image", "anim.gif", "image/gif", byteArrayOf(1, 2, 3))

        mockMvc
            .perform(
                multipart("/api/v1/users/me/profile-image")
                    .file(gif)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value(startsWith("지원하지 않는 이미지 형식입니다.")))
    }

    @Test
    fun `POST users me profile-image - 빈 이미지 파일은 400 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)
        val empty = MockMultipartFile("image", "empty.png", "image/png", ByteArray(0))

        mockMvc
            .perform(
                multipart("/api/v1/users/me/profile-image")
                    .file(empty)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("빈 이미지 파일은 업로드할 수 없습니다."))
    }

    @Test
    fun `POST users me profile-image - 토큰이 없으면 401 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val image = MockMultipartFile("image", "photo.png", "image/png", byteArrayOf(1, 2, 3))

        mockMvc
            .perform(
                multipart("/api/v1/users/me/profile-image").file(image),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST users me profile-image - S3 업로드 실패 시 502 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)
        val image = MockMultipartFile("image", "photo.jpg", "image/jpeg", jpegBytes())
        stubImageStorage.behavior = { _, _, _ -> throw ImageStorageException.uploadFailed() }

        try {
            mockMvc
                .perform(
                    multipart("/api/v1/users/me/profile-image")
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
                ).andExpect(status().isBadGateway)
        } finally {
            // 공유 컨텍스트의 stub mutable state 를 복원해 다른 테스트로 누수되지 않게 한다.
            stubImageStorage.behavior = stubImageStorage.defaultBehavior
        }
    }

    @Test
    fun `POST users me profile-image - 선언한 형식과 실제 파일 내용이 다르면 400 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)
        // Content-Type 은 image/png 로 선언했지만 실제 바이트는 JPEG 시그니처 (헤더 위조 시나리오).
        val mismatched = MockMultipartFile("image", "fake.png", "image/png", jpegBytes())

        mockMvc
            .perform(
                multipart("/api/v1/users/me/profile-image")
                    .file(mismatched)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("이미지 파일이 손상되었거나 형식과 내용이 일치하지 않습니다."))
    }

    @Test
    fun `POST users me profile-image - image 파트가 아예 없으면 400 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)

        // 파일 파트 없이 호출 — 컨트롤러의 `image ?: throw emptyProfileImage()` 분기가 프레임워크 500 이 아닌
        // 400 도메인 응답으로 떨어지는지 고정한다 (required=false + Elvis 방어).
        mockMvc
            .perform(
                multipart("/api/v1/users/me/profile-image")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("빈 이미지 파일은 업로드할 수 없습니다."))
    }
}
