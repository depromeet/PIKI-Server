package com.depromeet.piki.admin.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * admin 챗봇 관련 빈(SecurityFilterChain·컨트롤러·서비스·tool 등)을 한 어노테이션으로 게이팅한다.
 *
 * `admin.enabled=true` 인 환경에서만 부착 대상 빈이 등록된다. 기본(false)에선 컨트롤러 핸들러 매핑조차
 * 생성되지 않아 admin 경로가 404 가 되고, 운영 노출이 코드 레벨에서 차단된다. 메타 어노테이션으로 묶어
 * 빈마다 `@ConditionalOnProperty` 를 반복하다 한 곳을 빠뜨려 일부만 노출되는 사고를 막는다.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(prefix = "admin", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnAdminEnabled
