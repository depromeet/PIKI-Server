package com.depromeet.team3.common.exception

import com.depromeet.team3.common.response.ApiResponseBody
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(e: BaseException): ResponseEntity<ApiResponseBody<Nothing>> {
        log.warn("[{}] {}", e.javaClass.simpleName, e.message, e)
        val status = if (e is HttpMappable) e.httpStatus else HttpStatus.INTERNAL_SERVER_ERROR
        val category = if (e is HttpMappable) e.category else ErrorCategory.SERVER_ERROR
        return ResponseEntity
            .status(status)
            .body(ApiResponseBody.fail(category, status, e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponseBody<Nothing>> {
        // 첫 번째 위반 필드만 사용자 메시지로 노출. 나머지는 로그로.
        // 응답 스키마는 다른 4xx 와 동일하게 ApiResponseBody 로 통일한다.
        val first = e.bindingResult.fieldErrors.firstOrNull()
        val detail = first
            ?.let { "${it.field}: ${it.defaultMessage ?: "유효하지 않은 값입니다."}" }
            ?: "요청 본문 검증 실패"
        log.warn("[ValidationException] {}", e.bindingResult.fieldErrors)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponseBody.fail(ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST, detail))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ApiResponseBody<Nothing>> {
        log.warn("[IllegalArgumentException] {}", e.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponseBody.fail(ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST, e.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponseBody<Nothing>> {
        log.error("[UnexpectedException] {}", e.message, e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponseBody.fail(ErrorCategory.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR))
    }
}
