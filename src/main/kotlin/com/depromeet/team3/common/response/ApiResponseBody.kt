package com.depromeet.team3.common.response

import com.depromeet.team3.common.exception.ErrorCategory
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus

data class ApiResponseBody<T>(
    val status: Int,
    val data: T?,
    val detail: String,
    val code: String,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val pageResponse: PageResponse? = null,
) {
    companion object {

        fun <T> ok(data: T? = null): ApiResponseBody<T> =
            success(HttpStatus.OK, data)

        fun <T> created(data: T? = null): ApiResponseBody<T> =
            success(HttpStatus.CREATED, data)

        fun <T> fail(
            category: ErrorCategory,
            status: HttpStatus,
            detail: String? = null,
        ): ApiResponseBody<T> = ApiResponseBody(
            status = status.value(),
            data = null,
            detail = detail ?: category.description,
            code = status.name,
        )

        private fun <T> success(
            status: HttpStatus,
            data: T?,
        ): ApiResponseBody<T> = ApiResponseBody(
            status = status.value(),
            data = data,
            detail = "정상적으로 처리되었습니다.",
            code = status.name,
        )
    }
}
