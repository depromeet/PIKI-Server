package com.depromeet.piki.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {
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
                        "https://depromeet18team3.cloud",
                        "https://www.depromeet18team3.cloud",
                        "https://depromeet.vercel.app",
                        // dev 프론트 — dev.api.depromeet18team3.cloud(dev 서버)를 호출하는 dev 프론트 origin.
                        // (Apple 콜백이 www.dev 를 쓰는 등 호스트가 갈릴 수 있어 둘 다 허용)
                        "https://dev.depromeet18team3.cloud",
                        "https://www.dev.depromeet18team3.cloud",
                        // 우리 서버 자체 origin — Stoplight UI (/docs/index.html) 의 try-it 이
                        // 브라우저 fetch(mode=cors) 로 호출 시 same-origin 이어도 Origin 헤더가 박혀
                        // CORS 검사를 거치므로 화이트리스트에 포함되어야 한다. (prod·dev API 둘 다)
                        "https://api.depromeet18team3.cloud",
                        "https://dev.api.depromeet18team3.cloud",
                        // 프론트엔드 로컬 개발 환경 (Next.js dev server) — 로컬에서 배포 서버 API 를
                        // 호출하며 통합 테스트할 때 Origin 헤더가 박혀 CORS 검사를 거치므로 허용한다.
                        // localhost 와 127.0.0.1 은 브라우저상 서로 다른 origin 이라 둘 다 명시한다.
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                    )
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
