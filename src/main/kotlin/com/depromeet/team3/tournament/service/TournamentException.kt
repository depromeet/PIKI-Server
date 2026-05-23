package com.depromeet.team3.tournament.service

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
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
                ErrorCategory.INVALID_INPUT,
                HttpStatus.CONFLICT,
            )

        fun notInProgressTournament(): TournamentException =
            TournamentException(
                "진행 중인 토너먼트에만 수행할 수 있습니다.",
                ErrorCategory.INVALID_INPUT,
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
    }
}
