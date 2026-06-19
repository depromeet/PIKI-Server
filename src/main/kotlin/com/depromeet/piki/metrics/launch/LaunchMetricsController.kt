package com.depromeet.piki.metrics.launch

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime

// 런칭데이 리캡 화면(admin). 슬랙-IP 게이트(/admin/**)·admin.enabled 아래에서만 노출된다. from·to(KST datetime)로
// 조회 구간을 직접 지정한다 — 예: 6/20 13:00~18:00. 미지정이면 런칭일 00:00~지금. 새로고침할수록 최신 집계.
// datetime-local 입력값(yyyy-MM-ddTHH:mm)을 그대로 바인딩한다.
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
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        from: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        to: LocalDateTime?,
        model: Model,
    ): String {
        model.addAttribute("recap", launchMetricsService.recap(from, to))
        return "admin/launch-recap"
    }
}
