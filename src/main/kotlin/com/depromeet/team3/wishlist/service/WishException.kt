package com.depromeet.team3.wishlist.service

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
import com.depromeet.team3.product.domain.ProductLink
import org.springframework.http.HttpStatus
import java.util.UUID

class WishException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message), HttpMappable {

    companion object {
        fun alreadyExists(
            guestId: UUID,
            link: ProductLink,
        ): WishException = WishException(
            "이미 위시리스트에 등록된 상품입니다. guestId=$guestId link=$link",
            ErrorCategory.CONFLICT,
            HttpStatus.CONFLICT,
        )

        fun forbiddenWishItems(): WishException = WishException(
            "해당 위시 아이템에 접근할 권한이 없습니다.",
            ErrorCategory.FORBIDDEN,
            HttpStatus.FORBIDDEN,
        )
    }
}
