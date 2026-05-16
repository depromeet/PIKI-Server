package com.depromeet.team3.user.repository

import com.depromeet.team3.user.domain.UserDetail
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class UserDetailRepositoryImpl(
    private val userDetailJpaRepository: UserDetailJpaRepository,
) : UserDetailRepository {
    override fun save(userDetail: UserDetail): UserDetail = userDetailJpaRepository.save(userDetail)

    override fun findByUserId(userId: UUID): UserDetail? = userDetailJpaRepository.findByIdOrNull(userId)

    override fun findByProviderAndSocialId(
        provider: String,
        socialId: String,
    ): UserDetail? = userDetailJpaRepository.findByProviderAndSocialId(provider, socialId)
}
