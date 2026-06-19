package com.depromeet.piki.admin.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * admin 백오피스 빈(SecurityFilterChain·컨트롤러·서비스 등)을 한 어노테이션으로 게이팅한다.
 *
 * 옛 백오피스(#505 에서 제거)는 `@Profile("!prod")` 로 prod 에서 숨기는 dev 보조도구였다. 이 prod 운영
 * 백오피스(#249)는 정반대 — prod 에서 떠야 하므로 profile 배제가 아니라 `admin.enabled` 명시 플래그로 켠다.
 * prod 는 ADMIN_ENABLED=true, 끄려면 false. 빈마다 조건을 반복하다 한 곳을 빠뜨려 일부만 노출되는 사고를
 * 막기 위해 메타 어노테이션으로 묶는다.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(prefix = "admin", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnAdminEnabled
