package com.depromeet.piki.admin.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// admin 공통 헤더(환경·접속 IP)용 인터셉터를 /admin/** 에 등록한다. admin 빈 게이트(ConditionalOnAdminEnabled) 아래에서만
// 로드돼, admin 이 꺼진 환경에선 인터셉터 자체가 등록되지 않는다.
@Configuration
@ConditionalOnAdminEnabled
class AdminWebConfig(
    private val adminProperties: AdminProperties,
    private val environment: Environment,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(AdminHeaderInterceptor(adminProperties, environment))
            .addPathPatterns("/admin/**")
    }
}
