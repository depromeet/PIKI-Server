package com.depromeet.team3.user.domain

import com.depromeet.team3.common.domain.BaseEntity
import com.depromeet.team3.user.service.UserException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "user")
class User(
    id: UUID,
    nickname: String,
    identityType: IdentityType,
) : BaseEntity<UUID>() {
    init {
        validateNickname(nickname)
    }

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private val id: UUID = id

    override fun getIdOrNull(): UUID = id

    var nickname: String = nickname
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_type", nullable = false, length = 10)
    var identityType: IdentityType = identityType
        protected set

    fun promoteToMember() {
        if (identityType != IdentityType.GUEST) throw UserException.alreadyMember()
        identityType = IdentityType.MEMBER
    }

    fun updateNickname(newNickname: String) {
        validateNickname(newNickname)
        nickname = newNickname
    }

    fun softDelete() {
        deletedAt = LocalDateTime.now()
    }

    private fun validateNickname(value: String) {
        if (value.isBlank()) throw UserException.nicknameBlank()
        if (value.length > 16) throw UserException.nicknameTooLong()
    }
}
