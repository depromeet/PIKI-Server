package com.depromeet.piki.admin.config

import org.springframework.context.annotation.Profile

/**
 * admin 백오피스 관련 빈(SecurityFilterChain·컨트롤러·서비스 등)을 한 어노테이션으로 게이팅한다.
 *
 * `prod` 프로파일이 아닌 환경에서만 부착 대상 빈이 등록된다. prod 서버에서는 컨트롤러 핸들러 매핑조차
 * 생성되지 않아 admin 경로가 404 가 되고, 운영 노출이 코드 레벨에서 차단된다. 메타 어노테이션으로 묶어
 * 빈마다 조건을 반복하다 한 곳을 빠뜨려 일부만 노출되는 사고를 막는다.
 * dev/local 에서는 항상 활성 — 별도 환경변수 토글 없이 프로파일로 통일한다.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Profile("!prod")
annotation class ConditionalOnAdminEnabled
