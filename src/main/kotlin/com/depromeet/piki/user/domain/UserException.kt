package com.depromeet.piki.user.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// userId 등 내부 식별자는 응답 detail 로 노출하지 않는다(GlobalExceptionHandler 가 message 를 detail 로 내보냄).
// 디버깅에 필요한 userId 는 인증 컨텍스트·trace 로 추적 가능하므로 message 에는 고정 사용자 문구만 둔다.
class UserException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        fun notFound(): UserException =
            UserException(
                "존재하지 않는 계정이에요.",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )

        fun alreadyMember(): UserException =
            UserException(
                "이미 가입된 계정이에요.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun deletedUser(): UserException =
            UserException(
                "탈퇴한 계정이에요.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun duplicateNickname(): UserException =
            UserException(
                "이미 누군가 쓰고 있는 닉네임이에요. 다른 걸 입력해 주세요.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun nicknameGenerationFailed(): UserException =
            UserException(
                "닉네임을 만들지 못했어요. 잠시 후 다시 시도해 주세요.",
                ErrorCategory.SERVER_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR,
            )

        // reason 은 User.validateNickname 이 넘기는 고정 사용자 문구다(임의 입력·LLM 원문이 아님).
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
                "게스트는 탈퇴할 수 없어요.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        // 프로필 이미지 수정은 MEMBER 전용 — 게스트가 이미지 파트를 담아 PATCH /me 를 호출하면 닿는 계약 응답(403).
        // 게스트 토큰으로 정상 요청을 보낼 수 있으므로 require/check(500)가 아니라 커스텀 예외다(guestCannotWithdraw 와 같은 결).
        fun guestCannotUpdateProfileImage(): UserException =
            UserException(
                "프로필 이미지는 회원만 바꿀 수 있어요.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        fun emptyProfileImage(): UserException =
            UserException(
                "빈 이미지 파일은 올릴 수 없어요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun unsupportedProfileImageType(): UserException =
            UserException(
                "지원하지 않는 이미지 형식이에요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 선언한 Content-Type 과 실제 파일 바이트(매직바이트)가 어긋나거나 이미지로 해석되지 않을 때.
        // Content-Type 헤더는 클라이언트가 위조할 수 있으므로 실제 시그니처로 교차검증한다.
        fun malformedProfileImage(): UserException =
            UserException(
                "이미지 파일이 손상되었거나 형식이 맞지 않아요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
