package com.depromeet.team3.tournament.controller

import com.depromeet.team3.common.openapi.OpenApiObjectMapper
import com.depromeet.team3.common.openapi.binds
import com.depromeet.team3.common.openapi.examples
import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.tournament.controller.dto.StartTournamentResponse
import com.depromeet.team3.tournament.controller.dto.TournamentHistoryInfoResponse
import com.depromeet.team3.tournament.controller.dto.TournamentInfoResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class TournamentApiExamples(private val openApiObjectMapper: OpenApiObjectMapper) {

    @Bean
    fun tournamentOpenApiExamples(): OperationCustomizer = OperationCustomizer { operation, handlerMethod ->
        when {
            handlerMethod.binds(TournamentController::start) -> operation.examples(openApiObjectMapper.delegate) {
                add(
                    status = HttpStatus.CREATED,
                    name = "생성 성공",
                    payload = ApiResponseBody.created(StartTournamentResponse(tournamentId = 1)),
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
                            finalWinnerWishItemId = 3,
                            history = listOf(
                                TournamentHistoryInfoResponse(
                                    currentRound = 1,
                                    firstWishItemId = 1,
                                    secondWishItemId = 2,
                                    winnerWishItemId = 1,
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
