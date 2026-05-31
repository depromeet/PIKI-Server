package com.depromeet.piki.admin.web

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * PIKI 백오피스 셸의 SSR 뷰(Thymeleaf). 로그인·홈(대시보드)을 반환한다.
 *
 * 각 기능 페이지는 자기 컨트롤러가 담당한다(예: 실험 데이터는 AdminItemController). 기능이 늘면 사이드바 네비
 * 메뉴(layout fragment)와 해당 기능 컨트롤러를 더한다. `@Hidden` 으로 OpenAPI 문서에서 제외한다.
 */
@Controller
@Hidden
@ConditionalOnAdminEnabled
class BackofficeViewController {
    // 미인증 접근 가능(permitAll). 로그인 성공 시 form login 이 홈(/admin)으로 보낸다.
    @GetMapping("/admin/login")
    fun login(): String = "admin/login"

    // 백오피스 홈(대시보드) — 제공 기능을 카드로 보여주는 진입점.
    @GetMapping("/admin")
    fun home(): String = "admin/home"
}
