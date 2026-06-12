package com.depromeet.piki.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
    private val environment: Environment,
) {
    // Spring Security 의 CorsFilter 가 인가(authorization) 이전에 이 source 를 읽어 CORS 를 처리한다.
    // WebMvcConfigurer.addCorsMappings 로 두면 preflight(OPTIONS) 요청이 Security 필터의
    // anyRequest().authenticated() 에 먼저 잡혀 401 이 되므로(브라우저는 인증 헤더/JSON 요청 전
    // preflight 를 보낸다), CorsConfigurationSource 빈 + SecurityConfig 의 http.cors() 로 일원화한다.
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins =
                    listOf(
                        // Vercel 프로젝트 기본 URL — depromeet18team3.cloud 도메인과 별개라 retire 대상이 아니다.
                        "https://depromeet.vercel.app",
                        // piki.day — 프론트(Vercel: piki.day·www·dev) + 우리 서버 self-origin(api·dev.api 는
                        // Stoplight UI(/docs/index.html) try-it 이 same-origin 이어도 Origin 헤더가 박혀 필요).
                        // (#488 retire 로 옛 depromeet18team3.cloud 도메인 origin 은 제거됨.)
                        "https://piki.day",
                        "https://www.piki.day",
                        "https://dev.piki.day",
                        "https://www.dev.piki.day",
                        "https://api.piki.day",
                        "https://dev.api.piki.day",
                        // staging 환경(#498) — staging.piki.day 프론트 + staging.api self-origin(Stoplight try-it).
                        "https://staging.piki.day",
                        "https://www.staging.piki.day",
                        "https://staging.api.piki.day",
                        // 프론트엔드 로컬 개발 환경 (Next.js dev server) — 로컬에서 배포 서버 API 를
                        // 호출하며 통합 테스트할 때 Origin 헤더가 박혀 CORS 검사를 거치므로 허용한다.
                        // localhost 와 127.0.0.1 은 브라우저상 서로 다른 origin 이라 둘 다 명시한다.
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                    )
                // 실기기 dev 테스트(#509): 폰 브라우저가 LAN IP(http://192.168.x.y:port)로 프론트에 접속하면
                // Origin 이 그 사설망 IP 로 박혀 CORS 에 걸린다. dev·local 에서만 사설망 대역을 allowedOriginPatterns 로
                // 허용해 폰이 백엔드를 직접 호출하게 한다(프론트의 요청 수동 재작성 우회 제거).
                // 분기 기준은 'prod' 프로파일이다 — staging 도 deploy 가 'prod' 프로파일로 띄우므로(#498: pre-prod=prod
                // 동일 동작) prod 와 함께 LAN 이 닫힌다. 즉 LAN 은 dev(프로파일 dev)·local 에서만 열린다.
                // allowedOrigins(정확) + allowedOriginPatterns(와일드카드)는 한 설정에 공존하며, allowCredentials=true 와도
                // patterns 는 호환된다(allowedOrigins=["*"] 만 금지).
                if (!environment.acceptsProfiles(Profiles.of("prod"))) {
                    allowedOriginPatterns =
                        listOf(
                            "http://192.168.*.*:[*]", // 가정·사무실 LAN (가장 흔함)
                            "http://10.*.*.*:[*]",
                            "http://172.*.*.*:[*]",
                            "http://localhost:[*]",
                            "http://127.0.0.1:[*]",
                        )
                }
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
