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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import java.time.LocalDateTime
import java.util.UUID

@Tag(name = "Tournament", description = "토너먼트 API")
interface TournamentApi {

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
            isOwner: 요청자가 토너먼트 소유자이면 true. 클라이언트는 이 값으로 위시 추가·플레이링크 공유 버튼 등 소유자 전용 UI를 제어한다.
            응답의 status 필드에 따라 포함되는 데이터가 달라진다.
            - PENDING: pending 필드 (아이템 목록, 참여자 목록)
              - 각 아이템에 status 포함 (READY / PROCESSING / FAILED). PROCESSING 이면 name·price·imageUrl 은 null 이라 응답에서 제외됨
            - IN_PROGRESS: inProgress 필드
              - currentRound: 다음에 진행할 라운드 번호
              - lastHistory: 가장 최근에 기록된 매치 결과. 라운드 전환 직후에는 currentRound와 다른 라운드의 매치일 수 있음. 매치 기록이 없으면 null
              - remainingItems: 현재 라운드에서 아직 대결하지 않은 생존 아이템 목록, 가격 오름차순. 이 순서가 클라이언트의 매치 페어링 순서([0]vs[1], [2]vs[3] …)를 결정함. 각 아이템에 status 포함
            - COMPLETED: completed 필드
              - result: 1위부터 최대 4위까지 순위 아이템 목록
              - hasGroupResult: 참여자 2명 이상이면 true. 클라이언트는 이 값으로 친구 토너먼트 결과 보기 버튼을 제어한다.
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

    @Operation(
        summary = "토너먼트 생성",
        description = """
            이름으로 PENDING 상태의 토너먼트를 생성한다.
            응답의 inviteCode 와 inviteExpiresAt 을 친구에게 공유하면 초대 링크가 만료되기 전까지 친구가 참여할 수 있다.
            inviteDurationMinutes 를 생략하면 기본 30분, 최대 1440분(24시간)까지 1분 단위로 설정 가능하다.
        """,
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
                description = "권한 없음 (토너먼트 참여자가 아님 · 토너먼트 소유자가 아님)",
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
        summary = "소셜 토너먼트 참여 (인증된 사용자)",
        description = """
            PENDING 상태의 토너먼트에 참여한다. JWT 인증이 필요하다 (GUEST·MEMBER 모두 허용).
            - 링크 직접 접근: inviteCode 생략 가능. 만료 여부만 확인 후 바로 참여.
            - 코드 입력 경로: inviteCode 전달 시 코드 일치 여부도 검증.
            초대 링크가 만료됐거나 이미 참여 중이면 실패한다.
            계정이 없는 비회원은 /join/guest 를 사용한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "참여 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (초대 코드 형식 오류 · 코드 불일치 — inviteCode 전달 시에만 발생)",
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
                description = "상태 충돌 (PENDING이 아닌 토너먼트 · 초대 링크 만료 · 이미 참여 중 · 참여 인원 초과(최대 9명))",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun join(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
        request: JoinTournamentRequest,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "소셜 토너먼트 참여 (비회원 게스트)",
        description = """
            닉네임을 입력해 게스트 계정을 생성하고 토너먼트에 참여한다. 인증 불필요.
            - 링크 직접 접근: inviteCode 생략 가능. 만료 여부만 확인.
            - 코드 입력 경로: inviteCode 전달 시 코드 일치 여부도 검증.
            성공 시 JWT 토큰 쌍(accessToken·refreshToken)과 생성된 사용자 정보가 반환된다.
            이후 요청에서는 발급받은 accessToken 을 사용한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "게스트 계정 생성 및 참여 성공 (accessToken·refreshToken·userId·nickname·profileImage 포함)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (초대 코드 형식 오류 · 코드 불일치 — inviteCode 전달 시에만 발생 · 닉네임 미입력 · 닉네임 10자 초과)",
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
                description = "상태 충돌 (PENDING이 아닌 토너먼트 · 초대 링크 만료 · 닉네임 중복 · 참여 인원 초과(최대 9명))",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun joinAsGuest(
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
        request: JoinTournamentAsGuestRequest,
    ): ApiResponseBody<JoinTournamentAsGuestResponse>

    @Operation(
        summary = "매치 결과 기록",
        description = """
            IN_PROGRESS 상태의 토너먼트에서 한 매치의 결과(승자)를 기록한다.
            currentRound 는 해당 시점에 서버가 기대하는 라운드와 일치해야 한다.
            결승(currentRound=2) 결과 기록 시 토너먼트가 COMPLETED 로 자동 전환되고,
            응답 data 에 1위~최대 4위의 순위 결과(이름·가격·이미지)가 포함된다.
            결승이 아닌 라운드는 data=null 을 반환한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "매치 결과 기록 성공 (결승이 아닌 라운드: data=null · 결승 라운드: data.result에 순위 아이템 목록)",
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
                description = "상태 충돌 (IN_PROGRESS가 아닌 토너먼트 · 이미 탈락한 아이템)",
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
    ): ApiResponseBody<TournamentDetailResponse.CompletedData?>

    @Operation(
        summary = "토너먼트 삭제",
        description = "PENDING 또는 COMPLETED 상태의 토너먼트를 삭제한다. 토너먼트 소유자만 삭제할 수 있으며, IN_PROGRESS 상태에서는 삭제할 수 없다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "토너먼트 삭제 성공",
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
                description = "권한 없음 (토너먼트 참여자가 아님 · 토너먼트 소유자가 아님)",
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
                description = "상태 충돌 (IN_PROGRESS 토너먼트는 삭제 불가)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun deleteTournament(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "토너먼트 참여 전 미리보기",
        description = """
            토너먼트 기본 정보(이름, 아이템 수, 참여자 수)를 반환한다. 인증 불필요.
            - 링크 직접 접근: inviteCode 생략 가능. 만료 여부만 확인.
            - 코드 입력 경로: inviteCode 전달 시 코드 일치 여부도 함께 검증.
            비회원은 이 정보를 보고 닉네임 설정 후 /join/guest, 회원은 /join을 호출한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공 (토너먼트 이름·아이템 수·참여자 수 반환)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (초대 코드 불일치 — inviteCode 전달 시에만 발생)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (PENDING이 아닌 토너먼트 · 초대 링크 만료)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
        ],
    )
    fun getInvitePreview(
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
        @Parameter(description = "초대 코드 (영어 대문자 3자리 + 숫자 3자리, 코드 입력 경로에서만 전달)", example = "ABC123") inviteCode: String?,
    ): ApiResponseBody<TournamentInvitePreviewResponse>

    @Operation(
        summary = "플레이 링크 생성",
        description = """
            완료된 토너먼트의 플레이 링크를 생성한다. 토너먼트 소유자만 호출 가능.
            플레이 링크를 통해 친구들이 동일한 아이템 구성으로 토너먼트를 진행할 수 있다.
            만료 기간은 생성 시점 + 14일로 고정이며 변경 불가.
            이미 링크가 있으면 만료 시간이 갱신된다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "플레이 링크 생성 성공 (playLinkExpiresAt 반환)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (토너먼트 참여자가 아님 · 소유자가 아님)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (COMPLETED가 아닌 토너먼트)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
        ],
    )
    fun createPlayLink(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
    ): ApiResponseBody<LocalDateTime>

    @Operation(
        summary = "플레이 링크 정보 조회",
        description = "플레이 링크가 유효한 토너먼트의 정보(이름, 아이템 수, 만료 시간)를 반환한다. 인증 불필요.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음 · 플레이 링크가 생성되지 않은 토너먼트",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "플레이 링크 만료",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
        ],
    )
    fun getPlayLinkInfo(
        @Parameter(description = "원본 토너먼트 ID", example = "1") tournamentId: Long,
    ): ApiResponseBody<PlayLinkInfoResponse>

    @Operation(
        summary = "플레이 링크로 토너먼트 복제 생성",
        description = """
            플레이 링크가 유효한 토너먼트와 동일한 아이템 구성으로 새 토너먼트를 생성한다.
            생성된 토너먼트는 PENDING 상태이며 아이템이 미리 복사되어 있어 바로 시작할 수 있다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "복제 토너먼트 생성 성공 (tournamentId 반환)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음 · 플레이 링크가 생성되지 않은 토너먼트",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (플레이 링크 만료 · 이미 해당 플레이 링크로 토너먼트를 생성한 경우)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
        ],
    )
    fun createFromPlayLink(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "원본 토너먼트 ID", example = "1") sourceTournamentId: Long,
    ): ApiResponseBody<Long>

    @Operation(
        summary = "그룹 결과 조회",
        description = """
            완료된 토너먼트의 그룹 결과를 조회한다.
            원본 토너먼트와 플레이 링크로 복제된 모든 토너먼트의 결과를 비교해,
            각 순위의 아이템마다 동일한 결과를 선택한 참여자 정보를 반환한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "그룹 결과 조회 성공",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (토너먼트 참여자가 아님)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토너먼트를 찾을 수 없음",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (COMPLETED가 아닌 토너먼트)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
        ],
    )
    fun getGroupResult(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "토너먼트 ID", example = "1") tournamentId: Long,
    ): ApiResponseBody<GroupResultResponse>
}
