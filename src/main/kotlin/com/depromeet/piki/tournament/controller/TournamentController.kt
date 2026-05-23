package com.depromeet.piki.tournament.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemsRequest
import com.depromeet.piki.tournament.controller.dto.CreateTournamentRequest
import com.depromeet.piki.tournament.controller.dto.CreateTournamentResponse
import com.depromeet.piki.tournament.controller.dto.RecordMatchRequest
import com.depromeet.piki.tournament.controller.dto.TournamentInfoResponse
import com.depromeet.piki.tournament.controller.dto.TournamentSummaryResponse
import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.service.TournamentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: CreateTournamentRequest,
    ): ApiResponseBody<CreateTournamentResponse> {
        val tournamentId = tournamentService.create(userId, request.toCreateTournament())
        return ApiResponseBody.created(CreateTournamentResponse(tournamentId))
    }

    @PostMapping("/{tournamentId}/items")
    override fun addItems(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
        @Valid @RequestBody request: AddTournamentItemsRequest,
    ): ApiResponseBody<Unit> {
        tournamentService.addItems(userId, request.toAddTournamentItems(tournamentId))
        return ApiResponseBody.ok()
    }

    @DeleteMapping("/{tournamentId}/items/{tournamentItemId}")
    override fun deleteItem(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
        @PathVariable tournamentItemId: Long,
    ): ApiResponseBody<Unit> {
        tournamentService.deleteItem(userId, tournamentId, tournamentItemId)
        return ApiResponseBody.ok()
    }

    @PostMapping("/{tournamentId}/start")
    override fun start(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<Unit> {
        tournamentService.start(userId, tournamentId)
        return ApiResponseBody.ok()
    }

    @PostMapping("/{tournamentId}/matches")
    override fun recordMatch(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
        @Valid @RequestBody request: RecordMatchRequest,
    ): ApiResponseBody<Unit> {
        tournamentService.recordMatch(userId, request.toRecordMatch(tournamentId))
        return ApiResponseBody.ok()
    }

    @GetMapping
    override fun getTournaments(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam(required = false) status: List<TournamentStatus>?,
    ): ApiResponseBody<List<TournamentSummaryResponse>> {
        val summaries = tournamentService.getTournaments(userId, status)
        return ApiResponseBody.ok(summaries.map(TournamentSummaryResponse::from))
    }

    @GetMapping("/{tournamentId}")
    override fun getTournamentById(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<TournamentInfoResponse> {
        val tournamentInfo = tournamentService.getTournamentById(tournamentId, userId)
        return ApiResponseBody.ok(TournamentInfoResponse.from(tournamentInfo))
    }
}
