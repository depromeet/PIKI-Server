package com.depromeet.team3.user.exception

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
import org.springframework.http.HttpStatus
import java.util.UUID

class UserException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        fun notFound(userId: UUID): UserException =
            UserException(
                "유저를 찾을 수 없습니다. userId=$userId",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )
    }
}
