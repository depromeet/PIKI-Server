package com.depromeet.piki.announcement.service

import com.depromeet.piki.announcement.domain.Announcement
import com.depromeet.piki.announcement.domain.AnnouncementException
import com.depromeet.piki.announcement.repository.AnnouncementRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Base64

// 유저용 공지 조회(#556) — 발송 완료(SENT)된 공지만 노출한다. 작성·발송은 admin 이, 조회는 여기서.
@Service
class AnnouncementQueryService(
    private val announcementRepository: AnnouncementRepository,
) {
    // 단건(딥링크 착지). SENT 가 아니거나 없으면 404 — 미발송 공지의 존재를 노출하지 않는다.
    @Transactional(readOnly = true)
    fun getSent(id: Long): Announcement =
        announcementRepository.findByIdAndStatus(id, Announcement.STATUS_SENT)
            ?: throw AnnouncementException.notFound()

    // 목록(공지사항 페이지) — SENT 만 발송순(sentAt desc, 동시각 tie-breaker id desc), 커서 페이지네이션.
    // 정렬 키를 id 가 아니라 sentAt 으로 둔다 — 예약 발송으로 등록순(id)과 발송순이 어긋나도 목록이 실제 발송순을 따르게.
    // size+1 을 조회해 hasNext 를 판정하고, 초과분을 잘라 다음 커서(마지막 항목의 sentAt·id)를 만든다.
    @Transactional(readOnly = true)
    fun listSent(
        cursor: AnnouncementCursor?,
        size: Int,
    ): AnnouncementPage {
        val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val pageable = PageRequest.of(0, pageSize + 1)
        val rows =
            cursor
                ?.let {
                    announcementRepository.findNextByStatusAndSentAtCursor(Announcement.STATUS_SENT, it.sentAt, it.id, pageable)
                }
                ?: announcementRepository.findByStatusOrderBySentAtDescIdDesc(Announcement.STATUS_SENT, pageable)
        val hasNext = rows.size > pageSize
        val items = if (hasNext) rows.dropLast(1) else rows
        val nextCursor =
            items.lastOrNull()
                ?.takeIf { hasNext }
                ?.let { AnnouncementCursor(sentAt = requireNotNull(it.sentAt) { "SENT 공지에 sentAt 이 없다" }, id = it.getId()) }
        return AnnouncementPage(items = items, nextCursor = nextCursor, hasNext = hasNext)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
    }
}

// 커서 페이지 결과 — 컨트롤러가 ApiResponseBody(data + pageResponse)로 펼친다.
data class AnnouncementPage(
    val items: List<Announcement>,
    val nextCursor: AnnouncementCursor?,
    val hasNext: Boolean,
)

// 복합 커서 — 정렬 키(sentAt, id)를 그대로 담는다. 클라이언트엔 base64 한 줄(opaque)로 인코딩해 내려가고, 다시 받아 디코딩한다.
// 단일 id 가 아니라 (sentAt, id) 두 좌표가 필요한 이유는 sentAt 동시각 tie-breaker(id) 때문.
data class AnnouncementCursor(
    val sentAt: LocalDateTime,
    val id: Long,
) {
    // "{ISO-8601 LocalDateTime}_{id}" 를 base64 URL-safe 로 — 내부 정렬 키를 노출하지 않고 한 토큰으로 주고받는다.
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$sentAt$DELIM$id".toByteArray())

    companion object {
        private const val DELIM = "_"

        // 잘못된 커서(임의 문자열·변조)는 null → 컨트롤러가 400 으로 변환한다. ISO LocalDateTime 엔 '_' 가 없어 마지막 '_' 로 가른다.
        fun decode(raw: String): AnnouncementCursor? =
            runCatching {
                val decoded = String(Base64.getUrlDecoder().decode(raw))
                val sep = decoded.lastIndexOf(DELIM)
                if (sep < 0) return null
                AnnouncementCursor(
                    sentAt = LocalDateTime.parse(decoded.substring(0, sep)),
                    id = decoded.substring(sep + 1).toLong(),
                )
            }.getOrNull()
    }
}
