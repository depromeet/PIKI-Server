package com.depromeet.piki.user.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
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

        fun duplicateNickname(): UserException =
            UserException(
                "이미 사용 중인 닉네임입니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun nicknameGenerationFailed(): UserException =
            UserException(
                "닉네임 생성에 실패했습니다. 다시 시도해주세요.",
                ErrorCategory.SERVER_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR,
            )

        fun invalidNickname(reason: String): UserException =
            UserException(
                reason,
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 게스트 탈퇴 거부 — 멀쩡한 클라이언트(게스트 토큰)가 정상 요청으로 닿을 수 있는 계약 응답이라 커스텀 예외(403).
        // 게스트는 보존할 PII 도, 스토어 요건상 "계정"도 없고, 공유 토너먼트 참조 때문에 하드삭제도 불가하다.
        fun guestCannotWithdraw(): UserException =
            UserException(
                "게스트는 탈퇴할 수 없습니다.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )
    }
}
