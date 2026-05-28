package com.depromeet.piki.tournament.controller

import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemFromLinkResponse
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemsFromImagesResponse
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemsFromWishResponse
import com.depromeet.piki.tournament.controller.dto.CreateTournamentResponse
import com.depromeet.piki.tournament.controller.dto.TournamentDetailResponse
import com.depromeet.piki.tournament.controller.dto.TournamentItemDetailResponse
import com.depromeet.piki.tournament.controller.dto.TournamentStartResponse
import com.depromeet.piki.tournament.controller.dto.TournamentSummaryResponse
import com.depromeet.piki.tournament.domain.TournamentStatus
import java.time.LocalDateTime
import java.util.UUID
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

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
                    }

                handlerMethod.binds(TournamentController::create) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.CREATED,
                            name = "생성 성공",
                            payload = ApiResponseBody.created(CreateTournamentResponse(tournamentId = 1)),
                        )
                    }

                handlerMethod.binds(TournamentController::deleteItem) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "아이템 삭제 성공",
                            payload = ApiResponseBody.ok<Unit>(),
                        )
                    }

                handlerMethod.binds(TournamentController::addItemsFromWish) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "위시 아이템 추가 성공",
                            payload = ApiResponseBody.ok(
                                AddTournamentItemsFromWishResponse(tournamentItemIds = listOf(10L, 11L)),
                            ),
                        )
                    }

                handlerMethod.binds(TournamentController::addItemFromLink) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "링크 아이템 추가 성공",
                            payload = ApiResponseBody.ok(AddTournamentItemFromLinkResponse(tournamentItemId = 1L)),
                        )
                    }

                handlerMethod.binds(TournamentController::addItemsFromImages) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "이미지 아이템 추가 성공",
                            payload = ApiResponseBody.ok(
                                AddTournamentItemsFromImagesResponse(
                                    tournamentItemIds = listOf(1L, 2L, 3L),
                                ),
                            ),
                        )
                    }

                handlerMethod.binds(TournamentController::start) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "시작 성공",
                            payload = ApiResponseBody.ok(
                                TournamentStartResponse(
                                    items = listOf(
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
                    }

                handlerMethod.binds(TournamentController::recordMatch) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "기록 성공",
                            payload = ApiResponseBody.ok<Unit>(),
                        )
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
                                        pending =
                                            TournamentDetailResponse.PendingData(
                                                items =
                                                    listOf(
                                                        TournamentDetailResponse.ItemDetailResponse(
                                                            tournamentItemId = 1,
                                                            itemId = 10,
                                                            name = "나이키 에어맥스",
                                                            price = 129_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/1.jpg",
                                                        ),
                                                        TournamentDetailResponse.ItemDetailResponse(
                                                            tournamentItemId = 2,
                                                            itemId = 20,
                                                            name = "아디다스 울트라부스트",
                                                            price = 189_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/2.jpg",
                                                        ),
                                                    ),
                                                participants =
                                                    listOf(
                                                        TournamentDetailResponse.ParticipantResponse(
                                                            userId = UUID.fromString("11111111-2222-3333-4444-555555555555"),
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
                                                        ),
                                                        TournamentDetailResponse.ItemDetailResponse(
                                                            tournamentItemId = 4,
                                                            itemId = 40,
                                                            name = "살로몬 XT-6",
                                                            price = 279_000,
                                                            currency = "KRW",
                                                            imageUrl = null,
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
                                        pending = null,
                                        inProgress = null,
                                        completed =
                                            TournamentDetailResponse.CompletedData(
                                                result =
                                                    listOf(
                                                        TournamentDetailResponse.RankedItemResponse(
                                                            rank = 1,
                                                            tournamentItemId = 1,
                                                            itemId = 10,
                                                            name = "나이키 에어맥스",
                                                            price = 129_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/1.jpg",
                                                        ),
                                                        TournamentDetailResponse.RankedItemResponse(
                                                            rank = 2,
                                                            tournamentItemId = 2,
                                                            itemId = 20,
                                                            name = "아디다스 울트라부스트",
                                                            price = 189_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/2.jpg",
                                                        ),
                                                        TournamentDetailResponse.RankedItemResponse(
                                                            rank = 3,
                                                            tournamentItemId = 3,
                                                            itemId = 30,
                                                            name = "뉴발란스 993",
                                                            price = 259_000,
                                                            currency = "KRW",
                                                            imageUrl = "https://cdn.example.com/items/3.jpg",
                                                        ),
                                                        TournamentDetailResponse.RankedItemResponse(
                                                            rank = 4,
                                                            tournamentItemId = 4,
                                                            itemId = 40,
                                                            name = "살로몬 XT-6",
                                                            price = 279_000,
                                                            currency = "KRW",
                                                            imageUrl = null,
                                                        ),
                                                    ),
                                            ),
                                    ),
                                ),
                        )
                    }
                handlerMethod.binds(TournamentController::getTournamentItem) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "READY - 파싱 완료",
                            payload = ApiResponseBody.ok(
                                TournamentItemDetailResponse(
                                    tournamentItemId = 10,
                                    itemId = 100,
                                    name = "나이키 에어맥스",
                                    imageUrl = "https://cdn.example.com/items/1.jpg",
                                    price = 129_000,
                                    currency = "KRW",
                                    status = ItemStatus.READY,
                                ),
                            ),
                        )
                        add(
                            status = HttpStatus.OK,
                            name = "PROCESSING - 파싱 진행 중",
                            payload = ApiResponseBody.ok(
                                TournamentItemDetailResponse(
                                    tournamentItemId = 10,
                                    itemId = 100,
                                    name = null,
                                    imageUrl = null,
                                    price = null,
                                    currency = null,
                                    status = ItemStatus.PROCESSING,
                                ),
                            ),
                        )
                        add(
                            status = HttpStatus.OK,
                            name = "FAILED - 파싱 실패",
                            payload = ApiResponseBody.ok(
                                TournamentItemDetailResponse(
                                    tournamentItemId = 10,
                                    itemId = 100,
                                    name = null,
                                    imageUrl = null,
                                    price = null,
                                    currency = null,
                                    status = ItemStatus.FAILED,
                                ),
                            ),
                        )
                    }
            }
            operation
        }
}
