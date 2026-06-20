package com.depromeet.piki.admin.access

import jakarta.servlet.http.HttpSession

// 백오피스 접근 세션 — 슬랙 grant 가 발급한 신원(슬랙 표시명·유저 id)과 바인딩된 IP 를 담는다.
// password·계정이 없으므로 "이 세션은 슬랙으로 검증된 사용자 X" 가 곧 신원이다(감사 actor). boundIp 로 세션-IP 를
// 묶어 쿠키가 탈취돼도 다른 IP 에선 못 쓰게 한다 — AdminAccessFilter 가 요청 IP == boundIp 를 확인한다.
object AdminSession {
    private const val SLACK_NAME = "admin.slackName"
    private const val SLACK_USER_ID = "admin.slackUserId"
    private const val BOUND_IP = "admin.boundIp"

    fun establish(
        session: HttpSession,
        slackUserId: String,
        slackName: String,
        ip: String,
    ) {
        session.setAttribute(SLACK_USER_ID, slackUserId)
        session.setAttribute(SLACK_NAME, slackName)
        session.setAttribute(BOUND_IP, ip)
    }

    fun slackName(session: HttpSession): String? = session.getAttribute(SLACK_NAME) as? String

    fun boundIp(session: HttpSession): String? = session.getAttribute(BOUND_IP) as? String

    fun hasIdentity(session: HttpSession): Boolean = !slackName(session).isNullOrBlank()
}
