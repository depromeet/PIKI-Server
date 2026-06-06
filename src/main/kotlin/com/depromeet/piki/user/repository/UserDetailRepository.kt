package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.UserDetail
import java.util.UUID

interface UserDetailRepository {
    fun save(userDetail: UserDetail): UserDetail

    fun findByUserId(userId: UUID): UserDetail?

    fun findByProviderAndSocialId(
        provider: String,
        socialId: String,
    ): UserDetail?

    // 탈퇴 cascade — socialId 즉시 파기(PIPA "지체없이 파기"). user_id(PK) 기준 멱등 하드삭제.
    // UNIQUE(provider, social_id) 가 풀려 같은 소셜로 재가입 가능해진다.
    fun deleteByUserId(userId: UUID)
}
