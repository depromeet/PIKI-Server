package com.depromeet.piki.auth.config

import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.response.ApiResponseBody
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

// Security 필터 체인은 DispatcherServlet 이전 단계라 @RestControllerAdvice 의 GlobalExceptionHandler 가
// 잡지 못한다. 인증·인가 거부 응답도 다른 엔드포인트와 같은 ApiResponseBody contract 로 내려가도록
// EntryPoint·AccessDeniedHandler 를 여기에 직접 구현한다.
//
// detail 은 ErrorCategory.description 의 고정 사용자 대면 문구로 채워, 토큰 파싱 사유 등 내부 정보가
// 응답으로 새지 않게 한다 (CLAUDE.md 의 "클라이언트 응답에 내부 정보 노출 안 함" 정책).

@Component
class ApiResponseAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        log.info("[AuthenticationEntryPoint] {} {} - {}", request.method, request.requestURI, authException.message)
        writeApiResponseBody(response, HttpStatus.UNAUTHORIZED, ErrorCategory.UNAUTHORIZED, objectMapper)
    }
}

@Component
class ApiResponseAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        log.info("[AccessDeniedHandler] {} {} - {}", request.method, request.requestURI, accessDeniedException.message)
        writeApiResponseBody(response, HttpStatus.FORBIDDEN, ErrorCategory.FORBIDDEN, objectMapper)
    }
}

private fun writeApiResponseBody(
    response: HttpServletResponse,
    status: HttpStatus,
    category: ErrorCategory,
    objectMapper: ObjectMapper,
) {
    response.status = status.value()
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.characterEncoding = Charsets.UTF_8.name()
    val body = ApiResponseBody.fail<Nothing>(category)
    response.writer.write(objectMapper.writeValueAsString(body))
}
