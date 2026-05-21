package com.depromeet.team3.user.domain

import com.depromeet.team3.common.domain.BaseEntity
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
    profileImage: String,
    identityType: IdentityType,
) : BaseEntity<UUID>() {
    init {
        validateNickname(nickname)
    }

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    val id: UUID = id

    override fun getIdOrNull(): UUID = id

    var nickname: String = nickname
        protected set

    @Column(name = "profile_image", nullable = false, length = 2048)
    var profileImage: String = profileImage
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_type", nullable = false, length = 10)
    var identityType: IdentityType = identityType
        protected set

    fun promoteToMember() {
        if (identityType != IdentityType.GUEST) throw UserException.alreadyMember(id)
        identityType = IdentityType.MEMBER
    }

    fun updateProfileImage(newProfileImage: String) {
        profileImage = newProfileImage
    }

    fun updateNickname(newNickname: String) {
        validateNickname(newNickname)
        nickname = newNickname
    }

    fun softDelete() {
        deletedAt = deletedAt ?: LocalDateTime.now()
    }

    companion object {
        const val NICKNAME_MAX_LENGTH = 10

        private fun validateNickname(nickname: String) {
            if (nickname.isBlank()) throw UserException.invalidNickname("닉네임은 공백일 수 없습니다.")
            if (nickname.length > NICKNAME_MAX_LENGTH) {
                throw UserException.invalidNickname("닉네임은 $NICKNAME_MAX_LENGTH 자 이하여야 합니다.")
            }
        }
    }
}
