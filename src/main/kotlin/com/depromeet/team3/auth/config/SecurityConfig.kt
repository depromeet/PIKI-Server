package com.depromeet.team3.auth.config

import com.depromeet.team3.auth.filter.JwtAuthenticationFilter
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.response.ApiResponseBody
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val objectMapper: ObjectMapper,
) {
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
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/health", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/guest", "/auth/token/refresh")
                    .permitAll()
                    .requestMatchers("/dev/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "${MediaType.APPLICATION_JSON_VALUE};charset=UTF-8"
                    response.writer.write(
                        objectMapper.writeValueAsString(
                            ApiResponseBody.fail<Nothing>(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
                        ),
                    )
                }
            }.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
