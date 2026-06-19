package com.depromeet.piki.admin.access

import com.depromeet.piki.admin.config.AdminProperties
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

// Slack 슬래시커맨드 인바운드 진위 검증 — 공개 엔드포인트(/admin-access/slack)의 유일한 보호막.
// Slack 은 `v0:{timestamp}:{rawBody}` 를 signing secret 으로 HMAC-SHA256 해 `X-Slack-Signature: v0=...` 로 보낸다.
// signing secret 이 없으면(로컬) 항상 거부 — 로컬은 슬랙을 안 쓰고 localBypass 로 게이트를 건너뛴다.
@Component
@ConditionalOnAdminEnabled
class SlackSignatureVerifier(
    private val adminProperties: AdminProperties,
) {
    fun verify(
        timestamp: String?,
        signature: String?,
        rawBody: String,
    ): Boolean {
        val secret = adminProperties.slackSigningSecret.ifBlank { return false }
        timestamp ?: return false
        signature ?: return false
        // replay 방지 — 요청 timestamp 가 현재로부터 5분 이내여야 한다.
        val ts = timestamp.toLongOrNull() ?: return false
        if (abs(Instant.now().epochSecond - ts) > REPLAY_WINDOW_SECONDS) return false
        val expected = "v0=" + hmacSha256Hex(secret, "v0:$timestamp:$rawBody")
        // 타이밍 공격 방지 — 길이·내용 비교를 상수시간으로.
        return MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
    }

    private fun hmacSha256Hex(
        secret: String,
        data: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val REPLAY_WINDOW_SECONDS = 300L
    }
}
