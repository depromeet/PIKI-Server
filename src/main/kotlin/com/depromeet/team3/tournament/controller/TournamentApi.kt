package com.depromeet.team3.tournament.controller

import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.tournament.controller.dto.RecordMatchRequest
import com.depromeet.team3.tournament.controller.dto.StartTournamentRequest
import com.depromeet.team3.tournament.controller.dto.StartTournamentResponse
import com.depromeet.team3.tournament.controller.dto.TournamentInfoResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import java.util.UUID

@Tag(name = "Tournament", description = "토너먼트 API")
interface TournamentApi {
    @Operation(
        summary = "토너먼트 시작",
        description = "위시 아이템 목록으로 토너먼트를 생성하고 시작한다.",
    )
    @ApiResponse(
        responseCode = "201",
        description = "토너먼트 생성 성공",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiResponseBody::class),
            ),
        ],
    )
    fun start(
        userId: UUID,
        request: StartTournamentRequest,
    ): ApiResponseBody<StartTournamentResponse>

    @Operation(
        summary = "매치 결과 기록",
        description = "토너먼트의 한 라운드 매치 결과(승자)를 기록한다.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "매치 결과 기록 성공",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiResponseBody::class),
            ),
        ],
    )
    fun recordMatch(
        userId: UUID,
        tournamentId: Long,
        request: RecordMatchRequest,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "토너먼트 조회",
        description = "토너먼트 ID로 토너먼트 정보와 매치 기록을 조회한다.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "토너먼트 조회 성공",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiResponseBody::class),
            ),
        ],
    )
    fun getTournamentById(
        userId: UUID,
        tournamentId: Long,
    ): ApiResponseBody<TournamentInfoResponse>
}
