package com.depromeet.piki.tournament.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.tournament.controller.dto.CreateTournamentRequest
import com.depromeet.piki.tournament.controller.dto.CreateTournamentResponse
import com.depromeet.piki.tournament.controller.dto.GroupResultResponse
import com.depromeet.piki.tournament.controller.dto.JoinTournamentAsGuestRequest
import com.depromeet.piki.tournament.controller.dto.JoinTournamentAsGuestResponse
import com.depromeet.piki.tournament.controller.dto.JoinTournamentRequest
import com.depromeet.piki.tournament.controller.dto.PlayLinkInfoResponse
import com.depromeet.piki.tournament.controller.dto.RecordMatchRequest
import com.depromeet.piki.tournament.controller.dto.TournamentDetailResponse
import com.depromeet.piki.tournament.controller.dto.TournamentInvitePreviewResponse
import com.depromeet.piki.tournament.controller.dto.TournamentStartResponse
import com.depromeet.piki.tournament.controller.dto.TournamentSummaryResponse
import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.service.TournamentInviteService
import com.depromeet.piki.tournament.service.TournamentItemService
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
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tournaments")
class TournamentController(
    private val tournamentService: TournamentService,
    private val tournamentItemService: TournamentItemService,
    private val tournamentInviteService: TournamentInviteService,
) : TournamentApi {

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
    ): ApiResponseBody<TournamentDetailResponse> {
        val detail = tournamentService.getTournamentById(tournamentId, userId)
        return ApiResponseBody.ok(TournamentDetailResponse.from(detail))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun create(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: CreateTournamentRequest,
    ): ApiResponseBody<CreateTournamentResponse> {
        val result = tournamentService.create(userId, request.toCreateTournament())
        return ApiResponseBody.created(CreateTournamentResponse.from(result))
    }

    @PostMapping("/{tournamentId}/join")
    override fun join(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
        @Valid @RequestBody request: JoinTournamentRequest,
    ): ApiResponseBody<Unit> {
        tournamentInviteService.join(userId, tournamentId, request.inviteCode)
        return ApiResponseBody.ok()
    }

    @PostMapping("/{tournamentId}/join/guest")
    @ResponseStatus(HttpStatus.CREATED)
    override fun joinAsGuest(
        @PathVariable tournamentId: Long,
        @Valid @RequestBody request: JoinTournamentAsGuestRequest,
    ): ApiResponseBody<JoinTournamentAsGuestResponse> {
        val result = tournamentInviteService.joinAsGuest(tournamentId, request.inviteCode, request.nickname)
        return ApiResponseBody.created(JoinTournamentAsGuestResponse.from(result))
    }

    @PostMapping("/{tournamentId}/start")
    override fun start(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<TournamentStartResponse> {
        val result = tournamentService.start(userId, tournamentId)
        return ApiResponseBody.ok(TournamentStartResponse.from(result))
    }

    @PostMapping("/{tournamentId}/matches")
    override fun recordMatch(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
        @Valid @RequestBody request: RecordMatchRequest,
    ): ApiResponseBody<TournamentDetailResponse.CompletedData?> {
        val result = tournamentService.recordMatch(userId, request.toRecordMatch(tournamentId))
        return ApiResponseBody.ok(result?.let { TournamentDetailResponse.CompletedData.from(it) })
    }

    @DeleteMapping("/{tournamentId}")
    override fun deleteTournament(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<Unit> {
        tournamentService.deleteTournament(userId, tournamentId)
        return ApiResponseBody.ok()
    }

    @GetMapping("/{tournamentId}/invite-preview")
    override fun getInvitePreview(
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<TournamentInvitePreviewResponse> {
        val preview = tournamentService.getInvitePreview(tournamentId)
        return ApiResponseBody.ok(TournamentInvitePreviewResponse.from(preview))
    }

    @GetMapping("/by-invite-code")
    override fun getInvitePreviewByCode(
        @RequestParam code: String,
    ): ApiResponseBody<TournamentInvitePreviewResponse> {
        val preview = tournamentService.getInvitePreviewByCode(code)
        return ApiResponseBody.ok(TournamentInvitePreviewResponse.from(preview))
    }

    @PostMapping("/{tournamentId}/play-link")
    override fun createPlayLink(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<LocalDateTime> {
        val expiresAt = tournamentService.createPlayLink(userId, tournamentId)
        return ApiResponseBody.ok(expiresAt)
    }

    @GetMapping("/{tournamentId}/play-link-info")
    override fun getPlayLinkInfo(
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<PlayLinkInfoResponse> {
        val info = tournamentService.getPlayLinkInfo(tournamentId)
        return ApiResponseBody.ok(PlayLinkInfoResponse.from(info))
    }

    @PostMapping("/{sourceTournamentId}/from-play-link")
    @ResponseStatus(HttpStatus.CREATED)
    override fun createFromPlayLink(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable sourceTournamentId: Long,
    ): ApiResponseBody<Long> {
        val newTournamentId = tournamentService.createFromPlayLink(userId, sourceTournamentId)
        return ApiResponseBody.created(newTournamentId)
    }

    @GetMapping("/{tournamentId}/group-result")
    override fun getGroupResult(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable tournamentId: Long,
    ): ApiResponseBody<GroupResultResponse> {
        val result = tournamentService.getGroupResult(userId, tournamentId)
        return ApiResponseBody.ok(GroupResultResponse.from(result))
    }
}
