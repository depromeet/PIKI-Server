package com.depromeet.team3.auth.filter

import com.depromeet.team3.auth.infrastructure.jwt.JwtProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        authenticate(request)
        filterChain.doFilter(request, response)
    }

    private fun authenticate(request: HttpServletRequest) {
        val token = extractToken(request) ?: return
        val userId = jwtProvider.parseAccessToken(token) ?: return
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        return header.substring(BEARER_PREFIX.length)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
