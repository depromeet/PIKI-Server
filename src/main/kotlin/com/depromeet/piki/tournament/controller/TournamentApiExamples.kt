package com.depromeet.piki.tournament.controller

import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.tournament.controller.dto.CreateTournamentResponse
import com.depromeet.piki.tournament.controller.dto.TournamentBracketResponse
import com.depromeet.piki.tournament.controller.dto.TournamentHistoryInfoResponse
import com.depromeet.piki.tournament.controller.dto.TournamentInfoResponse
import com.depromeet.piki.tournament.controller.dto.TournamentItemInfoResponse
import com.depromeet.piki.tournament.controller.dto.TournamentSummaryResponse
import com.depromeet.piki.tournament.domain.TournamentStatus
import java.time.LocalDateTime
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

                handlerMethod.binds(TournamentController::addItems) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "아이템 추가 성공",
                            payload = ApiResponseBody.ok<Unit>(),
                        )
                    }

                handlerMethod.binds(TournamentController::start) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "시작 성공",
                            payload = ApiResponseBody.ok(
                                TournamentBracketResponse(
                                    matches = listOf(
                                        TournamentBracketResponse.MatchResponse(
                                            firstItem = TournamentBracketResponse.MatchItemResponse(
                                                tournamentItemId = 1,
                                                name = "나이키 에어맥스",
                                                price = 129_000,
                                                currency = "KRW",
                                                imageUrl = "https://cdn.example.com/items/1.jpg",
                                            ),
                                            secondItem = TournamentBracketResponse.MatchItemResponse(
                                                tournamentItemId = 2,
                                                name = "아디다스 울트라부스트",
                                                price = 189_000,
                                                currency = "KRW",
                                                imageUrl = "https://cdn.example.com/items/2.jpg",
                                            ),
                                        ),
                                        TournamentBracketResponse.MatchResponse(
                                            firstItem = TournamentBracketResponse.MatchItemResponse(
                                                tournamentItemId = 3,
                                                name = "뉴발란스 993",
                                                price = 259_000,
                                                currency = "KRW",
                                                imageUrl = "https://cdn.example.com/items/3.jpg",
                                            ),
                                            secondItem = TournamentBracketResponse.MatchItemResponse(
                                                tournamentItemId = 4,
                                                name = "살로몬 XT-6",
                                                price = 279_000,
                                                currency = "USD",
                                                imageUrl = null,
                                            ),
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
                            name = "조회 성공",
                            payload =
                                ApiResponseBody.ok(
                                    TournamentInfoResponse(
                                        tournamentId = 1,
                                        startRound = 2,
                                        items =
                                            listOf(
                                                TournamentItemInfoResponse(tournamentItemId = 1, itemId = 10),
                                                TournamentItemInfoResponse(tournamentItemId = 2, itemId = 20),
                                            ),
                                        history =
                                            listOf(
                                                TournamentHistoryInfoResponse(
                                                    currentRound = 2,
                                                    firstTournamentItemId = 1,
                                                    secondTournamentItemId = 2,
                                                    selectedTournamentItemId = 1,
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
