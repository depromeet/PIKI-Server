package com.depromeet.piki.notification.service

import com.depromeet.piki.common.storage.S3Properties
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

// 시스템 알림 기본 아바타 URL 조립 — publicBaseUrl + defaults/push-icon.svg. Spring 없이 순수 조립 로직만 검증.
class DefaultPushImageTest {
    @Test
    fun `publicBaseUrl 에 defaults push-icon 경로를 붙인다`() {
        val pushImage = DefaultPushImage(S3Properties(bucket = "b", publicBaseUrl = "https://cdn.example"))

        assertEquals("https://cdn.example/defaults/push-icon.svg", pushImage.url)
    }

    // publicBaseUrl 끝 슬래시는 무시한다(// 중복 방지) — DefaultProfileImages 와 동일 규칙.
    @ParameterizedTest
    @ValueSource(strings = ["https://cdn.example", "https://cdn.example/"])
    fun `끝 슬래시 유무와 무관하게 슬래시 하나로 이어붙인다`(base: String) {
        val pushImage = DefaultPushImage(S3Properties(bucket = "b", publicBaseUrl = base))

        assertEquals("https://cdn.example/defaults/push-icon.svg", pushImage.url)
    }
}
