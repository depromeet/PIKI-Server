package com.depromeet.team3.user.repository

import com.depromeet.team3.user.domain.UserDetail
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserDetailJpaRepository : JpaRepository<UserDetail, UUID> {
    fun findByProviderAndSocialId(
        provider: String,
        socialId: String,
    ): UserDetail?
}
