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
}
