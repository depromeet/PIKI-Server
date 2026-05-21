package com.depromeet.team3.auth.config

import com.depromeet.team3.auth.filter.JwtAuthenticationFilter
import com.depromeet.team3.user.domain.IdentityType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // 배포 워크플로우의 health check (`curl http://localhost:$PORT/health`) 가 인증 없이
                    // 통과해야 한다. anyRequest().authenticated() 에 잡히면 401 응답 → 워크플로우 실패 →
                    // 신규 컨테이너 롤백 → 배포 차단으로 이어진다.
                    .requestMatchers(HttpMethod.GET, "/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/guest")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/token/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                    .authenticated()
                    .requestMatchers("/api/v1/dev/**")
                    .hasAuthority(IdentityType.GUEST.name)
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/v1/wishlists/**")
                    .hasAuthority(IdentityType.MEMBER.name)
                    // 토너먼트 플레이는 GUEST 도 허용
                    .requestMatchers("/api/v1/tournaments/**")
                    .authenticated()
                    // /users/me 와 /users/nickname/check 는 게스트도 호출 가능 (닉네임 확정/수정 흐름)
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/me", "/api/v1/users/nickname/check")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me")
                    .authenticated()
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling {
                // 미인증 요청은 401, 인증됐지만 권한 없으면 403
                it.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                }
            }.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
