package com.depromeet.piki.admin.web

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * PIKI 백오피스 셸의 SSR 뷰(Thymeleaf). 로그인·홈(대시보드)·기능 페이지를 반환한다.
 *
 * AI 어시스턴트(챗봇)는 백오피스의 첫 기능이며, 향후 기능이 늘면 여기 뷰 매핑과 사이드바 네비 메뉴를 더한다.
 * 채팅 동작 자체의 JSON API 는 AdminChatController(/admin/api 경로)가 담당한다.
 * `@Hidden` 으로 OpenAPI 문서에서 제외한다 — 백오피스는 REST 계약 도구가 아니라 내부 운영 페이지다.
 */
@Controller
@Hidden
@ConditionalOnAdminEnabled
class BackofficeViewController {
    // 미인증 접근 가능(SecurityConfig permitAll). 로그인 성공 시 form login 이 홈(/admin)으로 보낸다.
    @GetMapping("/admin/login")
    fun login(): String = "admin/login"

    // 백오피스 홈(대시보드) — 제공 기능을 카드로 보여주는 진입점.
    @GetMapping("/admin")
    fun home(): String = "admin/home"

    // AI 어시스턴트 채팅 화면.
    @GetMapping("/admin/chat")
    fun chat(): String = "admin/chat"
}
