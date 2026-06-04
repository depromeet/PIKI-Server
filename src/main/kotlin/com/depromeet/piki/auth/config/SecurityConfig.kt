package com.depromeet.piki.auth.config

import com.depromeet.piki.auth.filter.JwtAuthenticationFilter
import com.depromeet.piki.user.domain.IdentityType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val authenticationEntryPoint: ApiResponseAuthenticationEntryPoint,
    private val accessDeniedHandler: ApiResponseAccessDeniedHandler,
) {
    // @Order(2): admin 백오피스 체인(AdminSecurityConfig, @Order(1))이 /admin/** 를 먼저 잡고,
    // 나머지 모든 요청은 이 기존 JWT(stateless) 체인이 처리한다. prod 환경에서는 admin 체인이
    // 아예 없어 이 체인만 단독으로 동작한다.
    @Bean
    @Order(2)
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
    ): SecurityFilterChain =
        http
            // CorsConfigurationSource 빈을 사용해 CORS 를 Security 필터 단에서 처리한다.
            // 이걸 빼면 preflight(OPTIONS) 가 anyRequest().authenticated() 에 잡혀 401 이 된다.
            .cors { }
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
                    // actuator health/prometheus 는 EC2 내부의 Grafana Alloy 가 localhost 로
                    // 직접 scrape 한다 (nginx 미경유). 외부 도달은 nginx 가 /actuator/ 를 403 으로
                    // 차단(infra/nginx/...conf)하므로, 앱 레벨 permitAll + 네트워크 레벨 차단의 2층 구조다.
                    // metrics·env 등 나머지 actuator 엔드포인트는 application.yml 에서 애초에 노출하지 않는다.
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/guest")
                    .permitAll()
                    // OAuth 인가 URL 생성 — state 발급 포함. 미인증으로 호출 (로그인 전 단계).
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/*/url")
                    .permitAll()
                    // 소셜 로그인 진입점 — 미인증으로 호출(게스트 토큰은 선택). 게스트 토큰을 보내면 필터가 principal 을 채워 게스트-연결로 동작.
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/token/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/dev/users", "/api/v1/dev/users/*")
                    .permitAll()
                    .requestMatchers("/api/v1/dev/**")
                    .hasAuthority(IdentityType.GUEST.name)
                    // API 문서: Stoplight Elements UI (/docs/**, static resource) + OpenAPI spec
                    // (/v3/api-docs/**, springdoc 제공). Swagger UI 는 사용하지 않음.
                    .requestMatchers("/docs/**", "/v3/api-docs/**")
                    .permitAll()
                    // 파비콘 — 브라우저가 모든 페이지에서 자동 요청하는 사이트 전역 정적 자산.
                    // admin 체인(/admin/**)이 안 잡고 이 체인으로 떨어지므로 여기서 permit 해야
                    // docs·admin 어디서든 401 없이 뜬다. 민감정보 없는 공개 파일.
                    .requestMatchers(HttpMethod.GET, "/favicon.ico")
                    .permitAll()
                    .requestMatchers("/api/v1/wishlists/**")
                    .hasAuthority(IdentityType.MEMBER.name)
                    // 토너먼트 플레이는 GUEST 도 허용
                    .requestMatchers("/api/v1/tournaments/**")
                    .authenticated()
                    // 알림 실시간 구독(SSE) — 인증 유저가 자기 알림 스트림을 연다 (GUEST 포함).
                    .requestMatchers("/api/v1/notifications/**")
                    .authenticated()
                    // /users/me 와 /users/nickname/check 는 게스트도 호출 가능 (닉네임 확정/수정 흐름)
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/me", "/api/v1/users/nickname/check")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me")
                    .authenticated()
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling {
                // 미인증 요청은 401, 인증됐지만 권한 없으면 403.
                // Security 필터 체인은 DispatcherServlet 이전이라 GlobalExceptionHandler 가 잡지 못하므로,
                // 두 경로 모두 ApiResponseBody contract 로 응답을 직접 작성하는 핸들러를 꽂는다.
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
