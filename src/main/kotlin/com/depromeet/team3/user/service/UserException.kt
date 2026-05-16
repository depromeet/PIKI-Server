package com.depromeet.team3.user.service

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class UserException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        fun nicknameBlank(): UserException =
            UserException(
                "닉네임은 공백일 수 없습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun nicknameTooLong(): UserException =
            UserException(
                "닉네임은 16자 이하여야 합니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun alreadyMember(): UserException =
            UserException(
                "이미 MEMBER 입니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )
    }
}
