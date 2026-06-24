package com.depromeet.piki.announcement.controller.dto

import com.depromeet.piki.announcement.domain.Announcement
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "공지사항 단건")
data class AnnouncementResponse(
    @field:Schema(description = "공지 id (알림 딥링크 refId 와 동일)", example = "42")
    val id: Long,
    @field:Schema(description = "제목", example = "서비스 점검 안내")
    val title: String,
    @field:Schema(
        description = "본문 (마크다운). 클라이언트는 마크다운으로 렌더한다 (제목/굵게/목록/링크 등).",
        example = "## 점검 안내\n6월 20일 **02:00~04:00** 점검이 예정되어 있어요.\n\n- 점검 중 일부 기능 제한\n- [자세히 보기](https://piki.day)",
    )
    val body: String,
    @field:Schema(description = "발송 시각(KST)", example = "2026-06-18T10:00:00")
    val sentAt: LocalDateTime,
) {
    companion object {
        // 도메인 → 응답 매핑 (받는 쪽이 책임). SENT 공지만 노출하므로 sentAt 은 markSent() 가 채워 항상 존재한다 —
        // 없으면 SENT 인데 sentAt 이 비었다는 불변식 위반(서비스 버그)이라 requireNotNull(500).
        fun from(announcement: Announcement): AnnouncementResponse =
            AnnouncementResponse(
                id = announcement.getId(),
                title = announcement.title,
                body = announcement.body,
                sentAt = requireNotNull(announcement.sentAt) { "SENT 공지에 sentAt 이 없다" },
            )
    }
}
