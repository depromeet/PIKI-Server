package com.depromeet.piki.common.controller

import io.swagger.v3.oas.annotations.security.SecurityRequirements
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    // 배포 헬스체크 진입점 (SecurityConfig 의 permitAll). 글로벌 Bearer 요구를 해제한다.
    @SecurityRequirements
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> = ResponseEntity.ok(mapOf("status" to "ok"))
}
