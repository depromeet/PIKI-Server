package com.depromeet.piki.user.service.dto

import com.depromeet.piki.user.domain.User

// getMyProfile 의 조회 결과 묶음. User(정체성)와 UserDetail 에 흩어진 email 을 한 트랜잭션에서 모아
// 컨트롤러로 넘긴다. email 은 미수집·미동의·backfill 전이면 null.
data class UserProfile(
    val user: User,
    val email: String?,
)
