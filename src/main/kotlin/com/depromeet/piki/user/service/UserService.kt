package com.depromeet.piki.user.service

import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.domain.UserException
import com.depromeet.piki.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    companion object {
        private const val MAX_NICKNAME_ATTEMPTS = 10
        private const val DICEBEAR_BASE_URL = "https://api.dicebear.com/9.x/bottts/svg?seed="

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
            )
    }

    @Transactional
    fun createGuest(): User {
        val id = UUID.randomUUID()
        val nickname = generateUniqueGuestNickname()
        val profileImage = dicebearUrl(id)
        return userRepository.save(
            User(id = id, nickname = nickname, profileImage = profileImage, identityType = IdentityType.GUEST),
        )
    }

    @Transactional
    fun createMember(nickname: String): User {
        if (userRepository.existsByNickname(nickname)) throw UserException.duplicateNickname()
        val id = UUID.randomUUID()
        val profileImage = dicebearUrl(id)
        return try {
            userRepository.save(
                User(id = id, nickname = nickname, profileImage = profileImage, identityType = IdentityType.MEMBER),
            )
        } catch (e: DataIntegrityViolationException) {
            throw UserException.duplicateNickname()
        }
    }

    // 소셜 신규 가입용 MEMBER 생성. 닉네임은 게스트와 동일하게 자동 생성(fill)하고 사용자가 나중에 수정한다.
    // 프로필 이미지는 provider 가 준 게 있으면 쓰고, 없으면(동의 거부 등) dicebear 기본 아바타.
    @Transactional
    fun createSocialUser(profileImage: String?): User {
        val id = UUID.randomUUID()
        val nickname = generateUniqueGuestNickname()
        return try {
            userRepository.save(
                User(
                    id = id,
                    nickname = nickname,
                    profileImage = profileImage ?: dicebearUrl(id),
                    identityType = IdentityType.MEMBER,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            throw UserException.duplicateNickname()
        }
    }

    @Transactional(readOnly = true)
    fun findById(userId: UUID): User = userRepository.findById(userId) ?: throw UserException.notFound(userId)

    // 본인 닉네임은 중복으로 잡지 않는다 (#230). 게스트가 자기 닉네임 그대로 유지하거나, 본인이
    // 자기 닉네임으로 다시 변경하는 흐름이 자연스럽게 통과되도록 본인 제외 후 검사.
    @Transactional(readOnly = true)
    fun isNicknameAvailable(
        nickname: String,
        userId: UUID,
    ): Boolean = !userRepository.existsByNicknameAndIdNot(nickname, userId)

    @Transactional
    fun updateNickname(
        userId: UUID,
        newNickname: String,
    ): User {
        val user = findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser(userId) }
        if (userRepository.existsByNicknameAndIdNot(newNickname, userId)) {
            throw UserException.duplicateNickname()
        }
        user.updateNickname(newNickname)
        // existsByNicknameAndIdNot 체크와 save 사이에 다른 트랜잭션이 같은 nickname 으로
        // update / insert 하면 DB unique constraint (uq_users_nickname) 위반이 떠 race 케이스에서
        // 500 이 새어나갈 수 있다. createMember 와 같은 패턴으로 catch → 409 로 매핑.
        return try {
            userRepository.save(user)
        } catch (e: DataIntegrityViolationException) {
            throw UserException.duplicateNickname()
        }
    }

    @Transactional
    fun promoteToMember(userId: UUID): User {
        val user = findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser(userId) }
        user.promoteToMember()
        return userRepository.save(user)
    }

    @Transactional
    fun softDelete(userId: UUID) {
        val user = findById(userId)
        user.softDelete()
        userRepository.save(user)
    }

    private fun dicebearUrl(userId: UUID): String = "$DICEBEAR_BASE_URL$userId"

    private fun generateUniqueGuestNickname(): String {
        repeat(MAX_NICKNAME_ATTEMPTS) {
            val candidate = "${NICKNAME_PREFIXES.random()} ${NICKNAME_ANIMALS.random()}"
            if (!userRepository.existsByNickname(candidate)) return candidate
        }
        throw UserException.nicknameGenerationFailed()
    }
}
