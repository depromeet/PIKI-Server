package com.depromeet.team3.tournament.controller

import com.depromeet.team3.common.openapi.OpenApiObjectMapper
import com.depromeet.team3.common.openapi.binds
import com.depromeet.team3.common.openapi.examples
import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.tournament.controller.dto.CreateTournamentResponse
import com.depromeet.team3.tournament.controller.dto.TournamentHistoryInfoResponse
import com.depromeet.team3.tournament.controller.dto.TournamentInfoResponse
import com.depromeet.team3.tournament.controller.dto.TournamentItemInfoResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class TournamentApiExamples(private val openApiObjectMapper: OpenApiObjectMapper) {

    @Bean
    fun tournamentOpenApiExamples(): OperationCustomizer = OperationCustomizer { operation, handlerMethod ->
        when {
            handlerMethod.binds(TournamentController::create) -> operation.examples(openApiObjectMapper.delegate) {
                add(
                    status = HttpStatus.CREATED,
                    name = "생성 성공",
                    payload = ApiResponseBody.created(CreateTournamentResponse(tournamentId = 1)),
                )
            }

            handlerMethod.binds(TournamentController::addItems) -> operation.examples(openApiObjectMapper.delegate) {
                add(
                    status = HttpStatus.OK,
                    name = "아이템 추가 성공",
                    payload = ApiResponseBody.ok<Unit>(),
                )
            }

            handlerMethod.binds(TournamentController::start) -> operation.examples(openApiObjectMapper.delegate) {
                add(
                    status = HttpStatus.OK,
                    name = "시작 성공",
                    payload = ApiResponseBody.ok<Unit>(),
                )
            }

            handlerMethod.binds(TournamentController::recordMatch) -> operation.examples(openApiObjectMapper.delegate) {
                add(
                    status = HttpStatus.OK,
                    name = "기록 성공",
                    payload = ApiResponseBody.ok<Unit>(),
                )
            }

            handlerMethod.binds(TournamentController::getTournamentById) -> operation.examples(openApiObjectMapper.delegate) {
                add(
                    status = HttpStatus.OK,
                    name = "조회 성공",
                    payload = ApiResponseBody.ok(
                        TournamentInfoResponse(
                            tournamentId = 1,
                            initialRound = 2,
                            items = listOf(
                                TournamentItemInfoResponse(tournamentItemId = 1, itemId = 10),
                                TournamentItemInfoResponse(tournamentItemId = 2, itemId = 20),
                            ),
                            history = listOf(
                                TournamentHistoryInfoResponse(
                                    currentRound = 4,
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
