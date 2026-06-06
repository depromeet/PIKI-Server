package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.repository.UserDetailRepository
import com.depromeet.piki.user.service.UserService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.util.UUID

// 소셜 아이디 ↔ user 해소. 영속화 mutation 은 SocialAccountWriter(REQUIRED 트랜잭션)에 위임하고,
// 이 클래스는 비트랜잭션 오케스트레이션만 한다 — 동시 첫 로그인 충돌을 catch 후 재조회·합류로 마무리하려면
// resolveUser 자신이 트랜잭션을 들고 있으면 안 되기 때문이다(상위 tx 가 rollback-only 로 오염되면 재조회가 깨진다).
@Service
class SocialAccountService(
    private val userService: UserService,
    private val userDetailRepository: UserDetailRepository,
    private val socialAccountWriter: SocialAccountWriter,
) {
    fun resolveUser(
        userInfo: OAuthUserInfo,
        currentUserId: UUID?,
    ): User {
        // 1. 이미 가입된 소셜 → 그 user 로 로그인. (재방문 / 게스트의 소셜이 이미 타계정 → 그 계정 로그인 = 게스트 포기)
        findExisting(userInfo)?.let { return it }

        // 2. 신규 소셜 + 현재 게스트면 → 게스트 계정에 연결 + 승격 (위시·토너먼트 데이터 이어줌)
        currentUserId?.let { guestId ->
            try {
                socialAccountWriter.linkGuestAndPromote(guestId, userInfo)?.let { return it }
            } catch (e: DataIntegrityViolationException) {
                // 동시 충돌: 다른 요청이 이 소셜을 먼저 선점 → 그 계정으로 합류 (게스트 포기)
                return findExisting(userInfo) ?: throw e
            }
        }

        // 3. 순수 신규 가입 → MEMBER 생성 + 소셜 연결
        return try {
            socialAccountWriter.createSocialUserAndLink(userInfo)
        } catch (e: DataIntegrityViolationException) {
            // 동시 충돌: 다른 요청이 먼저 같은 소셜로 가입 → 그 user 로 합류 (내가 만든 user 는 REQUIRED tx 롤백으로 폐기)
            findExisting(userInfo) ?: throw e
        }
    }

    // 탈퇴(tombstone) 유저는 없는 것으로 취급해 신규 가입 경로를 타게 한다. 탈퇴 시 user_details 는 하드삭제되므로
    // 보통 여기서 user_detail 자체가 안 잡히지만, 파기 전 잔존이나 경합 상황을 방어해 deletedAt 까지 확인한다 —
    // tombstone 을 반환하면 탈퇴한 소셜계정으로 재로그인 시 죽은 계정을 되살리는 버그가 된다.
    private fun findExisting(userInfo: OAuthUserInfo): User? =
        userDetailRepository
            .findByProviderAndSocialId(userInfo.provider.name, userInfo.socialId)
            ?.let { userService.findById(it.getIdOrNull()) }
            ?.takeIf { it.isActive() }
}
