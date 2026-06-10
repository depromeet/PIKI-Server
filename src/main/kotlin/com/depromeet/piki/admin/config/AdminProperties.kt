package com.depromeet.piki.admin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile

/**
 * 개발 서버 전용 admin 백오피스 설정.
 *
 * `@ConfigurationPropertiesScan`(PikiApplication)으로 자동 등록된다. 실제 admin 빈은
 * [ConditionalOnAdminEnabled](`@Profile("!prod")`)로 게이팅되어 prod 서버에서는 뜨지 않는다.
 * dev/local 에서 자격증명이 비어 있으면 부팅 시점에 실패시켜 "빈 계정으로 로그인 불가" 상태를 막는다.
 */
@Profile("!prod")
@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
    val username: String = "",
    val password: String = "",
) {
    init {
        require(username.isNotBlank()) { "ADMIN_USERNAME 이 비어 있습니다." }
        require(password.isNotBlank()) { "ADMIN_PASSWORD 가 비어 있습니다." }
    }

    // data class 기본 toString 은 password 를 그대로 노출하므로 로그 유출 방지를 위해 마스킹한다.
    override fun toString(): String = "AdminProperties(username=$username, password=*secret*)"
}
