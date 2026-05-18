package com.depromeet.team3.ocr.controller

import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.ocr.controller.dto.OcrResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile

@Tag(name = "OCR", description = "상품 이미지 OCR API")
interface OcrApi {
    @Operation(
        summary = "상품 이미지 OCR 추출",
        description = """
            상품 페이지를 캡처한 이미지를 받아 Gemini Vision 으로 상품명/가격/카테고리를 추출한다.
            각 필드는 이미지에서 직접 읽어낸 값(boundingBox 포함)인지, 추론된 값인지 구분되어 반환된다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OCR 추출 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (빈 이미지 / 지원하지 않는 이미지 형식 등)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "502",
                description = "Gemini 호출/응답 처리 실패",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun extractProduct(image: MultipartFile): ApiResponseBody<OcrResponse>
}
