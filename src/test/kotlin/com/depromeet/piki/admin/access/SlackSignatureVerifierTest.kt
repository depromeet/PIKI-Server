package com.depromeet.piki.admin.access

import com.depromeet.piki.admin.config.AdminProperties
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 슬랙 슬래시커맨드 서명 검증(#526) — 공개 엔드포인트의 유일한 보호막이라 분기를 단위로 망라한다.
// HMAC·5분 replay 윈도우는 순수 로직이라 Spring·네트워크 없이 검증한다(테스트가 슬랙과 동일하게 서명을 만들어 넣는다).
class SlackSignatureVerifierTest {
    private val secret = "8f742231b10e8c1a9bdeadbeefcafe00"
    private val verifier = SlackSignatureVerifier(AdminProperties(slackSigningSecret = secret))

    // 슬랙과 동일한 방식으로 서명 생성: "v0=" + HMAC-SHA256(secret, "v0:{ts}:{body}").
    private fun sign(
        ts: String,
        body: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return "v0=" + mac.doFinal("v0:$ts:$body".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun now() = Instant.now().epochSecond.toString()

    @Test
    fun `유효 서명과 최근 timestamp 면 통과한다`() {
        val ts = now()
        val body = "command=%2Fpiki-admin-dev&user_name=theo&text="
        assertTrue(verifier.verify(ts, sign(ts, body), body))
    }

    @Test
    fun `5분을 넘은 timestamp 는 거부한다 (replay 방지)`() {
        val ts = (Instant.now().epochSecond - 301).toString()
        val body = "text="
        assertFalse(verifier.verify(ts, sign(ts, body), body))
    }

    @Test
    fun `서명이 안 맞으면 거부한다`() {
        assertFalse(verifier.verify(now(), "v0=deadbeef", "text="))
    }

    @Test
    fun `timestamp 나 서명이 없으면 거부한다`() {
        assertFalse(verifier.verify(null, "v0=x", "body"))
        assertFalse(verifier.verify(now(), null, "body"))
    }

    @Test
    fun `숫자가 아닌 timestamp 는 거부한다`() {
        assertFalse(verifier.verify("not-a-number", "v0=x", "body"))
    }

    @Test
    fun `signing secret 미설정이면 항상 거부한다 (로컬·미설정 환경)`() {
        val noSecret = SlackSignatureVerifier(AdminProperties(slackSigningSecret = ""))
        val ts = now()
        assertFalse(noSecret.verify(ts, sign(ts, "body"), "body"))
    }
}
