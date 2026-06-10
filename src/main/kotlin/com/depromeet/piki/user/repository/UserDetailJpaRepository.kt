package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.UserDetail
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserDetailJpaRepository : JpaRepository<UserDetail, UUID> {
    fun findByProviderAndSocialId(
        provider: String,
        socialId: String,
    ): UserDetail?

    // 탈퇴 cascade — user_id(PK) 로 행을 하드삭제한다. 파생 delete 라 매칭 0건이어도 no-op(멱등).
    // deleteById 는 행이 없으면 EmptyResultDataAccessException 을 던져 double-request·user_detail 부재 시
    // cascade 가 깨지므로, 멱등한 파생 delete 로 둔다.
    fun deleteByUserId(userId: UUID)
}
