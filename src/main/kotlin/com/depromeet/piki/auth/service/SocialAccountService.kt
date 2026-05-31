package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.domain.UserDetail
import com.depromeet.piki.user.repository.UserDetailRepository
import com.depromeet.piki.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 소셜 아이디 ↔ user 영속화. 외부 OAuth 호출과 분리된 짧은 트랜잭션 (OAuthLoginService 가 호출).
@Service
class SocialAccountService(
    private val userService: UserService,
    private val userDetailRepository: UserDetailRepository,
) {
    @Transactional
    fun resolveUser(
        userInfo: OAuthUserInfo,
        currentUserId: UUID?,
    ): User {
        // 1. 이미 가입된 소셜 → 그 user 로 로그인. (재방문 / 게스트의 소셜이 이미 타계정 → 그 계정 로그인 = 게스트 포기)
        userDetailRepository
            .findByProviderAndSocialId(userInfo.provider.name, userInfo.socialId)
            ?.let { return userService.findById(it.getIdOrNull()) }

        // 2. 신규 소셜 + 현재 게스트면 → 게스트 계정에 연결 + 승격 (위시·토너먼트 데이터 이어줌)
        currentUserId?.let { guestId ->
            linkToGuestIfEligible(guestId, userInfo)?.let { return it }
        }

        // 3. 순수 신규 가입 → MEMBER 생성 + 소셜 연결
        val user = userService.createSocialUser(userInfo.profileImage)
        linkSocial(user.id, userInfo)
        return user
    }

    private fun linkToGuestIfEligible(
        guestId: UUID,
        userInfo: OAuthUserInfo,
    ): User? {
        val guest = userService.findById(guestId)
        guest.deletedAt?.let { return null } // 탈퇴 게스트 → 연결 대상 아님
        if (guest.identityType != IdentityType.GUEST) return null // 이미 MEMBER → 게스트-연결 케이스 아님
        linkSocial(guestId, userInfo)
        return userService.promoteToMember(guestId)
    }

    private fun linkSocial(
        userId: UUID,
        userInfo: OAuthUserInfo,
    ) {
        userDetailRepository.save(
            UserDetail(userId = userId, provider = userInfo.provider.name, socialId = userInfo.socialId),
        )
    }
}
