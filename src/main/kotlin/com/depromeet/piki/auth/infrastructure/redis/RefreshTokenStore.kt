package com.depromeet.piki.auth.infrastructure.redis

import java.util.UUID

interface RefreshTokenStore {
    fun save(
        userId: UUID,
        refreshToken: String,
    )

    fun get(userId: UUID): String?

    // 현재 토큰과 grace 레코드를 함께 지운다 (로그아웃은 grace 창 안의 멱등 replay 도 즉시 끊어야 한다).
    fun delete(userId: UUID)

    // refresh 토큰 회전의 단일 진입점. "GET 현재토큰 → 회전 / grace replay / 거부 / 재사용 무효화" 판정을
    // 하나의 원자 연산(Redis Lua)으로 수행해 동시 요청 race 를 원천 차단한다.
    //
    // - presented: 클라이언트가 제시한 refresh 토큰
    // - candidateRefreshToken: 호출자가 미리 발급해 둔 새 refresh 토큰 (generate-first). 회전 시 이 값이
    //   새 현재 토큰이 되고, replay 로 끝나면 버려진다 (jti 랜덤이라 충돌 없음).
    //
    // 반환은 sealed RefreshOutcome — 호출자는 when(is) 로 분기한다.
    fun rotateOrReplay(
        userId: UUID,
        presented: String,
        candidateRefreshToken: String,
    ): RefreshOutcome
}

sealed interface RefreshOutcome {
    // 제시 토큰이 현재 토큰과 일치 → 회전 완료. 호출자가 넘긴 candidate 가 새 현재 토큰이 됐다.
    data object Rotated : RefreshOutcome

    // grace 창 안에 같은 옛 토큰으로 다시 들어온 동시 요청 → 이미 발급된 토큰을 멱등 반환 (회전·무효화 없음).
    data class Replayed(
        val refreshToken: String,
    ) : RefreshOutcome

    // 저장된 토큰이 없음 (이미 소비됐거나 TTL 만료) → 거부.
    data object Expired : RefreshOutcome

    // 회전 후·grace 밖에서 옛 토큰 재사용 감지 → family invalidation 수행됨, 거부 (도난 의심).
    data object ReuseDetected : RefreshOutcome
}
