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
                    .requestMatchers(HttpMethod.POST, "/auth/guest")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/token/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/logout")
                    .authenticated()
                    // dev/local 프로파일에서만 빈이 등록되므로 prod 에서는 404 로 막힌다
                    .requestMatchers("/dev/**")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/v1/wishlists/**")
                    .hasAuthority(IdentityType.MEMBER.name)
                    // 토너먼트 플레이는 GUEST 도 허용
                    .requestMatchers("/api/v1/tournaments/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            }.exceptionHandling {
                // 미인증 요청은 401, 인증됐지만 권한 없으면 403
                it.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                }
            }.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
