package com.depromeet.piki.announcement.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Announcement 도메인 불변식·상태분기 단위테스트(#561) — Spring·DB 없이 검증한다.
class AnnouncementTest {
    private fun draft(
        title: String = "공지 제목",
        body: String = "공지 본문",
        pushEnabled: Boolean = true,
        pushTitle: String = "",
        pushBody: String = "",
    ) = Announcement(title, body, "토큰 보유자 전체", pushEnabled, pushTitle, pushBody)

    @Test
    fun `DRAFT 공지는 edit 로 내용·푸시필드가 바뀐다`() {
        val a = draft()
        a.edit(title = "새 제목", body = "## 마크다운\n새 본문", pushEnabled = false, pushTitle = "푸시 제목", pushBody = "푸시 본문")

        assertEquals("새 제목", a.title)
        assertEquals("## 마크다운\n새 본문", a.body)
        assertFalse(a.pushEnabled)
        assertEquals("푸시 제목", a.pushTitle)
        assertEquals("푸시 본문", a.pushBody)
    }

    @Test
    fun `발송 예약된 공지는 edit 가 거부된다 - DRAFT 에서만 수정`() {
        val a = draft()
        a.schedule(LocalDateTime.now(Announcement.KST).plusHours(1)) // DRAFT → SCHEDULED

        assertFailsWith<IllegalStateException> {
            a.edit("새 제목", "새 본문", true, "", "")
        }
    }

    @Test
    fun `edit 도 길이 검증을 한다 - body 가 상한을 넘으면 거부`() {
        val a = draft()
        assertFailsWith<IllegalArgumentException> {
            a.edit("제목", "x".repeat(Announcement.MAX_BODY_LENGTH + 1), true, "", "")
        }
    }

    @Test
    fun `edit 도 푸시 문구 길이를 검증한다 - pushTitle 256자 거부`() {
        val a = draft()
        assertFailsWith<IllegalArgumentException> {
            a.edit("제목", "본문", true, "x".repeat(Announcement.MAX_PUSH_TEXT_LENGTH + 1), "")
        }
    }

    @Test
    fun `pushTitle 이 비면 effectivePushTitle 은 공지 title 로 폴백한다`() {
        val a = draft(title = "공지 제목", pushTitle = "")
        assertEquals("공지 제목", a.effectivePushTitle)
        assertEquals("", a.effectivePushBody)
    }

    @Test
    fun `pushTitle 이 있으면 그 값을 쓴다`() {
        val a = draft(title = "공지 제목", pushTitle = "푸시 전용 제목", pushBody = "푸시 본문")
        assertEquals("푸시 전용 제목", a.effectivePushTitle)
        assertEquals("푸시 본문", a.effectivePushBody)
    }

    @Test
    fun `새 공지는 DRAFT 다`() {
        assertTrue(draft().isDraft)
    }
}
