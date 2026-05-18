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

        fun alreadyMember(userId: UUID): UserException =
            UserException(
                "이미 MEMBER 입니다. userId=$userId",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun deletedUser(userId: UUID): UserException =
            UserException(
                "탈퇴한 유저입니다. userId=$userId",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun invalidNickname(detail: String): UserException =
            UserException(detail, ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST)

        fun duplicateNickname(nickname: String): UserException =
            UserException(
                "이미 사용 중인 닉네임입니다. nickname=$nickname",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun nicknameGenerationFailed(): UserException =
            UserException(
                "닉네임 생성에 실패했습니다. 다시 시도해주세요.",
                ErrorCategory.SERVER_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR,
            )
    }
}
