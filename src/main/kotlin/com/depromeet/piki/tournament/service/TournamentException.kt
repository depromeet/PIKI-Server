package com.depromeet.piki.tournament.service

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class TournamentException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        fun forbiddenTournament(): TournamentException =
            TournamentException(
                "해당 토너먼트에 대한 권한이 없습니다.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        fun notFoundTournament(): TournamentException =
            TournamentException(
                "토너먼트를 찾을 수 없습니다.",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )

        fun invalidWinner(): TournamentException =
            TournamentException(
                "승자는 대결한 두 아이템 중 하나여야 합니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun invalidTournamentItem(): TournamentException =
            TournamentException(
                "해당 토너먼트에 속하지 않는 아이템입니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun notPendingTournament(): TournamentException =
            TournamentException(
                "PENDING 상태인 토너먼트에만 수행할 수 있습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun notInProgressTournament(): TournamentException =
            TournamentException(
                "진행 중인 토너먼트에만 수행할 수 있습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun invalidItemCount(): TournamentException =
            TournamentException(
                "토너먼트 아이템은 최소 2개, 최대 32개여야 합니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun tooManyTournamentItems(): TournamentException =
            TournamentException(
                "토너먼트 아이템은 최대 32개까지 추가할 수 있습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun duplicateTournamentItem(): TournamentException =
            TournamentException(
                "이미 토너먼트에 등록된 아이템입니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun notFoundTournamentItem(): TournamentException =
            TournamentException(
                "토너먼트 아이템을 찾을 수 없습니다.",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )

        fun notFoundItems(): TournamentException =
            TournamentException(
                "존재하지 않는 아이템이 포함되어 있습니다.",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )

        // 비동기 파싱이 끝나지 않은(PENDING·PROCESSING) 또는 실패한(FAILED) 상품을 위시에서 토너먼트에 추가하려 한 경우.
        // 곧 READY 가 되거나 영구 실패라 현재 상태와 충돌 → 409.
        fun itemNotReady(): TournamentException =
            TournamentException(
                "아직 준비되지 않은 상품은 토너먼트에 추가할 수 없습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        // 토너먼트 시작 시 PENDING·PROCESSING·FAILED 아이템이 포함된 경우.
        fun itemNotReadyToStart(): TournamentException =
            TournamentException(
                "아직 준비되지 않은 상품이 포함되어 있어 토너먼트를 시작할 수 없습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        // 토너먼트 시작 시 가격 정보가 없는 아이템이 포함된 경우.
        // 가격 기준 정렬이 불가능하고 클라이언트가 아이템 추가 시점에 해당 상태를 유발할 수 있어 → 409.
        fun itemPriceRequired(): TournamentException =
            TournamentException(
                "가격 정보가 없는 상품이 포함되어 있어 토너먼트를 시작할 수 없습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun invalidImageCount(): TournamentException =
            TournamentException(
                "이미지는 최소 1개, 최대 5개까지 전송할 수 있습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun itemNotInWishlist(): TournamentException =
            TournamentException(
                "위시리스트에 없는 아이템은 토너먼트에 추가할 수 없습니다.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        fun eliminatedTournamentItem(): TournamentException =
            TournamentException(
                "이미 탈락한 아이템은 매치에 참가할 수 없습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun invalidCurrentRound(): TournamentException =
            TournamentException(
                "현재 진행할 수 없는 라운드입니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun inProgressTournamentCannotBeDeleted(): TournamentException =
            TournamentException(
                "진행 중인 토너먼트는 삭제할 수 없습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun invalidInviteCode(): TournamentException =
            TournamentException(
                "초대 코드가 올바르지 않습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun inviteExpired(): TournamentException =
            TournamentException(
                "초대 링크가 만료되었습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun alreadyParticipant(): TournamentException =
            TournamentException(
                "이미 해당 토너먼트에 참여 중입니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun notCompletedTournament(): TournamentException =
            TournamentException(
                "완료된 토너먼트에만 수행할 수 있습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun clonedTournamentCannotSharePlayLink(): TournamentException =
            TournamentException(
                "플레이 링크로 참여한 토너먼트는 플레이 링크를 생성할 수 없습니다.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        fun playLinkAlreadyCreated(): TournamentException =
            TournamentException(
                "플레이 링크가 이미 생성된 토너먼트입니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun playLinkNotCreated(): TournamentException =
            TournamentException(
                "플레이 링크가 생성되지 않은 토너먼트입니다.",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )

        fun playLinkExpired(): TournamentException =
            TournamentException(
                "플레이 링크가 만료되었습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun groupResultNotAvailable(): TournamentException =
            TournamentException(
                "그룹 결과를 조회할 수 없습니다. 완료된 토너먼트만 가능합니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun alreadyCloned(): TournamentException =
            TournamentException(
                "이미 해당 플레이 링크로 토너먼트를 생성하셨습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun participantLimitExceeded(): TournamentException =
            TournamentException(
                "토너먼트 참여 인원이 가득 찼습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        fun clonedTournamentCannotViewGroupResult(): TournamentException =
            TournamentException(
                "플레이 링크로 참여한 토너먼트에서는 친구 결과를 조회할 수 없습니다.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        fun clonedTournamentCannotAddItems(): TournamentException =
            TournamentException(
                "플레이 링크로 생성된 토너먼트에는 아이템을 추가할 수 없습니다.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )
    }
}
