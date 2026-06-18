package com.depromeet.piki.announcement.service

import com.depromeet.piki.announcement.domain.Announcement
import com.depromeet.piki.announcement.domain.AnnouncementException
import com.depromeet.piki.announcement.repository.AnnouncementRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

    // 목록(공지사항 페이지) — SENT 만 최신순(id desc), 커서 페이지네이션. size+1 을 조회해 hasNext 를 판정하고
    // 초과분을 잘라 다음 커서(마지막 항목 id)를 만든다.
    @Transactional(readOnly = true)
    fun listSent(
        cursor: Long?,
        size: Int,
    ): AnnouncementPage {
        val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val pageable = PageRequest.of(0, pageSize + 1)
        val rows =
            cursor
                ?.let { announcementRepository.findByStatusAndIdLessThanOrderByIdDesc(Announcement.STATUS_SENT, it, pageable) }
                ?: announcementRepository.findByStatusOrderByIdDesc(Announcement.STATUS_SENT, pageable)
        val hasNext = rows.size > pageSize
        val items = if (hasNext) rows.dropLast(1) else rows
        val nextCursor = if (hasNext) items.last().getId() else null
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
    val nextCursor: Long?,
    val hasNext: Boolean,
)
