package com.depromeet.piki.tournament.controller

import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemFromLinkResponse
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemsFromImagesResponse
import com.depromeet.piki.tournament.controller.dto.AddTournamentItemsFromWishResponse
import com.depromeet.piki.tournament.controller.dto.TournamentItemDetailResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class TournamentItemApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun tournamentItemOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            when {
                handlerMethod.binds(TournamentItemController::addItemsFromWish) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "위시 아이템 추가 성공",
                            payload = ApiResponseBody.ok(
                                AddTournamentItemsFromWishResponse(tournamentItemIds = listOf(10L, 11L)),
                            ),
                        )
                    }

                handlerMethod.binds(TournamentItemController::addItemFromLink) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "링크 아이템 추가 성공",
                            payload = ApiResponseBody.ok(AddTournamentItemFromLinkResponse(tournamentItemId = 1L)),
                        )
                    }

                handlerMethod.binds(TournamentItemController::addItemsFromImages) ->
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

                handlerMethod.binds(TournamentItemController::updateItem) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "수정 성공 (FAILED → READY)",
                            payload = ApiResponseBody.ok<Unit>(),
                        )
                    }

                handlerMethod.binds(TournamentItemController::deleteItem) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "아이템 삭제 성공",
                            payload = ApiResponseBody.ok<Unit>(),
                        )
                    }

                handlerMethod.binds(TournamentItemController::getTournamentItem) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "READY - 파싱 완료 (링크 등록)",
                            payload = ApiResponseBody.ok(
                                TournamentItemDetailResponse(
                                    tournamentItemId = 10,
                                    itemId = 100,
                                    sourceUrl = "https://www.nike.com/kr/t/air-max/example",
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
                            name = "READY - 파싱 완료 (이미지 등록, sourceUrl=null)",
                            payload = ApiResponseBody.ok(
                                TournamentItemDetailResponse(
                                    tournamentItemId = 11,
                                    itemId = 101,
                                    sourceUrl = null,
                                    name = "아디다스 울트라부스트",
                                    imageUrl = "https://cdn.example.com/items/2.jpg",
                                    price = 189_000,
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
                                    sourceUrl = "https://www.nike.com/kr/t/air-max/example",
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
                                    sourceUrl = "https://www.nike.com/kr/t/air-max/example",
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
