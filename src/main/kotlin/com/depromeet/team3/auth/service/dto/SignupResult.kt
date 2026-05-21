package com.depromeet.team3.auth.service.dto

import com.depromeet.team3.user.domain.User

// 회원/게스트 생성 결과를 controller 에 넘기는 묶음. 토큰만 반환하면 controller 가 user 정보를
// 다시 조회해야 하므로 생성한 user 와 발급한 토큰을 함께 들고 나간다.
data class SignupResult(
    val tokenPair: TokenPair,
    val user: User,
)
