package com.depromeet.piki.common.response

import com.depromeet.piki.common.exception.ErrorCategory

data class ApiResponseBody<T>(
    val data: T?,
    val detail: String,
    // 모든 응답이 동일한 형태를 갖도록 항상 포함한다. 페이징과 무관한 응답은 "다음 페이지 없음"(EMPTY) 으로 채운다.
    val pageResponse: PageResponse = PageResponse.EMPTY,
) {
    companion object {
        private const val SUCCESS_DETAIL = "완료했어요."

        fun <T> ok(
            data: T? = null,
            pageResponse: PageResponse = PageResponse.EMPTY,
        ): ApiResponseBody<T> = ApiResponseBody(data, SUCCESS_DETAIL, pageResponse)

        fun <T> created(data: T? = null): ApiResponseBody<T> = ApiResponseBody(data, SUCCESS_DETAIL)

        fun <T> fail(
            category: ErrorCategory,
            detail: String? = null,
        ): ApiResponseBody<T> = ApiResponseBody(null, detail ?: category.description)
    }
}
