package com.depromeet.piki.auth.web

import com.depromeet.piki.auth.service.dto.TokenPair

// 응답이 토큰을 싣는다는 타입 계약. TokenCookieResponseAdvice 가 이 타입을 보고
// (WEB 이면) 쿠키를 set 하고 body 토큰을 비운다. 컨트롤러는 이 타입의 DTO 를 반환할 뿐
// 쿠키 동작을 알지 못한다.
interface TokenCarrying {
    val tokenPair: TokenPair

    // WEB 응답용 — body 의 토큰을 노출하지 않는 복제본. advice 가 쿠키 set 후 호출한다.
    fun withoutBodyTokens(): TokenCarrying
}

// 응답이 토큰 쿠키를 비운다는 타입 계약 (logout). advice 가 (WEB 이면) 만료 쿠키를 내린다.
interface TokenClearing
