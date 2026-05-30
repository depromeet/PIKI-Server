package com.depromeet.piki.auth.web

import com.depromeet.piki.common.response.ApiResponseBody
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

// 토큰 전달의 cross-cutting 처리. 컨트롤러는 TokenCarrying/TokenClearing DTO 를 반환할 뿐이고,
// 쿠키 set/expire 는 여기서 타입 매칭으로 일어난다 (문자열 sniffing 없음).
//
// - WEB: TokenCarrying → 쿠키 set + body 토큰 제거 / TokenClearing → 만료 쿠키
// - APP·미상: 쿠키 미사용, body 그대로 (graceful default = body 토큰)
@RestControllerAdvice
class TokenCookieResponseAdvice(
    private val tokenCookieWriter: TokenCookieWriter,
) : ResponseBodyAdvice<Any> {
    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        val responseBody = body as? ApiResponseBody<*> ?: return body
        return when (val data = responseBody.data) {
            // 토큰 발급: WEB 만 쿠키로 내리고 body 토큰을 비운다. APP·미상은 body 그대로(graceful default).
            is TokenCarrying -> {
                if (!isWebClient(request)) return body
                writeCookies(response, tokenCookieWriter.setCookies(data.tokenPair))
                stripBodyTokens(responseBody, data)
            }
            // 로그아웃: 쿠키 만료는 ClientType 무관하게 무조건 내린다. 웹이 X-Client-Type 을 빠뜨려도
            // 쿠키가 확실히 삭제되도록(fail-safe — 안 지우면 access 쿠키가 만료까지 인증에 쓰임).
            // APP 은 쿠키 jar 가 없어 만료 쿠키를 받아도 무해하다.
            is TokenClearing -> {
                writeCookies(response, tokenCookieWriter.clearCookies())
                body
            }
            else -> body
        }
    }

    // 웹 판별: X-Client-Type: web 이거나, 요청에 우리 토큰 쿠키가 동봉된 경우.
    // 앱(네이티브)은 쿠키 jar 가 없어 우리 쿠키를 절대 보내지 않으므로 false positive 가 없다.
    // 이 덕분에 웹이 refresh 에서 X-Client-Type 을 빠뜨려도(쿠키는 자동 전송) 쿠키 회전이 보장된다.
    // login(첫 접촉)엔 쿠키가 없어 영향이 없고 헤더로만 판별된다.
    private fun isWebClient(request: ServerHttpRequest): Boolean =
        ClientType.from(request.headers.getFirst(ClientType.HEADER)) == ClientType.WEB ||
            carriesAuthCookie(request)

    private fun carriesAuthCookie(request: ServerHttpRequest): Boolean {
        val servletRequest = (request as? ServletServerHttpRequest)?.servletRequest ?: return false
        return servletRequest.cookies?.any {
            it.name == TokenCookieWriter.ACCESS_COOKIE || it.name == TokenCookieWriter.REFRESH_COOKIE
        } ?: false
    }

    private fun writeCookies(
        response: ServerHttpResponse,
        cookies: List<ResponseCookie>,
    ) {
        cookies.forEach { response.headers.add(HttpHeaders.SET_COOKIE, it.toString()) }
    }

    private fun stripBodyTokens(
        original: ApiResponseBody<*>,
        data: TokenCarrying,
    ): ApiResponseBody<*> =
        ApiResponseBody(
            status = original.status,
            data = data.withoutBodyTokens(),
            detail = original.detail,
            code = original.code,
            pageResponse = original.pageResponse,
        )
}
