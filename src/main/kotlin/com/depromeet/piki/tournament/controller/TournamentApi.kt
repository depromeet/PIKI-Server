package com.depromeet.piki.tournament.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemsRequest
import com.depromeet.piki.tournament.controller.dto.CreateTournamentRequest
import com.depromeet.piki.tournament.controller.dto.CreateTournamentResponse
import com.depromeet.piki.tournament.controller.dto.RecordMatchRequest
import com.depromeet.piki.tournament.controller.dto.TournamentInfoResponse
import com.depromeet.piki.tournament.controller.dto.TournamentSummaryResponse
import com.depromeet.piki.tournament.domain.TournamentStatus
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
        summary = "토너먼트 생성",
        description = "이름으로 PENDING 상태의 토너먼트를 생성한다.",
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
    fun create(
        userId: UUID,
        request: CreateTournamentRequest,
    ): ApiResponseBody<CreateTournamentResponse>

    @Operation(
        summary = "토너먼트 아이템 추가",
        description = "PENDING 상태의 토너먼트에 아이템을 추가한다. 토너먼트 참여자만 추가할 수 있다.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "아이템 추가 성공",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiResponseBody::class),
            ),
        ],
    )
    fun addItems(
        userId: UUID,
        tournamentId: Long,
        request: AddTournamentItemsRequest,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "토너먼트 아이템 삭제",
        description = "PENDING 상태의 토너먼트에서 아이템을 제거한다. 아이템을 추가한 본인 또는 토너먼트 소유자만 삭제할 수 있다.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "아이템 삭제 성공",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiResponseBody::class),
            ),
        ],
    )
    fun deleteItem(
        userId: UUID,
        tournamentId: Long,
        tournamentItemId: Long,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "토너먼트 시작",
        description = "PENDING 상태의 토너먼트를 IN_PROGRESS 상태로 전환한다.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "토너먼트 시작 성공",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiResponseBody::class),
            ),
        ],
    )
    fun start(
        userId: UUID,
        tournamentId: Long,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "매치 결과 기록",
        description = "IN_PROGRESS 상태의 토너먼트에서 한 라운드 매치 결과(승자)를 기록한다.",
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
        summary = "토너먼트 목록 조회",
        description = "내 토너먼트 목록을 최근 생성 순으로 조회한다. status 파라미터로 상태 필터링 가능하며 여러 값을 중복 전달할 수 있다(예: ?status=PENDING&status=IN_PROGRESS). 생략 시 전체 반환. status 값은 대문자(PENDING/IN_PROGRESS/COMPLETED)로 전달해야 한다.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "목록 조회 성공",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiResponseBody::class),
            ),
        ],
    )
    fun getTournaments(
        userId: UUID,
        status: List<TournamentStatus>?,
    ): ApiResponseBody<List<TournamentSummaryResponse>>

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
