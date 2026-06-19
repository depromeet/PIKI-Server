package com.depromeet.piki.admin.config

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class AdminHeaderInterceptorTest {
    // localBypass 가 켜지면 프로파일·게이트와 무관하게 LOCAL. 그 외엔 dev 프로파일이면 DEV,
    // (staging·prod 공유 프로파일에서) 게이트가 열린 staging 은 STAGING, 닫힌 prod 는 PROD.
    @ParameterizedTest(name = "localBypass={0}, isDev={1}, gate={2} -> {3}")
    @CsvSource(
        "true,  false, true,  LOCAL", // 로컬 (localBypass 가 최우선)
        "true,  true,  false, LOCAL", // 로컬은 다른 신호를 가린다
        "false, true,  true,  DEV", // dev 프로파일
        "false, false, true,  STAGING", // prod 프로파일 + 게이트 열림 = staging
        "false, false, false, PROD", // prod 프로파일 + 게이트 닫힘 = prod
    )
    fun `환경 판정은 localBypass→dev프로파일→게이트 순으로 LOCAL·DEV·STAGING·PROD 를 가른다`(
        localBypass: Boolean,
        isDevProfile: Boolean,
        environmentGate: Boolean,
        expected: String,
    ) {
        val env = AdminHeaderInterceptor.resolveEnv(localBypass, isDevProfile, environmentGate)

        assertEquals(expected, env)
    }
}
