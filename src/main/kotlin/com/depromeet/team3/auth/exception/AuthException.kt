package com.depromeet.team3.auth.exception

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class AuthException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        fun invalidToken(): AuthException =
            AuthException(
                "유효하지 않은 토큰입니다.",
                ErrorCategory.UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED,
            )

        fun missingNickname(): AuthException =
            AuthException(
                "MEMBER 생성 시 nickname 은 필수입니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
