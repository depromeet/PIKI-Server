package com.depromeet.piki.notification.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// 알림 도메인 커스텀 예외. HttpMappable 로 status·category 를 예외 정의 한 곳에 박는다(WishException 과 동일 트레이드오프).
class NotificationException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        fun invalidCursor(): NotificationException =
            NotificationException(
                "유효하지 않은 cursor 입니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
