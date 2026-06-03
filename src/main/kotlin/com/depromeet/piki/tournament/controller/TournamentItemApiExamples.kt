package com.depromeet.piki.tournament.controller

import com.depromeet.piki.common.exception.ErrorCategory
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
                        add(
                            status = HttpStatus.BAD_REQUEST,
                            name = "itemIds 개수 위반 (1~32개)",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.INVALID_INPUT,
                                    detail = "itemIds: 아이템은 1개 이상 32개 이하여야 합니다.",
                                ),
                        )
                        unauthorized()
                        add(
                            status = HttpStatus.FORBIDDEN,
                            name = "위시리스트에 없는 아이템 포함",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.FORBIDDEN,
                                    detail = "위시리스트에 없는 아이템은 토너먼트에 추가할 수 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.NOT_FOUND,
                            name = "토너먼트를 찾을 수 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
                                    detail = "토너먼트를 찾을 수 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.CONFLICT,
                            name = "PENDING 상태 아님",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.CONFLICT,
                                    detail = "PENDING 상태인 토너먼트에만 수행할 수 있습니다.",
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
                        add(
                            status = HttpStatus.BAD_REQUEST,
                            name = "URL 형식 오류",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.INVALID_INPUT,
                                    detail = "유효한 URL 형식이 아닙니다.",
                                ),
                        )
                        unauthorized()
                        add(
                            status = HttpStatus.FORBIDDEN,
                            name = "토너먼트 권한 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.FORBIDDEN,
                                    detail = "해당 토너먼트에 대한 권한이 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.NOT_FOUND,
                            name = "토너먼트를 찾을 수 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
                                    detail = "토너먼트를 찾을 수 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.CONFLICT,
                            name = "PENDING 상태 아님",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.CONFLICT,
                                    detail = "PENDING 상태인 토너먼트에만 수행할 수 있습니다.",
                                ),
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
                        add(
                            status = HttpStatus.BAD_REQUEST,
                            name = "이미지 개수 위반 (1~5개)",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.INVALID_INPUT,
                                    detail = "이미지는 최소 1개, 최대 5개까지 전송할 수 있습니다.",
                                ),
                        )
                        unauthorized()
                        add(
                            status = HttpStatus.FORBIDDEN,
                            name = "토너먼트 권한 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.FORBIDDEN,
                                    detail = "해당 토너먼트에 대한 권한이 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.NOT_FOUND,
                            name = "토너먼트를 찾을 수 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
                                    detail = "토너먼트를 찾을 수 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.CONFLICT,
                            name = "PENDING 상태 아님",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.CONFLICT,
                                    detail = "PENDING 상태인 토너먼트에만 수행할 수 있습니다.",
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
                        add(
                            status = HttpStatus.BAD_GATEWAY,
                            name = "이미지 저장 실패 (S3 업로드 오류)",
                            payload = ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.RETRYABLE,
                                detail = "이미지 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.",
                            ),
                        )
                        add(
                            status = HttpStatus.BAD_REQUEST,
                            name = "상품명 없이 보정 시도",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.INVALID_INPUT,
                                    detail = "상품명을 입력해야 합니다.",
                                ),
                        )
                        unauthorized()
                        add(
                            status = HttpStatus.FORBIDDEN,
                            name = "토너먼트 권한 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.FORBIDDEN,
                                    detail = "해당 토너먼트에 대한 권한이 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.NOT_FOUND,
                            name = "토너먼트를 찾을 수 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
                                    detail = "토너먼트를 찾을 수 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.CONFLICT,
                            name = "이미 등록 완료(READY) 항목",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.CONFLICT,
                                    detail = "이미 등록 완료된 상품은 수정할 수 없습니다.",
                                ),
                        )
                    }

                handlerMethod.binds(TournamentItemController::deleteItem) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "아이템 삭제 성공",
                            payload = ApiResponseBody.ok<Unit>(),
                        )
                        unauthorized()
                        add(
                            status = HttpStatus.FORBIDDEN,
                            name = "토너먼트 권한 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.FORBIDDEN,
                                    detail = "해당 토너먼트에 대한 권한이 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.NOT_FOUND,
                            name = "토너먼트/아이템을 찾을 수 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
                                    detail = "토너먼트를 찾을 수 없습니다.",
                                ),
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
                        unauthorized()
                        add(
                            status = HttpStatus.FORBIDDEN,
                            name = "토너먼트 권한 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.FORBIDDEN,
                                    detail = "해당 토너먼트에 대한 권한이 없습니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.NOT_FOUND,
                            name = "토너먼트/아이템을 찾을 수 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
                                    detail = "토너먼트를 찾을 수 없습니다.",
                                ),
                        )
                    }
            }
            operation
        }
}
