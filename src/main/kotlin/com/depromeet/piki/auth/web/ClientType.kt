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

        // 헤더 누락·미상 값은 APP 으로 처리한다 (graceful default = body 토큰).
        // 웹이 헤더를 빠뜨려도 body 로 받아 로그인이 깨지지 않고 하드닝만 덜 된다.
        // 또한 curl·Postman·Swagger·dev 스크립트는 헤더 없이 호출하므로 기존 흐름이 그대로 유지된다.
        fun from(raw: String?): ClientType {
            raw ?: return APP
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: APP
        }
    }
}
