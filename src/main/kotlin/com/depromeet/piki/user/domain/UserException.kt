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

        fun emptyProfileImage(): UserException =
            UserException(
                "빈 이미지 파일은 업로드할 수 없습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 지원 형식 목록은 클라이언트 안내 목적이라 노출돼도 안전하다(내부 정보 아님).
        fun unsupportedProfileImageType(): UserException =
            UserException(
                "지원하지 않는 이미지 형식입니다. (지원: ${ProfileImageFile.SUPPORTED_MIME_TYPES.joinToString()})",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 선언한 Content-Type 과 실제 파일 바이트(매직바이트)가 어긋나거나 이미지로 해석되지 않을 때.
        // Content-Type 헤더는 클라이언트가 위조할 수 있으므로 실제 시그니처로 교차검증한다.
        fun malformedProfileImage(): UserException =
            UserException(
                "이미지 파일이 손상되었거나 형식과 내용이 일치하지 않습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
