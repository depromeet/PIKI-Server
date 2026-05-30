package com.depromeet.piki.auth.web

// 클라이언트 종류. X-Client-Type 헤더로 구분한다.
// - WEB: HttpOnly 쿠키로 토큰 수신 (body 토큰 생략)
// - APP: body 로 토큰 수신 (쿠키 미사용)
enum class ClientType {
    WEB,
    APP,
    ;

    companion object {
        const val HEADER = "X-Client-Type"

        // secure by default — body 토큰 전달은 브라우저에서 XSS 노출 경로(위험구역)이므로,
        // 위험한 body 는 app 을 명시한 경우에만 내주고, 그 외(web 명시·미상·누락)는 전부 안전한 WEB(쿠키)로 둔다.
        // 브라우저는 X-Client-Type: app 을 보낼 일이 없으므로, 브라우저가 body 토큰을 받는 경우가 구조적으로 없다.
        // 네이티브 앱은 자신을 app 으로 명시해야 body 토큰을 받는다(안 보내면 쿠키를 받아 안전하게 깨질 뿐 토큰 유출은 없음).
        fun from(raw: String?): ClientType = if (raw?.trim().equals(APP.name, ignoreCase = true)) APP else WEB
    }
}
