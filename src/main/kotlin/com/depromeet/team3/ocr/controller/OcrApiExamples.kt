package com.depromeet.team3.ocr.controller

import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.openapi.OpenApiObjectMapper
import com.depromeet.team3.common.openapi.binds
import com.depromeet.team3.common.openapi.examples
import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.ocr.controller.dto.OcrResponse
import com.depromeet.team3.ocr.domain.OcrImage
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class OcrApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun ocrOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.binds(OcrController::extractProduct)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "추출 성공",
                        payload =
                            ApiResponseBody.ok(
                                OcrResponse(
                                    name =
                                        OcrResponse.ExtractedFieldResponse(
                                            value = "에어 조던 1 미드",
                                            boundingBox = OcrResponse.BoundingBoxResponse(yMin = 120, xMin = 80, yMax = 180, xMax = 640),
                                            isInferred = false,
                                        ),
                                    price =
                                        OcrResponse.ExtractedFieldResponse(
                                            value = 119_000,
                                            boundingBox = OcrResponse.BoundingBoxResponse(yMin = 220, xMin = 80, yMax = 260, xMax = 300),
                                            isInferred = false,
                                        ),
                                    category =
                                        OcrResponse.ExtractedFieldResponse(
                                            value = "신발",
                                            boundingBox = null,
                                            isInferred = true,
                                        ),
                                ),
                            ),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "지원하지 않는 이미지 형식",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                status = HttpStatus.BAD_REQUEST,
                                detail = OcrImage.unsupportedMimeTypeMessage("image/gif"),
                            ),
                    )
                    add(
                        status = HttpStatus.BAD_GATEWAY,
                        name = "Gemini 호출 실패",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.RETRYABLE,
                                status = HttpStatus.BAD_GATEWAY,
                                detail = "Gemini 호출 실패",
                            ),
                    )
                }
            }
            operation
        }
}
