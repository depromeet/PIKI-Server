package com.depromeet.piki.metrics.activity

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// 일별 활동 캡처 인터셉터를 전 경로에 등록한다. principal 이 UUID 인 인증 요청만 실제 기록되고 나머지는 무시되므로
// path 제외 없이 둔다(활성 유저 측정이 목적). 루트 redirect 를 담당하는 WebConfig 와 관심사를 분리해 metrics 패키지에 둔다.
@Configuration
class MetricsWebConfig(
    private val dailyActivityRecordingInterceptor: DailyActivityRecordingInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(dailyActivityRecordingInterceptor)
    }
}
