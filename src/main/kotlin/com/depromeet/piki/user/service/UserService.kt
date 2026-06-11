package com.depromeet.piki.user.service

import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.domain.UserException
import com.depromeet.piki.user.repository.UserDetailRepository
import com.depromeet.piki.user.repository.UserRepository
import com.depromeet.piki.user.service.dto.UserProfile
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val userDetailRepository: UserDetailRepository,
    private val defaultProfileImages: DefaultProfileImages,
) {
    companion object {
        // 형용사 32 × 동물 32 = 1024 조합. 모든 조합이 닉네임 10자 제한 이하가 되도록
        // 형용사는 5자 이하, 동물은 3자 이하로 유지한다(최대 5+1+3=9자).
        private val NICKNAME_PREFIXES =
            listOf(
                "날뛰는",
                "졸린",
                "배고픈",
                "춤추는",
                "달리는",
                "헤엄치는",
                "잠자는",
                "신나는",
                "빠른",
                "느린",
                "배부른",
                "웃는",
                "뛰어다니는",
                "구르는",
                "노래하는",
                "울부짖는",
                "용감한",
                "귀여운",
                "똑똑한",
                "엉뚱한",
                "수줍은",
                "행복한",
                "게으른",
                "화난",
                "신비한",
                "우아한",
                "까칠한",
                "명랑한",
                "차분한",
                "도도한",
                "엉큼한",
                "발랄한",
            )
        private val NICKNAME_ANIMALS =
            listOf(
                "하마",
                "토끼",
                "고양이",
                "강아지",
                "코끼리",
                "기린",
                "펭귄",
                "판다",
                "사자",
                "호랑이",
                "여우",
                "늑대",
                "독수리",
                "돌고래",
                "부엉이",
                "다람쥐",
                "너구리",
                "수달",
                "거북이",
                "두더지",
                "햄스터",
                "코알라",
                "캥거루",
                "미어캣",
                "알파카",
                "치타",
                "표범",
                "오리",
                "거위",
                "두루미",
                "청설모",
                "살쾡이",
            )
        private val NICKNAME_POOL: List<String> by lazy {
            NICKNAME_PREFIXES.flatMap { prefix -> NICKNAME_ANIMALS.map { animal -> "$prefix $animal" } }
        }
    }

    @Transactional
    fun createGuest(): User {
        val id = UUID.randomUUID()
        val nickname = generateUniqueGuestNickname()
        val profileImage = defaultProfileImages.random()
        return userRepository.save(
            User(id = id, nickname = nickname, profileImage = profileImage, identityType = IdentityType.GUEST),
        )
    }

    @Transactional
    fun createGuestWithNickname(nickname: String): User {
        val id = UUID.randomUUID()
        val profileImage = defaultProfileImages.random()
        return try {
            userRepository.save(
                User(id = id, nickname = nickname, profileImage = profileImage, identityType = IdentityType.GUEST),
            )
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            throw UserException.duplicateNickname()
        }
    }

    @Transactional
    fun createMember(nickname: String): User {
        if (userRepository.existsByNickname(nickname)) throw UserException.duplicateNickname()
        val id = UUID.randomUUID()
        val profileImage = defaultProfileImages.random()
        return try {
            userRepository.save(
                User(id = id, nickname = nickname, profileImage = profileImage, identityType = IdentityType.MEMBER),
            )
        } catch (e: DataIntegrityViolationException) {
            throw UserException.duplicateNickname()
        }
    }

    // 소셜 신규 가입용 MEMBER 생성. 닉네임은 게스트와 동일하게 자동 생성(fill)하고 사용자가 나중에 수정한다.
    // 프로필 이미지는 provider 가 준 게 있으면 쓰고, 없으면(동의 거부 등) 기본 아바타 4종 중 랜덤.
    @Transactional
    fun createSocialUser(profileImage: String?): User {
        val id = UUID.randomUUID()
        val nickname = generateUniqueGuestNickname()
        return try {
            userRepository.save(
                User(
                    id = id,
                    nickname = nickname,
                    profileImage = profileImage ?: defaultProfileImages.random(),
                    identityType = IdentityType.MEMBER,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            throw UserException.duplicateNickname()
        }
    }

    @Transactional(readOnly = true)
    fun findById(userId: UUID): User = userRepository.findById(userId) ?: throw UserException.notFound()

    // 마이페이지(GET /me) 조회 — User(정체성)와 UserDetail 의 email 을 한 트랜잭션에서 모은다.
    // email 은 미수집(게스트)·미동의·backfill 전이면 UserDetail 이 없거나 null 이라 그대로 null 로 내려간다.
    @Transactional(readOnly = true)
    fun getMyProfile(userId: UUID): UserProfile {
        val user = findById(userId)
        val email = userDetailRepository.findByUserId(userId)?.email
        return UserProfile(user, email)
    }

    // 본인 닉네임은 중복으로 잡지 않는다 (#230). 게스트가 자기 닉네임 그대로 유지하거나, 본인이
    // 자기 닉네임으로 다시 변경하는 흐름이 자연스럽게 통과되도록 본인 제외 후 검사.
    @Transactional(readOnly = true)
    fun isNicknameAvailable(
        nickname: String,
        userId: UUID?,
    ): Boolean =
        userId
            ?.let { !userRepository.existsByNicknameAndIdNot(nickname, it) }
            ?: !userRepository.existsByNickname(nickname)

    // 내 정보(닉네임·프로필 이미지) 부분 수정 영속화 — 들어온 필드만 갱신한다. 둘을 한 트랜잭션에 묶어
    // "닉네임은 됐는데 이미지는 실패" 같은 부분 성공을 막는다. 이미지 S3 업로드(외부 호출)는 ProfileUpdateService 가
    // 트랜잭션 밖에서 끝낸 뒤 그 결과 URL 만 여기로 위임한다 (## 트랜잭션 경계 — 외부 호출은 트랜잭션 밖).
    @Transactional
    fun updateProfile(
        userId: UUID,
        nickname: String?,
        profileImageUrl: String?,
    ): User {
        val user = findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser() }
        nickname?.let {
            if (userRepository.existsByNicknameAndIdNot(it, userId)) throw UserException.duplicateNickname()
            user.updateNickname(it)
        }
        profileImageUrl?.let { user.updateProfileImage(it) }
        // existsByNicknameAndIdNot 체크와 save 사이에 다른 트랜잭션이 같은 nickname 으로 update / insert 하면
        // DB unique constraint (uq_users_nickname) 위반이 떠 race 케이스에서 500 이 새어나갈 수 있다.
        // createMember 와 같은 패턴으로 catch → 409 로 매핑.
        return try {
            userRepository.save(user)
        } catch (e: DataIntegrityViolationException) {
            throw UserException.duplicateNickname()
        }
    }

    // 소셜 가입/연동 시 provider 가 준 프로필 이미지 URL 영속화만 담당하는 짧은 트랜잭션 (SocialAccountWriter 전용).
    // 내 정보 수정(PATCH /me) 경로는 ProfileUpdateService → updateProfile 로 가며, 이 메서드는 닉네임과 무관한 auth 경로다.
    @Transactional
    fun updateProfileImageUrl(
        userId: UUID,
        profileImageUrl: String,
    ): User {
        val user = findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser() }
        user.updateProfileImage(profileImageUrl)
        return userRepository.save(user)
    }

    @Transactional
    fun promoteToMember(userId: UUID): User {
        val user = findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser() }
        user.promoteToMember()
        return userRepository.save(user)
    }

    @Transactional
    fun softDelete(userId: UUID) {
        val user = findById(userId)
        user.softDelete()
        userRepository.save(user)
    }

    private fun generateUniqueGuestNickname(): String {
        val taken = userRepository.findNicknamesIn(NICKNAME_POOL).toSet()
        return (NICKNAME_POOL - taken).randomOrNull() ?: throw UserException.nicknameGenerationFailed()
    }
}
