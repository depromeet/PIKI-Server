package com.depromeet.team3.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/**")
            .allowedOrigins(
                "https://depromeet.vercel.app",
                // 우리 서버 자체 origin — Stoplight UI (/docs/index.html) 의 try-it 이
                // 브라우저 fetch(mode=cors) 로 호출 시 same-origin 이어도 Origin 헤더가 박혀
                // CORS 검사를 거치므로 화이트리스트에 포함되어야 한다.
                "https://api.depromeet18team3.cloud",
            ).allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
