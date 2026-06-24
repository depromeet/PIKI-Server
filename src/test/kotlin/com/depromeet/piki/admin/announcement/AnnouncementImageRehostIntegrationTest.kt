package com.depromeet.piki.admin.announcement

import com.depromeet.piki.admin.announcement.FetchedImage
import com.depromeet.piki.announcement.domain.Announcement
import com.depromeet.piki.announcement.domain.AnnouncementBodyImages
import com.depromeet.piki.announcement.domain.AnnouncementImageException
import com.depromeet.piki.announcement.repository.AnnouncementRepository
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubAnnouncementImageFetcher
import com.depromeet.piki.support.StubImageStorage
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 공지 본문 이미지(#561) 통합테스트. 외부 fetch·S3 업로드는 stub 으로 격리하고, 컨트롤러 POST 부터 DB 까지 실제 흐름으로
// (1) 수정 시 외부 URL → 우리 S3 rehost (2) 이미 우리 S3 면 재fetch 생략 (3) 삭제 시 prefix 정리 (4) fetch 실패 처리
// (5) 로컬 파일 즉시 업로드 엔드포인트를 검증한다. 작성은 stub-create 후 수정 화면으로 일원화돼 rehost 는 edit 에서 일어난다.
@Transactional
class AnnouncementImageRehostIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Autowired private lateinit var announcementRepository: AnnouncementRepository

    @Autowired private lateinit var stubFetcher: StubAnnouncementImageFetcher

    @Autowired private lateinit var stubImageStorage: StubImageStorage

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun pngBytes() =
        ByteArray(12) { intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)[it].toByte() }

    private fun draftId(): Long = announcementRepository.save(Announcement("제목", "", "토큰 보유자 전체")).getId()

    @Test
    fun `수정 시 본문의 외부 이미지 URL 이 우리 S3 로 rehost 되고 본문이 치환된다`() {
        stubFetcher.requestedUrls.clear()
        stubImageStorage.uploadedKeys.clear()
        stubFetcher.behavior = { FetchedImage(pngBytes(), "image/png") }
        val id = draftId()

        mockMvc()
            .perform(
                post("/admin/announcements/$id/edit")
                    .param("title", "이미지 공지")
                    .param("body", "## 안내\n![배너](https://ext.example.com/banner.png)")
                    .param("pushEnabled", "true")
                    .with(csrf()),
            ).andExpect(status().is3xxRedirection)

        val saved = announcementRepository.findById(id).get()
        val imageUrl = AnnouncementBodyImages.urls(saved.body).single()
        assertTrue(
            imageUrl.startsWith("${StubImageStorage.BASE_URL}/announcement/$id/"),
            "본문 이미지가 우리 S3 로 치환되어야 함: $imageUrl",
        )
        assertFalse(saved.body.contains("ext.example.com"), "외부 URL 이 본문에 남으면 안 됨")
        assertTrue(stubImageStorage.uploadedKeys.any { it.startsWith("announcement/$id/") && it.endsWith(".png") })
    }

    @Test
    fun `이미 우리 S3 인 이미지 URL 은 재저장 시 다시 fetch 하지 않는다`() {
        stubFetcher.behavior = { FetchedImage(pngBytes(), "image/png") }
        val id = draftId()

        // 1) 외부 URL 1건 rehost
        mockMvc()
            .perform(
                post("/admin/announcements/$id/edit")
                    .param("title", "공지")
                    .param("body", "![배너](https://ext.example.com/banner.png)")
                    .param("pushEnabled", "true")
                    .with(csrf()),
            ).andExpect(status().is3xxRedirection)
        val ourS3Url = AnnouncementBodyImages.urls(announcementRepository.findById(id).get().body).single()
        stubFetcher.requestedUrls.clear()

        // 2) 본문이 이미 우리 S3 URL 이면 다시 fetch 하지 않아야 한다
        mockMvc()
            .perform(
                post("/admin/announcements/$id/edit")
                    .param("title", "공지 수정")
                    .param("body", "![배너]($ourS3Url)")
                    .param("pushEnabled", "true")
                    .with(csrf()),
            ).andExpect(status().is3xxRedirection)

        assertTrue(stubFetcher.requestedUrls.isEmpty(), "우리 S3 URL 은 재fetch 하지 않아야 함: ${stubFetcher.requestedUrls}")
    }

    @Test
    fun `초안 삭제 시 그 공지의 S3 이미지가 prefix 로 정리된다`() {
        stubFetcher.behavior = { FetchedImage(pngBytes(), "image/png") }
        stubImageStorage.deletedPrefixes.clear()
        val id = draftId()

        mockMvc()
            .perform(
                post("/admin/announcements/$id/edit")
                    .param("title", "삭제될 공지")
                    .param("body", "![배너](https://ext.example.com/banner.png)")
                    .param("pushEnabled", "true")
                    .with(csrf()),
            ).andExpect(status().is3xxRedirection)

        mockMvc()
            .perform(post("/admin/announcements/$id/delete").with(csrf()))
            .andExpect(status().is3xxRedirection)

        assertTrue(stubImageStorage.deletedPrefixes.contains("announcement/$id/"))
    }

    @Test
    fun `외부 이미지 fetch 실패 시 수정이 image 에러로 리다이렉트된다`() {
        stubFetcher.behavior = { throw AnnouncementImageException.fetchFailed() }
        val id = draftId()

        mockMvc()
            .perform(
                post("/admin/announcements/$id/edit")
                    .param("title", "깨진 이미지 공지")
                    .param("body", "![배너](https://ext.example.com/dead.png)")
                    .param("pushEnabled", "true")
                    .with(csrf()),
            ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/announcements/$id/edit?error=image"))
    }

    @Test
    fun `로컬 파일 이미지 업로드는 S3 에 올리고 공개 URL 을 돌려준다`() {
        stubImageStorage.uploadedKeys.clear()
        val id = draftId()

        mockMvc()
            .perform(
                multipart("/admin/announcements/$id/images")
                    .file(MockMultipartFile("image", "shot.png", "image/png", pngBytes()))
                    .with(csrf()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.url").value(startsWith("${StubImageStorage.BASE_URL}/announcement/$id/")))

        assertTrue(stubImageStorage.uploadedKeys.any { it.startsWith("announcement/$id/") && it.endsWith(".png") })
    }

    @Test
    fun `이미지 아닌 파일 업로드는 400 으로 거부된다`() {
        val id = draftId()

        mockMvc()
            .perform(
                multipart("/admin/announcements/$id/images")
                    .file(MockMultipartFile("image", "x.png", "image/png", "not an image".toByteArray()))
                    .with(csrf()),
            ).andExpect(status().isBadRequest)
    }
}
