package com.depromeet.team3.tournament.controller

import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.tournament.controller.dto.AddTournamentItemRequest
import com.depromeet.team3.tournament.controller.dto.CreateTournamentRequest
import com.depromeet.team3.tournament.controller.dto.CreateTournamentResponse
import com.depromeet.team3.tournament.controller.dto.RecordMatchRequest
import com.depromeet.team3.tournament.controller.dto.TournamentInfoResponse
import com.depromeet.team3.tournament.service.TournamentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tournaments")
class TournamentController(
    private val tournamentService: TournamentService,
) : TournamentApi {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun create(
        @RequestHeader("X-User-Id") userId: UUID,
        @RequestBody @Valid request: CreateTournamentRequest,
    ): ApiResponseBody<CreateTournamentResponse> {
        val tournamentId = tournamentService.create(userId, request.toCreateTournament())
        return ApiResponseBody.created(CreateTournamentResponse(tournamentId))
    }

    @PostMapping("/{tournamentId}/items")
    override fun addItem(
        @RequestHeader("X-User-Id") userId: UUID,
        @PathVariable tournamentId: Long,
        @RequestBody @Valid request: AddTournamentItemRequest,
    ): ApiResponseBody<Unit> {
        tournamentService.addItem(userId, request.toAddTournamentItem(tournamentId))
        return ApiResponseBody.ok()
    }

    @PostMapping("/{tournamentId}/start")
    override fun start(
        @RequestHeader("X-User-Id") userId: UUID,
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<Unit> {
        tournamentService.start(userId, tournamentId)
        return ApiResponseBody.ok()
    }

    @PostMapping("/{tournamentId}/matches")
    override fun recordMatch(
        @RequestHeader("X-User-Id") userId: UUID,
        @PathVariable tournamentId: Long,
        @RequestBody @Valid request: RecordMatchRequest,
    ): ApiResponseBody<Unit> {
        tournamentService.recordMatch(userId, request.toRecordMatch(tournamentId))
        return ApiResponseBody.ok()
    }

    @GetMapping("/{tournamentId}")
    override fun getTournamentById(
        @RequestHeader("X-User-Id") userId: UUID,
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<TournamentInfoResponse> {
        val tournamentInfo = tournamentService.getTournamentById(tournamentId, userId)
        return ApiResponseBody.ok(TournamentInfoResponse.from(tournamentInfo))
    }
}
