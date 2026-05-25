package com.depromeet.piki.common.response

import com.depromeet.piki.common.exception.ErrorCategory
import org.springframework.http.HttpStatus

data class ApiResponseBody<T>(
    val status: Int,
    val data: T?,
    val detail: String,
    val code: String,
    // 모든 응답이 동일한 형태를 갖도록 항상 포함한다. 페이징과 무관한 응답은 "다음 페이지 없음"(EMPTY) 으로 채운다.
    val pageResponse: PageResponse = PageResponse.EMPTY,
) {
    companion object {
        fun <T> ok(
            data: T? = null,
            pageResponse: PageResponse = PageResponse.EMPTY,
        ): ApiResponseBody<T> = success(HttpStatus.OK, data, pageResponse)

        fun <T> created(data: T? = null): ApiResponseBody<T> = success(HttpStatus.CREATED, data)

        fun <T> fail(
            category: ErrorCategory,
            status: HttpStatus,
            detail: String? = null,
        ): ApiResponseBody<T> =
            ApiResponseBody(
                status = status.value(),
                data = null,
                detail = detail ?: category.description,
                code = status.name,
            )

        private fun <T> success(
            status: HttpStatus,
            data: T?,
            pageResponse: PageResponse = PageResponse.EMPTY,
        ): ApiResponseBody<T> =
            ApiResponseBody(
                status = status.value(),
                data = data,
                detail = "정상적으로 처리되었습니다.",
                code = status.name,
                pageResponse = pageResponse,
            )
    }
}
