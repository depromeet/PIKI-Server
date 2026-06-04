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
        // 미인증 401 은 클라이언트 계약 위반이라 INFO 로 남긴다(로깅 정책). 단 우리 API 표면(/api/**)에 한정한다.
        // 우리가 인증을 요구하는 경로는 전부 /api/v1/** 라, 정상 클라이언트가 비-API 경로로 401 을 받을 일이 없다 —
        // 루트·favicon·인터넷 스캐너 probe(/test/.git/config·/.env 등)가 만드는 노이즈일 뿐이라 기록하지 않는다.
        // 거절(401 응답)은 그대로 유지하고 로그만 끄는 것 — 보안 경계는 바뀌지 않는다.
        if (request.requestURI.startsWith(API_PATH_PREFIX)) {
            log.info("[AuthenticationEntryPoint] {} {} - {}", request.method, request.requestURI, authException.message)
        }
        writeApiResponseBody(response, HttpStatus.UNAUTHORIZED, ErrorCategory.UNAUTHORIZED, objectMapper)
    }

    private companion object {
        const val API_PATH_PREFIX = "/api/"
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
