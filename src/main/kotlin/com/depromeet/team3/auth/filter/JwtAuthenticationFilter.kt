package com.depromeet.team3.auth.filter

import com.depromeet.team3.auth.infrastructure.jwt.JwtProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
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
        val token = extractToken(request)
        if (token != null && jwtProvider.validateToken(token) && jwtProvider.isAccessToken(token)) {
            val userId = jwtProvider.getUserIdFromToken(token)
            val authority = SimpleGrantedAuthority(jwtProvider.getIdentityTypeFromToken(token).name)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null, listOf(authority))
        }
        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.substring(7)
    }
}
