package com.depromeet.team3.user.repository

import com.depromeet.team3.user.domain.User
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: User): User = userJpaRepository.save(user)

    override fun findById(id: UUID): User? = userJpaRepository.findByIdOrNull(id)

    override fun findByIds(ids: Collection<UUID>): List<User> = userJpaRepository.findAllById(ids)

    override fun existsByNickname(nickname: String): Boolean = userJpaRepository.existsByNickname(nickname)
}
