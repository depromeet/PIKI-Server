package com.depromeet.piki.admin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * admin 백오피스 전용 SecurityFilterChain.
 *
 * 기존 앱 JWT(stateless) 체인과 완전히 분리된다 — securityMatcher 로 admin 경로만 관할하고
 * `@Order(1)` 로 기존 체인(@Order(2))보다 먼저 매칭된다. 인증은 세션 기반 form login + 환경변수로 주입한
 * 단일 admin 계정(InMemory). [ConditionalOnAdminEnabled](`@Profile("!prod")`) 로 게이팅되어 prod 서버에는 등록되지 않는다.
 *
 * UserDetailsService/Provider 를 체인-로컬(`authenticationProvider`)로 묶어, 전역 AuthenticationManager 에
 * admin 계정이 새어 기존 JWT 경로에 의도치 않은 인증 수단이 생기는 것을 막는다.
 */
@Configuration
@ConditionalOnAdminEnabled
class AdminSecurityConfig(
    private val adminProperties: AdminProperties,
) {
    @Bean
    @Order(1)
    fun adminSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val passwordEncoder: PasswordEncoder = BCryptPasswordEncoder()
        // env 로 평문 주입된 비밀번호를 부팅 시 1회 해싱해 InMemory 에 적재한다 (평문이 메모리에 남지 않게).
        val userDetailsService: UserDetailsService =
            InMemoryUserDetailsManager(
                User
                    .withUsername(adminProperties.username)
                    .password(passwordEncoder.encode(adminProperties.password))
                    .roles(ADMIN_ROLE)
                    .build(),
            )
        val authProvider = DaoAuthenticationProvider(userDetailsService)
        authProvider.setPasswordEncoder(passwordEncoder)

        return http
            // /admin/** 와 정적 리소스만 이 체인이 관할. 그 외 요청은 통과시켜 기존 JWT 체인(@Order(2))이 잡는다.
            .securityMatcher("/admin/**", "/admin-assets/**")
            .authenticationProvider(authProvider)
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/admin/login", "/admin-assets/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.formLogin {
                it
                    .loginPage("/admin/login")
                    .loginProcessingUrl("/admin/login")
                    .defaultSuccessUrl("/admin", true)
                    .failureUrl("/admin/login?error")
                    .permitAll()
            }.logout {
                it
                    .logoutUrl("/admin/logout")
                    .logoutSuccessUrl("/admin/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            }
            // form login + Thymeleaf SSR 이므로 CSRF 는 기본(활성) 유지 — 로그인·채팅·확인 POST 가 CSRF 토큰을 동반한다.
            // 세션 기반 인증이라 로그인 시 세션을 만든다.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .build()
    }

    companion object {
        // Spring Security 의 roles(..) 는 내부적으로 ROLE_ 접두사를 붙인다.
        private const val ADMIN_ROLE = "ADMIN"
    }
}
