package com.depromeet.piki.admin.access

import com.depromeet.piki.admin.audit.AdminAuditAction
import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.config.ClientIp
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// 백오피스 접근의 유일한 공개 표면 — 슬랙 슬래시커맨드 + grant 링크. 두 게이트 필터(EnvironmentAccessFilter·
// AdminAccessFilter)는 이 경로(/admin-access/**)를 항상 통과시킨다(여기서 IP 를 등록해야 게이트를 열 수 있으므로).
@RestController
@ConditionalOnAdminEnabled
@RequestMapping("/admin-access")
class SlackAccessController(
    private val signatureVerifier: SlackSignatureVerifier,
    private val allowlistService: AdminAllowlistService,
    private val auditService: AdminAuditService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Slack 슬래시커맨드 수신(application/x-www-form-urlencoded). 서명 검증 후 명령 분기:
    //   (인자 없음)      → 원타임 grant 링크 발급(A. 그 기기에서 링크 열면 IP 자동 캡처)
    //   <ip>            → 직접 등록(B)
    //   list            → 현재 허용 IP 목록
    //   revoke <ip>     → 해제
    @PostMapping("/slack", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun slack(
        request: HttpServletRequest,
    ): Map<String, String> {
        val rawBody = request.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        val valid =
            signatureVerifier.verify(
                timestamp = request.getHeader("X-Slack-Request-Timestamp"),
                signature = request.getHeader("X-Slack-Signature"),
                rawBody = rawBody,
            )
        if (!valid) return ephemeral("서명 검증 실패")

        val params = parseForm(rawBody)
        val slackUserId = params["user_id"] ?: "unknown"
        val slackName = params["user_name"] ?: "unknown"
        val text = (params["text"] ?: "").trim()

        return when {
            text.isEmpty() -> issueGrantLink(request, slackUserId, slackName)
            text == "list" -> listAllowed()
            text.startsWith("revoke ") -> revoke(text.removePrefix("revoke ").trim(), slackName)
            else -> grantDirect(text, slackUserId, slackName)
        }
    }

    // grant 링크 클릭(A) — 토큰 검증 후 접속자 IP 를 자동 캡처해 등록 + 세션 발급(슬랙 신원·IP 바인딩) → /admin 이동.
    @GetMapping("/grant")
    fun grant(
        @RequestParam token: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val identity =
            allowlistService.consumeGrantToken(token) ?: run {
                // setStatus 로 끝낸다(sendError 금지) — sendError 는 /error 로 ERROR 디스패치를 일으키고,
                // /error 는 admin 체인 밖이라 메인 JWT 체인이 401 로 가로채 링크가 늘 401 나던 버그가 있었다.
                response.status = HttpServletResponse.SC_NOT_FOUND
                response.contentType = "text/plain;charset=UTF-8"
                response.writer.write("링크가 만료됐거나 유효하지 않습니다. 슬랙에서 다시 발급하세요.")
                return
            }
        val ip = ClientIp.of(request)
        allowlistService.grant(ip, identity.name)
        AdminSession.establish(request.getSession(true), identity.userId, identity.name, ip)
        auditService.record(identity.name, AdminAuditAction.ACCESS_GRANTED, "원타임 링크로 접근 허용(IP 캡처)", ip)
        response.sendRedirect("/admin")
    }

    private fun issueGrantLink(
        request: HttpServletRequest,
        slackUserId: String,
        slackName: String,
    ): Map<String, String> {
        val token = allowlistService.issueGrantToken(slackUserId, slackName)
        val link = "${baseUrl(request)}/admin-access/grant?token=$token"
        return ephemeral("접속할 기기에서 이 링크를 여세요 (5분 내, 그 기기 IP 가 등록됩니다):\n$link")
    }

    private fun grantDirect(
        ip: String,
        slackUserId: String,
        slackName: String,
    ): Map<String, String> {
        // 오타로 엉뚱한 값이 allowlist 키로 박히는 걸 막는다 — 형식 안 맞으면 등록하지 않고 안내만.
        if (!isValidIp(ip)) return ephemeral("유효한 IP 형식이 아닙니다: `$ip` (예: 121.130.45.67)")
        allowlistService.grant(ip, slackName)
        auditService.record(slackName, AdminAuditAction.ACCESS_GRANTED, "직접 입력으로 IP $ip 허용", ip)
        return ephemeral("IP $ip 를 허용했습니다 (등록자: $slackName).")
    }

    // IPv4 는 옥텟 범위까지 엄격히, IPv6 는 hex·콜론 구성만 느슨히 본다(정확한 파싱보다 오타 차단이 목적).
    private fun isValidIp(ip: String): Boolean {
        IPV4.matchEntire(ip)?.let { m -> return m.groupValues.drop(1).all { it.toInt() in 0..255 } }
        return ip.contains(":") && ip.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
    }

    private fun listAllowed(): Map<String, String> {
        val lines = allowlistService.list().joinToString("\n") { "• ${it.ip} — ${it.name}" }.ifBlank { "(허용된 IP 없음)" }
        return ephemeral("현재 허용된 IP:\n$lines")
    }

    private fun revoke(
        ip: String,
        slackName: String,
    ): Map<String, String> {
        allowlistService.revoke(ip)
        auditService.record(slackName, AdminAuditAction.ACCESS_REVOKED, "IP $ip 허용 해제", ip)
        return ephemeral("IP $ip 허용을 해제했습니다.")
    }

    // nginx 가 넘기는 X-Forwarded-* 로 외부에서 본 base URL 을 만든다(컨테이너 내부 localhost:8080 가 아니라).
    private fun baseUrl(request: HttpServletRequest): String {
        val proto = request.getHeader("X-Forwarded-Proto")?.ifBlank { null } ?: request.scheme
        val host = request.getHeader("X-Forwarded-Host")?.ifBlank { null } ?: request.getHeader("Host") ?: "localhost"
        return "$proto://$host"
    }

    private fun parseForm(body: String): Map<String, String> =
        body
            .split("&")
            .filter { it.contains("=") }
            .associate {
                val (k, v) = it.split("=", limit = 2)
                URLDecoder.decode(k, StandardCharsets.UTF_8) to URLDecoder.decode(v, StandardCharsets.UTF_8)
            }

    private fun ephemeral(text: String): Map<String, String> = mapOf("response_type" to "ephemeral", "text" to text)

    companion object {
        private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    }
}
