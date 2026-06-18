package com.depromeet.piki.wishlist.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// 도메인 예외지만 HttpMappable 로 status·category 를 직접 들고 있다. 도메인이 전송 계층(HTTP)을 아는
// 형태는 순수 DDD 에선 피하지만, "사유 + status" 를 예외 정의 한 곳에서 보는 응집도를 위해 의식적으로
// 택한 트레이드오프다. status 매핑을 핸들러로 분리하는 대안은 #181 에서 검토.
class WishException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        // 위시리스트는 회원 전용 — 게스트(인증은 됐으나 회원 아님)가 정상 요청으로 닿을 수 있는 계약 응답이라 커스텀 예외(403).
        // Security 에서 MEMBER 만 허용하면 detail 없는 권한 없음 403 으로 떨어져 "회원 전용" 사유를 못 전달하므로,
        // authenticated() 로 통과시킨 뒤 서비스가 이 예외로 막는다(UserException.guestCannotWithdraw 와 같은 패턴).
        fun guestCannotUseWishlist(): WishException =
            WishException(
                "위시리스트는 회원만 이용할 수 있어요.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        fun forbiddenWishItems(): WishException =
            WishException(
                "내 위시 아이템만 볼 수 있어요.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        fun invalidCursor(): WishException =
            WishException(
                "페이지를 불러오지 못했어요. 새로고침 해주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun notFound(): WishException =
            WishException(
                "이미 삭제된 아이템이에요.",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )

        fun invalidImageCount(): WishException =
            WishException(
                "이미지는 1~5장만 올릴 수 있어요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun invalidIdCount(): WishException =
            WishException(
                "한 번에 최대 100개까지 삭제할 수 있어요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun notRefreshable(): WishException =
            WishException(
                "링크가 없는 항목은 새로고침할 수 없습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun failedNotRefreshable(): WishException =
            WishException(
                "추출에 실패한 항목은 새로고침 대신 정보를 직접 입력해 복구해 주세요.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )
    }
}
