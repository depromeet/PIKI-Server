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
        fun notFoundTournament(): TournamentException =
            TournamentException(
                "토너먼트를 찾을 수 없습니다.",
                ErrorCategory.NOT_FOUND,
                HttpStatus.NOT_FOUND,
            )

        fun forbiddenTournament(): TournamentException =
            TournamentException(
                "해당 토너먼트에 접근할 권한이 없습니다.",
                ErrorCategory.FORBIDDEN,
                HttpStatus.FORBIDDEN,
            )

        fun alreadyCompleted(): TournamentException =
            TournamentException(
                "이미 완료된 토너먼트입니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.CONFLICT,
            )

        fun invalidWinner(): TournamentException =
            TournamentException(
                "승자는 대결한 두 아이템 중 하나여야 합니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
