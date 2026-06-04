package com.depromeet.piki.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// 루트(/) 는 API 표면이 아니지만 브라우저로 도메인을 직접 열면 들어온다. 매핑이 없으면
// NoResourceFoundException → 404 로그 노이즈가 남으므로, 사람이 루트를 열면 API 문서로
// 안내되도록 Stoplight UI(/docs/index.html) 로 리다이렉트한다. SecurityConfig 가 / 를
// permitAll 로 열어 이 리다이렉트가 인증 벽 앞에서 동작한다.
@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/", "/docs/index.html")
    }
}
