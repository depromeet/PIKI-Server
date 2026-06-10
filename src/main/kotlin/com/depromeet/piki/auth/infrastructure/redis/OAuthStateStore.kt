package com.depromeet.piki.auth.infrastructure.redis

interface OAuthStateStore {
    // state 를 10분 TTL 로 Redis 에 저장한다.
    fun store(state: String)

    // state 가 유효하면 삭제(1회 사용)하고 true, 없거나 이미 소비됐으면 false.
    fun consumeIfValid(state: String): Boolean
}
