package com.depromeet.piki.metrics.dashboard

import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.sql.Timestamp
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 운영 통계 리포트 export(팀 공유용). 데이터 집계는 MetricsDashboardIntegrationTest 가 망라하므로 여기선
// export 만의 응답 계약을 검증한다: (1) attachment 다운로드 헤더, (2) text/html, (3) 외부 자산 링크 없는 자체완결 HTML.
@Transactional
class MetricsExportIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun mockMvc() =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun insertUser(createdAt: String) {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, identity_type, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            uuidToBytes(id),
            id.toString().take(10),
            "MEMBER",
            Timestamp.valueOf(createdAt),
            Timestamp.valueOf(createdAt),
        )
    }

    @Test
    fun `export 는 attachment 헤더와 text-html 로 자체완결 HTML 을 내려준다`() {
        val response =
            mockMvc()
                .perform(get("/admin/metrics/export").param("from", "2026-06-20T13:00").param("to", "2026-06-20T18:00"))
                .andExpect(status().isOk)
                .andExpect(view().name("admin/metrics-export"))
                .andReturn()
                .response

        // (1) attachment 다운로드 + 구간 기반 파일명
        val disposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION).orEmpty()
        assertContains(disposition, "attachment")
        assertContains(disposition, "piki-metrics-20260620-1300_20260620-1800.html")

        // (2) text/html
        assertTrue(response.contentType.orEmpty().startsWith(MediaType.TEXT_HTML_VALUE))

        // (3) 자체완결: 외부 stylesheet/script 링크가 없고, 인라인 style 로 렌더된다
        val body = response.contentAsString
        assertFalse(body.contains("rel=\"stylesheet\""), "외부 CSS 링크가 있으면 자체완결이 아니다")
        assertFalse(body.contains("<script"), "JS 가 있으면 자체완결 정적 리포트가 아니다")
        assertContains(body, "<style>")
        assertContains(body, "운영 통계 리포트")
    }

    @Test
    fun `export 본문에 대시보드와 같은 지표 섹션이 담긴다`() {
        insertUser("2026-06-20 06:00:00") // 구간 내(= 15:00 KST)

        val body =
            mockMvc()
                .perform(get("/admin/metrics/export").param("from", "2026-06-20T13:00").param("to", "2026-06-20T18:00"))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString

        // 대시보드와 동일한 핵심 섹션·구간 헤더가 들어 있다
        assertContains(body, "누적 가입자")
        assertContains(body, "직전 동기간 대비")
        assertContains(body, "2026-06-20 13:00")
    }
}
