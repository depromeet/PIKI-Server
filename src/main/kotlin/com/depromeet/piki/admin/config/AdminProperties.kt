package com.depromeet.piki.admin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * prod 운영 백오피스 설정. `@ConfigurationPropertiesScan`(PikiApplication)으로 자동 등록.
 *
 * 옛 백오피스의 @Profile("!prod") 숨김과 달리 명시 플래그(enabled)로 켠다. 슬랙-IP 접근 게이트(#526)가
 * 들어와 enabled=true 를 전 환경에 둬도 안전하다 — /admin 은 슬랙 세션, dev/staging 도메인은 IP allowlist 로 막힌다.
 *
 * @property enabled 백오피스 빈 전체 게이트(ConditionalOnAdminEnabled). 전 환경 true(게이트가 보호).
 * @property environmentGate dev/staging 도메인 전체를 allowlist IP 로만 여는 게이트(EnvironmentAccessFilter). prod 는 false(공개).
 * @property slackSigningSecret 슬랙 슬래시커맨드 HMAC 검증 키(민감). 비면 슬랙 진입이 항상 거부돼 게이트를 못 연다.
 * @property allowlistTtl 허용 IP sliding TTL(무활동 만료, IP 변동 흡수).
 * @property grantTokenTtl 원타임 grant 링크 토큰 수명(짧게).
 * @property localBypass 로컬 개발에서 /admin 게이트(AdminAccessFilter)를 건너뛴다. 배포 환경은 false.
 * @property scheduleGraceWindow 예약 발송 유예시간. 예약시각을 이 시간보다 더 넘겨 도래하면(다운타임 등) 발송하지 않고 MISSED 로 정리한다.
 * @property schedulerAutoDispatch 스케줄러의 주기 폴링 자동 실행. 테스트는 false 로 끄고 dispatchDue() 를 직접 호출해 결정적으로 검증한다.
 */
@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
    val enabled: Boolean = false,
    val environmentGate: Boolean = false,
    val slackSigningSecret: String = "",
    val allowlistTtl: Duration = Duration.ofHours(24),
    val grantTokenTtl: Duration = Duration.ofMinutes(5),
    val localBypass: Boolean = false,
    val scheduleGraceWindow: Duration = Duration.ofHours(1),
    val schedulerAutoDispatch: Boolean = true,
) {
    // slackSigningSecret 는 크리덴셜이라 로그·디버그 출력에 평문이 새지 않게 마스킹한다(data class toString 자동 노출 차단).
    override fun toString(): String =
        "AdminProperties(enabled=$enabled, environmentGate=$environmentGate, " +
            "slackSigningSecret=${if (slackSigningSecret.isBlank()) "<none>" else "<set>"}, " +
            "allowlistTtl=$allowlistTtl, grantTokenTtl=$grantTokenTtl, localBypass=$localBypass)"
}
