package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.UserDetail
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

    // user_id 가 user_details 의 PK. 파생 deleteByUserId 라 행이 없어도 no-op(멱등) — deleteById 와 달리
    // EmptyResultDataAccessException 을 던지지 않아 double-request·user_detail 부재에 안전하다.
    override fun deleteByUserId(userId: UUID) = userDetailJpaRepository.deleteByUserId(userId)
}
