package com.depromeet.piki.metrics.launch

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

// 런칭데이 리캡 화면(admin). 슬랙-IP 게이트(/admin/**)·admin.enabled 아래에서만 노출된다. date 파라미터로 런칭
// 경계를 조정할 수 있고 기본은 2026-06-20. 새로고침(재요청)할수록 "런칭 후" 수치가 커진다(on-demand 집계).
@Hidden
@Controller
@ConditionalOnAdminEnabled
@RequestMapping("/admin/metrics")
class LaunchMetricsController(
    private val launchMetricsService: LaunchMetricsService,
) {
    @GetMapping("/launch")
    fun launch(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?,
        model: Model,
    ): String {
        val launchDate = date ?: LaunchMetricsService.DEFAULT_LAUNCH_DATE
        model.addAttribute("recap", launchMetricsService.recap(launchDate))
        model.addAttribute("launchDate", launchDate)
        return "admin/launch-recap"
    }
}
