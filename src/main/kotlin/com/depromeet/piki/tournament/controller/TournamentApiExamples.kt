package com.depromeet.piki.tournament.controller

import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.tournament.controller.dto.CreateTournamentResponse
import com.depromeet.piki.tournament.controller.dto.GroupResultResponse
import com.depromeet.piki.tournament.controller.dto.JoinTournamentAsGuestRequest
import com.depromeet.piki.tournament.controller.dto.JoinTournamentAsGuestResponse
import com.depromeet.piki.tournament.controller.dto.PlayLinkInfoResponse
import com.depromeet.piki.tournament.controller.dto.RankedItemResponse
import com.depromeet.piki.tournament.controller.dto.TournamentDetailResponse
import com.depromeet.piki.tournament.controller.dto.TournamentInvitePreviewResponse
import com.depromeet.piki.tournament.controller.dto.TournamentStartResponse
import com.depromeet.piki.tournament.controller.dto.TournamentSummaryResponse
import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.service.TournamentException
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.util.UUID

@Configuration
class TournamentApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun tournamentOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            when {
                handlerMethod.binds(TournamentController::getTournaments) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "목록 조회 성공",
                            payload =
                                ApiResponseBody.ok(
                                    listOf(
                                        TournamentSummaryResponse(
                                            tournamentId = 1,
                                            name = "내 토너먼트",
                                            status = TournamentStatus.PENDING,
                                            createdAt = LocalDateTime.of(2026, 5, 22, 12, 0, 0),
                                            participantProfileImages =
                                                listOf(
                                                    "https://cdn.example.com/profiles/user1.jpg",
                                                    "https://cdn.example.com/profiles/user2.jpg",
                                                ),
                                        ),
                                    ),
                                ),
                        )
                        unauthorized()
                    }

                handlerMethod.binds(TournamentController::create) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.CREATED,
                            name = "생성 성공",
                            payload =
                                ApiResponseBody.created(
                                    CreateTournamentResponse(
                                        tournamentId = 1,
                                        inviteCode = "ABC123",
                                        inviteExpiresAt = LocalDateTime.of(2026, 5, 30, 15, 0, 0),
                                    ),
                                ),
                        )
                    }

                handlerMethod.binds(TournamentController::join) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "참여 성공",
                            payload = ApiResponseBody.ok<Unit>(),
                        )
                    }

                handlerMethod.binds(TournamentController::joinAsGuest) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.CREATED,
                            name = "게스트 참여 성공",
                            payload =
                                ApiResponseBody.created(
                                    JoinTournamentAsGuestResponse(
                                        accessToken = "eyJhbGciOiJIUzI1NiJ9.example",
                                        refreshToken = "eyJhbGciOiJIUzI1NiJ9.refresh",
                                        userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                                        nickname = "멋진친구",
                                        profileImage = "https://api.dicebear.com/9.x/bottts/svg?seed=aaaaaaaa",
                                        tournamentId = 1,
                                    ),
                                ),
                        )
                        add(
                            status = HttpStatus.BAD_REQUEST,
                            name = "닉네임 미입력",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.INVALID_INPUT,
                                    // @RequestBody Bean Validation 위반은 GlobalExceptionHandler.detailOf 가 "필드명: 메시지" 로 만든다.
                                    detail = "nickname: ${JoinTournamentAsGuestRequest.NICKNAME_BLANK_MESSAGE}",
                                ),
                        )
                        unauthorized()
                    }

                handlerMethod.binds(TournamentController::start) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "시작 성공",
                            payload =
                                ApiResponseBody.ok(
                                    TournamentStartResponse(
                                        items =
                                            listOf(
                                                TournamentStartResponse.ItemResponse(
                                                    tournamentItemId = 1,
                                                    name = "나이키 에어맥스",
                                                    price = 129_000,
                                                    currency = "KRW",
                                                    imageUrl = "https://cdn.example.com/items/1.jpg",
                                                ),
                                                TournamentStartResponse.ItemResponse(
                                                    tournamentItemId = 2,
                                                    name = "아디다스 울트라부스트",
                                                    price = 189_000,
                                                    currency = "KRW",
                                                    imageUrl = "https://cdn.example.com/items/2.jpg",
                                                ),
                                                TournamentStartResponse.ItemResponse(
                                                    tournamentItemId = 3,
                                                    name = "뉴발란스 993",
                                                    price = 259_000,
                                                    currency = "KRW",
                                                    imageUrl = "https://cdn.example.com/items/3.jpg",
                                                ),
                                                TournamentStartResponse.ItemResponse(
                                                    tournamentItemId = 4,
                                                    name = "살로몬 XT-6",
                                                    price = 279_000,
                                                    currency = "USD",
                                                    imageUrl = null,
                                                ),
                                            ),
                                    ),
                                ),
                        )
                        add(TournamentException.invalidItemCount(), name = "아이템 수 미충족 (2~32개)")
                        unauthorized()
                        add(TournamentException.forbiddenTournament(), name = "토너먼트 권한 없음")
                        add(TournamentException.notFoundTournament(), name = "토너먼트를 찾을 수 없음")
                        add(TournamentException.notPendingTournament(), name = "PENDING 상태 아님")
                    }

                handlerMethod.binds(TournamentController::recordMatch) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "기록 성공 (결승 아닌 라운드) — data=null",
                            payload = ApiResponseBody.ok<TournamentDetailResponse.CompletedData?>(),
                        )
                        add(
                            status = HttpStatus.OK,
                            name = "기록 성공 (결승 라운드) — 순위 결과 포함",
                            payload =
                                ApiResponseBody.ok(
                                    TournamentDetailResponse.CompletedData(
                                        result =
                                            listOf(
                                                RankedItemResponse(
                                                    rank = 1,
                                                    tournamentItemId = 1,
                                                    itemId = 10,
                                                    name = "나이키 에어맥스",
                                                    price = 129_000,
                                                    currency = "KRW",
                                                    imageUrl = "https://cdn.example.com/items/1.jpg",
                                                ),
                                                RankedItemResponse(
                                                    rank = 2,
                                                    tournamentItemId = 2,
                                                    itemId = 20,
                                                    name = "아디다스 울트라부스트",
                                                    price = 189_000,
                                                    currency = "KRW",
                                                    imageUrl = "https://cdn.example.com/items/2.jpg",
                                                ),
                                                RankedItemResponse(
                                                    rank = 3,
                                                    tournamentItemId = 3,
                                                    itemId = 30,
                                                    name = "뉴발란스 993",
                                                    price = 259_000,
                                                    currency = "KRW",
                                                    imageUrl = "https://cdn.example.com/items/3.jpg",
                                                ),
                                                RankedItemResponse(
                                                    rank = 4,
                                                    tournamentItemId = 4,
                                                    itemId = 40,
                                                    name = "살로몬 XT-6",
                                                    price = 279_000,
                                                    currency = "USD",
                                                    imageUrl = null,
                                                ),
                                            ),
                                        hasGroupResult = true,
                                    ),
                                ),
                        )
                        add(TournamentException.invalidWinner(), name = "승자가 대결 아이템이 아님")
                        unauthorized()
                        add(TournamentException.forbiddenTournament(), name = "토너먼트 권한 없음")
                        add(TournamentException.notFoundTournament(), name = "토너먼트를 찾을 수 없음")
                        add(TournamentException.notInProgressTournament(), name = "진행 중 토너먼트 아님")
                    }

                handlerMethod.binds(TournamentController::getTournamentById) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "PENDING - 아이템 담는 중",
                            payload =
                                ApiResponseBody.ok(
                                    TournamentDetailResponse(
                                        tournamentId = 1,
                                        name = "내 토너먼트",
                                        status = TournamentStatus.PENDING,
                                        isOwner = true,
                                        pending =
                                            TournamentDetailResponse.PendingData(
                                                inviteCode = "ABC123",
                                                inviteExpiresAt = LocalDateTime.of(2026, 5, 30, 15, 0, 0),
                                                items =
                                                    listOf(
                                                        TournamentDetailResponse.ItemDetailResponse(
                                                            tournamentItemId = 1,
                                                            itemId = 10,
                                                            name = "나이키 에어맥스",
                                                            price = 129_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/1.jpg",
                                                            status = ItemStatus.READY,
                                                        ),
                                                        TournamentDetailResponse.ItemDetailResponse(
                                                            tournamentItemId = 2,
                                                            itemId = 20,
                                                            name = "아디다스 울트라부스트",
                                                            price = 189_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/2.jpg",
                                                            status = ItemStatus.READY,
                                                        ),
                                                    ),
                                                participants =
                                                    listOf(
                                                        TournamentDetailResponse.ParticipantResponse(
                                                            userId =
                                                                UUID.fromString(
                                                                    "11111111-2222-3333-4444-555555555555",
                                                                ),
                                                            nickname = "참여자1",
                                                            profileImage = "https://cdn.example.com/profiles/user1.jpg",
                                                        ),
                                                    ),
                                            ),
                                        inProgress = null,
                                        completed = null,
                                    ),
                                ),
                        )
                        add(
                            status = HttpStatus.OK,
                            name = "IN_PROGRESS - 진행 중 복원",
                            payload =
                                ApiResponseBody.ok(
                                    TournamentDetailResponse(
                                        tournamentId = 1,
                                        name = "내 토너먼트",
                                        status = TournamentStatus.IN_PROGRESS,
                                        isOwner = false,
                                        pending = null,
                                        inProgress =
                                            TournamentDetailResponse.InProgressData(
                                                currentRound = 4,
                                                lastHistory =
                                                    TournamentDetailResponse.HistoryResponse(
                                                        currentRound = 4,
                                                        firstTournamentItemId = 1,
                                                        secondTournamentItemId = 2,
                                                        selectedTournamentItemId = 1,
                                                    ),
                                                remainingItems =
                                                    listOf(
                                                        TournamentDetailResponse.ItemDetailResponse(
                                                            tournamentItemId = 3,
                                                            itemId = 30,
                                                            name = "뉴발란스 993",
                                                            price = 259_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/3.jpg",
                                                            status = ItemStatus.READY,
                                                        ),
                                                        TournamentDetailResponse.ItemDetailResponse(
                                                            tournamentItemId = 4,
                                                            itemId = 40,
                                                            name = "살로몬 XT-6",
                                                            price = 279_000,
                                                            currency = "KRW",
                                                            imageUrl = null,
                                                            status = ItemStatus.READY,
                                                        ),
                                                    ),
                                            ),
                                        completed = null,
                                    ),
                                ),
                        )
                        add(
                            status = HttpStatus.OK,
                            name = "COMPLETED - 최종 결과",
                            payload =
                                ApiResponseBody.ok(
                                    TournamentDetailResponse(
                                        tournamentId = 1,
                                        name = "내 토너먼트",
                                        status = TournamentStatus.COMPLETED,
                                        isOwner = true,
                                        pending = null,
                                        inProgress = null,
                                        completed =
                                            TournamentDetailResponse.CompletedData(
                                                result =
                                                    listOf(
                                                        RankedItemResponse(
                                                            rank = 1,
                                                            tournamentItemId = 1,
                                                            itemId = 10,
                                                            name = "나이키 에어맥스",
                                                            price = 129_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/1.jpg",
                                                        ),
                                                        RankedItemResponse(
                                                            rank = 2,
                                                            tournamentItemId = 2,
                                                            itemId = 20,
                                                            name = "아디다스 울트라부스트",
                                                            price = 189_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/2.jpg",
                                                        ),
                                                        RankedItemResponse(
                                                            rank = 3,
                                                            tournamentItemId = 3,
                                                            itemId = 30,
                                                            name = "뉴발란스 993",
                                                            price = 259_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/3.jpg",
                                                        ),
                                                        RankedItemResponse(
                                                            rank = 4,
                                                            tournamentItemId = 4,
                                                            itemId = 40,
                                                            name = "살로몬 XT-6",
                                                            price = 279_000,
                                                            currency = "KRW",
                                                            imageUrl = null,
                                                        ),
                                                    ),
                                                hasGroupResult = true,
                                            ),
                                    ),
                                ),
                        )
                        unauthorized()
                        add(TournamentException.forbiddenTournament(), name = "토너먼트 권한 없음")
                        add(TournamentException.notFoundTournament(), name = "토너먼트를 찾을 수 없음")
                    }

                handlerMethod.binds(TournamentController::deleteTournament) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "토너먼트 삭제 성공",
                            payload = ApiResponseBody.ok<Unit>(),
                        )
                        unauthorized()
                        add(TournamentException.forbiddenTournament(), name = "토너먼트 권한 없음")
                        add(TournamentException.notFoundTournament(), name = "토너먼트를 찾을 수 없음")
                        add(TournamentException.inProgressTournamentCannotBeDeleted(), name = "진행 중 토너먼트 삭제 불가")
                    }

                handlerMethod.binds(TournamentController::getInvitePreview) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "초대 코드 검증 성공",
                            payload =
                                ApiResponseBody.ok(
                                    TournamentInvitePreviewResponse(
                                        tournamentId = 1,
                                        tournamentName = "내 토너먼트",
                                        itemCount = 8,
                                        participantCount = 2,
                                    ),
                                ),
                        )
                    }

                handlerMethod.binds(TournamentController::createPlayLink) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "플레이 링크 생성 성공",
                            payload = ApiResponseBody.ok(LocalDateTime.of(2026, 6, 9, 22, 0, 0)),
                        )
                        add(
                            TournamentException.clonedTournamentCannotSharePlayLink(),
                            name = "플레이 링크로 참여한 토너먼트 (재공유 불가)",
                        )
                        add(TournamentException.playLinkAlreadyCreated(), name = "플레이 링크 이미 생성됨")
                    }

                handlerMethod.binds(TournamentController::getPlayLinkInfo) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "플레이 링크 정보 조회 성공",
                            payload =
                                ApiResponseBody.ok(
                                    PlayLinkInfoResponse(
                                        sourceTournamentId = 1,
                                        tournamentName = "내 토너먼트",
                                        itemCount = 8,
                                        playLinkExpiresAt = LocalDateTime.of(2026, 6, 9, 22, 0, 0),
                                    ),
                                ),
                        )
                    }

                handlerMethod.binds(TournamentController::createFromPlayLink) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.CREATED,
                            name = "복제 토너먼트 생성 성공",
                            payload = ApiResponseBody.created(42L),
                        )
                    }

                handlerMethod.binds(TournamentController::getGroupResult) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "그룹 결과 조회 성공",
                            payload =
                                ApiResponseBody.ok(
                                    GroupResultResponse(
                                        items =
                                            listOf(
                                                GroupResultResponse.GroupResultItemResponse(
                                                    rank = 1,
                                                    itemId = 10,
                                                    name = "나이키 에어맥스",
                                                    price = 129_000,
                                                    currency = "KRW",
                                                    imageUrl = "https://cdn.example.com/items/1.jpg",
                                                    chosenBy =
                                                        listOf(
                                                            GroupResultResponse.ParticipantSummaryResponse(
                                                                userId =
                                                                    UUID.fromString(
                                                                        "11111111-2222-3333-4444-555555555555",
                                                                    ),
                                                                nickname = "참여자A",
                                                                profileImage = "https://api.dicebear.com/9.x/bottts/svg?seed=aaaaaaaa",
                                                            ),
                                                            GroupResultResponse.ParticipantSummaryResponse(
                                                                userId =
                                                                    UUID.fromString(
                                                                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                                                                    ),
                                                                nickname = "참여자B",
                                                                profileImage = "https://api.dicebear.com/9.x/bottts/svg?seed=bbbbbbbb",
                                                            ),
                                                        ),
                                                ),
                                                GroupResultResponse.GroupResultItemResponse(
                                                    rank = 2,
                                                    itemId = 20,
                                                    name = "아디다스 울트라부스트",
                                                    price = 189_000,
                                                    currency = "KRW",
                                                    imageUrl = "https://cdn.example.com/items/2.jpg",
                                                    chosenBy =
                                                        listOf(
                                                            GroupResultResponse.ParticipantSummaryResponse(
                                                                userId =
                                                                    UUID.fromString(
                                                                        "11111111-2222-3333-4444-555555555555",
                                                                    ),
                                                                nickname = "참여자A",
                                                                profileImage = "https://api.dicebear.com/9.x/bottts/svg?seed=aaaaaaaa",
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                                ),
                        )
                    }
            }
            operation
        }
}
