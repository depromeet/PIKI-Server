package com.depromeet.team3.user.service

import com.depromeet.team3.user.domain.IdentityType
import com.depromeet.team3.user.domain.User
import com.depromeet.team3.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    companion object {
        private const val MAX_NICKNAME_ATTEMPTS = 5
    }

    @Transactional
    fun createGuest(): User {
        val id = UUID.randomUUID()
        val nickname = generateUniqueGuestNickname()
        return userRepository.save(User(id = id, nickname = nickname, identityType = IdentityType.GUEST))
    }

    @Transactional
    fun createMember(nickname: String): User {
        if (userRepository.existsByNickname(nickname)) throw UserException.duplicateNickname(nickname)
        val id = UUID.randomUUID()
        return userRepository.save(User(id = id, nickname = nickname, identityType = IdentityType.MEMBER))
    }

    private fun generateUniqueGuestNickname(): String {
        repeat(MAX_NICKNAME_ATTEMPTS) {
            val candidate = "게스트_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            if (!userRepository.existsByNickname(candidate)) return candidate
        }
        throw UserException.nicknameGenerationFailed()
    }

    @Transactional(readOnly = true)
    fun findById(userId: UUID): User = userRepository.findById(userId) ?: throw UserException.notFound(userId)

    @Transactional(readOnly = true)
    fun existsByNickname(nickname: String): Boolean = userRepository.existsByNickname(nickname)

    @Transactional
    fun updateNickname(
        userId: UUID,
        newNickname: String,
    ): User {
        val user = findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser(userId) }
        user.updateNickname(newNickname)
        return userRepository.save(user)
    }

    @Transactional
    fun promoteToMember(userId: UUID): User {
        val user = findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser(userId) }
        if (user.identityType != IdentityType.GUEST) throw UserException.alreadyMember(userId)
        user.promoteToMember()
        return userRepository.save(user)
    }

    @Transactional
    fun softDelete(userId: UUID) {
        val user = findById(userId)
        user.softDelete()
        userRepository.save(user)
    }
}
