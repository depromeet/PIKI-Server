package com.depromeet.team3.wishlist.domain

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// 도메인 예외지만 HttpMappable 로 status·category 를 직접 들고 있다. 도메인이 전송 계층(HTTP)을 아는
// 형태는 순수 DDD 에선 피하지만, "사유 + status" 를 예외 정의 한 곳에서 보는 응집도를 위해 의식적으로
// 택한 트레이드오프다. status 매핑을 핸들러로 분리하는 대안은 #181 에서 검토.
class WishException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        fun forbiddenWishItems(): WishException =
            WishException(
                "해당 위시 아이템에 접근할 권한이 없습니다.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        fun invalidCursor(): WishException =
            WishException(
                "유효하지 않은 cursor 입니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun notFound(): WishException =
            WishException(
                "존재하지 않는 위시리스트 항목입니다.",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )
    }
}
