package com.depromeet.piki.admin.item

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import io.swagger.v3.oas.annotations.Hidden
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.security.Principal

/**
 * 백오피스 실험 데이터 폼 페이지.
 *
 * 최근 item 목록을 보여주고, 샘플 상품을 폼으로 추가한다. 추가는 PRG(POST-Redirect-Get) 패턴 — 성공/실패를
 * flash 로 전달해 새로고침 중복 제출을 막는다. 확인은 폼의 JS confirm 으로 처리한다(챗봇식 2단계 흐름 불필요).
 * `@Hidden` 으로 OpenAPI 문서에서 제외한다.
 */
@Controller
@Hidden
@ConditionalOnAdminEnabled
class AdminItemController(
    private val adminItemService: AdminItemService,
) {
    @GetMapping("/admin/items")
    fun items(model: Model): String {
        model.addAttribute("items", adminItemService.recentItems())
        return "admin/items"
    }

    @PostMapping("/admin/items/samples")
    fun addSamples(
        @RequestParam count: Int,
        @RequestParam(required = false) namePrefix: String?,
        principal: Principal,
        redirectAttributes: RedirectAttributes,
    ): String {
        try {
            val inserted = adminItemService.insertSamples(count, namePrefix, principal.name)
            redirectAttributes.addFlashAttribute("message", "샘플 상품 ${inserted}개를 추가했습니다.")
        } catch (e: IllegalArgumentException) {
            // 폼 입력 검증 실패 — 사용자에게 사유를 보여주고 폼으로 돌려보낸다.
            log.info("admin 샘플 추가 입력 오류: {}", e.message)
            redirectAttributes.addFlashAttribute("error", e.message)
        }
        return "redirect:/admin/items"
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminItemController::class.java)
    }
}
