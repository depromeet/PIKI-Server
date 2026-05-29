package com.depromeet.piki.admin.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 개발 서버 전용 admin 챗봇 설정.
 *
 * `@ConfigurationPropertiesScan`(PikiApplication)으로 자동 등록된다. enabled=false 여도 이 빈 자체는
 * 무해하게 바인딩되며, 실제 admin 빈(SecurityFilterChain·컨트롤러·tool 등)은 [ConditionalOnAdminEnabled]
 * 로 게이팅된다.
 */
@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
    val enabled: Boolean = false,
    val username: String = "",
    val password: String = "",
) {
    init {
        // enabled=true 인데 자격증명이 비어 있으면 "켰는데 로그인 불가" 상태로 부팅된다.
        // 부팅 시점에 막아 운영자가 즉시 알아채게 한다 (GeminiProperties.init 선례와 같은 결).
        if (enabled) {
            require(username.isNotBlank()) { "admin.enabled=true 인데 ADMIN_USERNAME 이 비어 있습니다." }
            require(password.isNotBlank()) { "admin.enabled=true 인데 ADMIN_PASSWORD 가 비어 있습니다." }
        }
    }

    // data class 기본 toString 은 password 를 그대로 노출하므로 로그 유출 방지를 위해 마스킹한다.
    override fun toString(): String = "AdminProperties(enabled=$enabled, username=$username, password=*secret*)"
}
