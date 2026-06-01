package com.depromeet.piki.common.exception

import com.depromeet.piki.common.response.ApiResponseBody
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

// ResponseEntityExceptionHandler 를 상속해 Spring 표준 MVC 예외(HttpMessageNotReadable·메서드 미지원·
// 미디어타입 미지원·필수 파라미터 누락·타입 불일치 등)를 올바른 4xx 로 처리한다.
// 상속 전에는 이들이 아래 catch-all `Exception` 에 먼저 잡혀 전부 500 으로 샜다 — 클라이언트 입력 실수가
// 서버 오류로 응답되던 전역 갭(#300). RESEH 의 표준 핸들러가 status 를 정하고, handleExceptionInternal
// override 가 응답 바디만 우리 ApiResponseBody 포맷으로 통일한다. catch-all 은 예상 못한 서버 버그만 500.
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(e: BaseException): ResponseEntity<ApiResponseBody<Nothing>> {
        log.info("[{}] {}", e.javaClass.simpleName, e.message)
        val status = if (e is HttpMappable) e.httpStatus else HttpStatus.INTERNAL_SERVER_ERROR
        val category = if (e is HttpMappable) e.category else ErrorCategory.SERVER_ERROR
        return ResponseEntity
            .status(status)
            .body(ApiResponseBody.fail(category, e.message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ApiResponseBody<Nothing>> {
        log.info("[IllegalArgumentException] {}", e.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponseBody.fail(ErrorCategory.INVALID_INPUT, e.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponseBody<Nothing>> {
        log.error("[UnexpectedException] {}", e.message, e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponseBody.fail(ErrorCategory.SERVER_ERROR))
    }

    // RESEH 의 모든 표준 예외 핸들러가 최종적으로 이 메서드를 거쳐 응답 바디를 만든다 → ApiResponseBody 로 통일.
    // status 는 RESEH 가 정한 값을 그대로 쓰고(예: HttpMessageNotReadable→400, 메서드 미지원→405, 미디어타입→415),
    // 바디만 우리 래퍼로 교체한다.
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val status = HttpStatus.valueOf(statusCode.value())
        // 5xx 는 서버 버그 신호이므로 스택 트레이스와 함께 error 로, 4xx(클라 계약 위반)는 info 로 남긴다.
        if (status.is5xxServerError) {
            log.error("[{}] {}", ex.javaClass.simpleName, ex.message, ex)
        } else {
            log.info("[{}] {} → {}", ex.javaClass.simpleName, ex.message, status.value())
        }
        val wrapped: ApiResponseBody<Nothing> = ApiResponseBody.fail(categoryOf(status), detailOf(ex))
        return ResponseEntity.status(statusCode).headers(headers).body(wrapped)
    }

    // status → 우리 ErrorCategory. 인증/권한/리소스/충돌은 각자, 그 외 4xx 는 입력 오류, 5xx 는 서버 오류.
    private fun categoryOf(status: HttpStatus): ErrorCategory =
        when {
            status == HttpStatus.UNAUTHORIZED -> ErrorCategory.UNAUTHORIZED
            status == HttpStatus.FORBIDDEN -> ErrorCategory.FORBIDDEN
            status == HttpStatus.NOT_FOUND -> ErrorCategory.NOT_FOUND
            status == HttpStatus.CONFLICT -> ErrorCategory.CONFLICT
            status.is4xxClientError -> ErrorCategory.INVALID_INPUT
            else -> ErrorCategory.SERVER_ERROR
        }

    // 검증 실패만 첫 위반 필드를 노출해 사용자 수정을 돕는다. 그 외 표준 예외는 내부 메시지(파서 세부 등)를
    // 노출하지 않도록 null 로 두어, fail 이 category 의 고정 문구로 응답하게 한다(detail 노이즈·정보 누출 방지).
    private fun detailOf(ex: Exception): String? =
        when (ex) {
            is MethodArgumentNotValidException ->
                ex.bindingResult.fieldErrors.firstOrNull()
                    ?.let { "${it.field}: ${it.defaultMessage ?: "유효하지 않은 값입니다."}" }
                    ?: "요청 본문 검증 실패"
            else -> null
        }
}
