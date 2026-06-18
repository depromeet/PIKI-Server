package com.depromeet.piki.announcement.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// 공지 도메인 예외. WishException 과 같은 결로 status·category 를 예외 정의 한 곳에 박는다(HttpMappable).
// message 는 사용자 대면 고정 문구 — 내부 정보(미발송 공지 존재 등)를 노출하지 않는다.
class AnnouncementException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        // 발송 완료(SENT)되지 않았거나 존재하지 않는 공지. 미발송(DRAFT/SCHEDULED 등) 공지의 존재를 노출하지
        // 않기 위해 "없음"과 동일하게 404 로 응답한다.
        fun notFound(): AnnouncementException =
            AnnouncementException(
                "존재하지 않는 공지예요.",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )

        // 커서가 숫자로 변환되지 않는 등 잘못된 페이지 요청.
        fun invalidCursor(): AnnouncementException =
            AnnouncementException(
                "페이지를 불러오지 못했어요. 새로고침 해주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
