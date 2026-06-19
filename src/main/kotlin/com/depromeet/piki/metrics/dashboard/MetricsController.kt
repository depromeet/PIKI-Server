package com.depromeet.piki.metrics.dashboard

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime

// 운영 통계 대시보드(admin). 슬랙-IP 게이트(/admin/**)·admin.enabled 아래에서만 노출. 프리셋(preset)이나 직접 지정
// (from·to, datetime-local: yyyy-MM-ddTHH:mm)으로 구간을 잡고, 현재 구간 vs 직전 동일 길이 구간 비교도 함께 내린다.
@Hidden
@Controller
@ConditionalOnAdminEnabled
@RequestMapping("/admin/metrics")
class MetricsController(
    private val metricsService: MetricsService,
) {
    @GetMapping
    fun dashboard(
        @RequestParam(required = false) preset: String?,
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        from: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        to: LocalDateTime?,
        model: Model,
    ): String {
        val range = metricsService.resolveRange(preset, from, to)
        model.addAttribute("snapshot", metricsService.snapshot(range.from, range.to))
        model.addAttribute("comparison", metricsService.compareWithPrevious(range.from, range.to))
        model.addAttribute("activePreset", range.preset)
        return "admin/metrics"
    }
}
