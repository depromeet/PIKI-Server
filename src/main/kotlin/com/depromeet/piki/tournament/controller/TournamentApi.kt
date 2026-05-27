package com.depromeet.piki.tournament.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemFromLinkRequest
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemFromLinkResponse
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemsFromImagesResponse
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemsRequest
import com.depromeet.piki.tournament.controller.dto.CreateTournamentRequest
import com.depromeet.piki.tournament.controller.dto.CreateTournamentResponse
import com.depromeet.piki.tournament.controller.dto.RecordMatchRequest
import com.depromeet.piki.tournament.controller.dto.TournamentDetailResponse
import com.depromeet.piki.tournament.controller.dto.TournamentStartResponse
import com.depromeet.piki.tournament.controller.dto.TournamentSummaryResponse
import com.depromeet.piki.tournament.domain.TournamentStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Tag(name = "Tournament", description = "토너먼트 API")
interface TournamentApi {
    @Operation(
        summary = "토너먼트 생성",
        description = "이름으로 PENDING 상태의 토너먼트를 생성한다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "토너먼트 생성 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (name 미입력)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun create(
        @Parameter(hidden = true) userId: UUID,
        request: CreateTournamentRequest,
    ): ApiResponseBody<CreateTournamentResponse>

    @Operation(
        summary = "위시에서 토너먼트 아이템 추가",
        description = """
            PENDING 상태의 토너먼트에 위시리스트에 있는 아이템을 추가한다. 토너먼트 참여자만 추가할 수 있다.
            itemIds 중 하나라도 조건에 맞지 않으면 요청 전체가 실패한다(부분 성공 없음).
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "아이템 추가 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (itemIds 1~32개 범위 초과 · 아이템 최대 32개 초과)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (토너먼트 참여자가 아님 · 위시리스트에 없는 아이템 포함)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음 · 존재하지 않는 아이템 포함",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (PENDING이 아닌 토너먼트 · 이미 등록된 아이템 · PROCESSING/FAILED 상품 포함)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun addItemsFromWish(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
        request: AddTournamentItemsRequest,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "URL 링크로 토너먼트 아이템 추가",
        description = """
            PENDING 상태의 토너먼트에 URL 링크를 통해 아이템을 추가한다.
            아이템이 PROCESSING 상태로 즉시 생성되어 itemId 가 반환된다.
            파싱은 비동기로 진행되며 완료 시 READY 또는 FAILED 상태로 전환된다.
            클라이언트는 itemId 로 상태를 폴링한다. 토너먼트 참여자만 추가할 수 있다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "아이템 추가 성공 (item.status=PROCESSING, 파싱은 백그라운드)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (URL 미입력 · URL 형식 오류(비어 있음/유효하지 않음/https 외 스킴) · URL 2048자 초과 · 아이템 최대 32개 초과)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (토너먼트 참여자가 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (PENDING이 아닌 토너먼트)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun addItemFromLink(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
        request: AddTournamentItemFromLinkRequest,
    ): ApiResponseBody<AddTournamentItemFromLinkResponse>

    @Operation(
        summary = "이미지로 토너먼트 아이템 추가",
        description = """
            PENDING 상태의 토너먼트에 이미지 추출을 통해 아이템을 추가한다.
            이미지 1~5장을 전달하면 아이템이 PROCESSING 상태로 즉시 생성되어 itemIds 가 반환된다.
            이미지 파싱은 비동기로 진행되며 완료 시 READY 또는 FAILED 상태로 전환된다.
            클라이언트는 itemId 로 상태를 폴링한다. 토너먼트 참여자만 추가할 수 있다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "아이템 추가 성공 (item.status=PROCESSING, 파싱은 백그라운드)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (이미지 1~5장 범위 초과 · 빈 이미지 · 이미지 타입 미지정 · 지원하지 않는 이미지 형식(png/jpeg/webp/heic/heif만 허용) · 아이템 최대 32개 초과)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (토너먼트 참여자가 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (PENDING이 아닌 토너먼트)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun addItemsFromImages(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
        images: List<MultipartFile>,
    ): ApiResponseBody<AddTournamentItemsFromImagesResponse>

    @Operation(
        summary = "토너먼트 아이템 삭제",
        description = "PENDING 상태의 토너먼트에서 아이템을 제거한다. 아이템을 추가한 본인 또는 토너먼트 소유자만 삭제할 수 있다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "아이템 삭제 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (아이템을 추가한 본인도 아니고 토너먼트 소유자도 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음 · 토너먼트 아이템을 찾을 수 없음",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (PENDING이 아닌 토너먼트)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun deleteItem(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
        @Parameter(description = "토너먼트 아이템 ID", example = "10") tournamentItemId: Long,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "토너먼트 시작",
        description = "PENDING 상태의 토너먼트를 IN_PROGRESS 상태로 전환하고, 참여 아이템 목록을 가격 오름차순으로 정렬해 반환한다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "토너먼트 시작 성공 (가격 오름차순 정렬 아이템 목록 반환)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "아이템 수 미충족 (최소 2개, 최대 32개)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (토너먼트 소유자가 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음 · 존재하지 않는 아이템 포함",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (PENDING이 아닌 토너먼트 · PROCESSING/FAILED 상품 포함 · 가격 정보 없는 상품 포함)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun start(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
    ): ApiResponseBody<TournamentStartResponse>

    @Operation(
        summary = "매치 결과 기록",
        description = """
            IN_PROGRESS 상태의 토너먼트에서 한 매치의 결과(승자)를 기록한다.
            currentRound 는 해당 시점에 서버가 기대하는 라운드와 일치해야 한다.
            결승(currentRound=2) 결과 기록 시 토너먼트가 COMPLETED 로 자동 전환된다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "매치 결과 기록 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (승자가 대결 두 아이템 중 하나가 아님 · 해당 토너먼트에 속하지 않는 아이템 · 현재 진행해야 할 라운드가 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (토너먼트 참여자가 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (IN_PROGRESS가 아닌 토너먼트)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun recordMatch(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
        request: RecordMatchRequest,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "토너먼트 목록 조회",
        description = """
            내 토너먼트 목록을 최근 생성 순으로 조회한다.
            status 파라미터로 상태 필터링 가능하며 여러 값을 중복 전달할 수 있다(예: ?status=PENDING&status=IN_PROGRESS).
            생략 시 전체 반환. status 값은 대문자(PENDING/IN_PROGRESS/COMPLETED)로 전달해야 한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "목록 조회 성공 (참여 토너먼트 없으면 빈 배열 반환)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun getTournaments(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "상태 필터 (복수 전달 가능, 생략 시 전체)", example = "PENDING")
        status: List<TournamentStatus>?,
    ): ApiResponseBody<List<TournamentSummaryResponse>>

    @Operation(
        summary = "토너먼트 단건 조회",
        description = """
            토너먼트 ID로 상태에 따른 상세 정보를 조회한다.
            응답의 status 필드에 따라 포함되는 데이터가 달라진다.
            - PENDING: pending 필드 (아이템 목록, 참여자 목록)
            - IN_PROGRESS: inProgress 필드
              - currentRound: 다음에 진행할 라운드 번호
              - lastHistory: 가장 최근에 기록된 매치 결과. 라운드 전환 직후에는 currentRound와 다른 라운드의 매치일 수 있음. 매치 기록이 없으면 null
              - remainingItems: 현재 라운드에서 아직 대결하지 않은 생존 아이템 목록, 가격 오름차순. 이 순서가 클라이언트의 매치 페어링 순서([0]vs[1], [2]vs[3] …)를 결정함
            - COMPLETED: completed 필드 (result — 1위부터 최대 4위까지 순위 아이템 목록)
            나머지 필드는 응답에 포함되지 않는다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "토너먼트 조회 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (토너먼트 참여자가 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun getTournamentById(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
    ): ApiResponseBody<TournamentDetailResponse>
}
