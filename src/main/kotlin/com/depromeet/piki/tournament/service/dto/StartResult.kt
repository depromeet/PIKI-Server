package com.depromeet.piki.tournament.service.dto

data class StartResult(
    val tournamentId: Long,
    val items: List<TournamentStartResult>,
)
