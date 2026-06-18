package com.depromeet.piki.admin.web
import io.swagger.v3.oas.annotations.Hidden

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

// 백오피스 SSR 진입점(랜딩). 접근 게이트(슬랙-IP)는 #526 에서 붙는다 — 토대 단계는 게이트 없이 셸만 둔다(admin.enabled
// 기본 false 라 미배포). 실제 관리 기능(알림 템플릿)은 #250.
@Hidden
@Controller
@ConditionalOnAdminEnabled
@RequestMapping("/admin")
class AdminViewController {
    @GetMapping
    fun index(): String = "admin/index"
}
