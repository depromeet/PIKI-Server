package com.depromeet.team3.wishlist.service

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
import org.springframework.http.HttpStatus

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
    }
}
