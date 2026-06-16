package com.depromeet.piki.auth.exception

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
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
                "로그인 정보가 만료됐어요. 다시 로그인해 주세요.",
                ErrorCategory.UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED,
            )

        fun missingNickname(): AuthException =
            AuthException(
                "닉네임을 입력해 주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // refresh 토큰이 쿠키·body 어느 쪽에도 없을 때. 정상 클라이언트가 도달 가능한 계약 → 400.
        fun refreshTokenRequired(): AuthException =
            AuthException(
                "다시 로그인해 주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
