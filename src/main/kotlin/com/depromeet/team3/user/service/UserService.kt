package com.depromeet.team3.user.service

import com.depromeet.team3.user.domain.IdentityType
import com.depromeet.team3.user.domain.User
import com.depromeet.team3.user.domain.UserException
import com.depromeet.team3.user.repository.UserRepository
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
        if (userRepository.existsByNickname(nickname)) throw UserException.duplicateNickname(nickname)
        val id = UUID.randomUUID()
        val profileImage = dicebearUrl(id)
        return try {
            userRepository.save(
                User(id = id, nickname = nickname, profileImage = profileImage, identityType = IdentityType.MEMBER),
            )
        } catch (e: DataIntegrityViolationException) {
            throw UserException.duplicateNickname(nickname)
        }
    }

    @Transactional(readOnly = true)
    fun findById(userId: UUID): User = userRepository.findById(userId) ?: throw UserException.notFound(userId)

    @Transactional
    fun updateNickname(
        userId: UUID,
        newNickname: String,
    ): User {
        val user = findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser(userId) }
        if (user.nickname != newNickname && userRepository.existsByNickname(newNickname)) {
            throw UserException.duplicateNickname(newNickname)
        }
        user.updateNickname(newNickname)
        return userRepository.save(user)
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
