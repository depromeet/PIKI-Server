package com.depromeet.piki.auth.infrastructure.redis

import java.util.UUID

// 탈퇴한 회원의 access token 을 만료 전까지 즉시 거부하기 위한 denylist.
// access token 은 stateless JWT 라 개별 폐기가 불가능하다. 그래서 탈퇴 시 userId 를 마킹하고,
// JwtAuthenticationFilter 가 인증 시 이 마커를 확인해 탈퇴 회원의 access token 을 거부한다.
// TTL = access token 만료 시간이라, 그 시간이 지나면 남은 access token 도 전부 만료되므로 마커는 자동 정리된다.
// (로그아웃은 표준대로 refresh-only 유지 — 즉시 무효화는 계정 삭제(탈퇴)에 한정한다.)
interface WithdrawnTokenStore {
    fun markWithdrawn(userId: UUID)

    fun isWithdrawn(userId: UUID): Boolean
}
