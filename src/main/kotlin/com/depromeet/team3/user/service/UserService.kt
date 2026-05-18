package com.depromeet.team3.user.service

import com.depromeet.team3.user.domain.IdentityType
import com.depromeet.team3.user.domain.User
import com.depromeet.team3.user.exception.UserException
import com.depromeet.team3.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createGuest(): User {
        val id = UUID.randomUUID()
        val nickname = "게스트_${id.toString().replace("-", "").take(8)}"
        return userRepository.save(User(id = id, nickname = nickname, identityType = IdentityType.GUEST))
    }

    @Transactional
    fun createMember(nickname: String): User {
        val id = UUID.randomUUID()
        return userRepository.save(User(id = id, nickname = nickname, identityType = IdentityType.MEMBER))
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
        user.updateNickname(newNickname)
        return userRepository.save(user)
    }

    @Transactional
    fun promoteToMember(userId: UUID): User {
        val user = findById(userId)
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
