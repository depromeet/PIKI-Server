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
    identityType: IdentityType,
) : BaseEntity<UUID>() {
    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    val id: UUID = id

    override fun getIdOrNull(): UUID = id

    var nickname: String = nickname
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_type", nullable = false, length = 10)
    var identityType: IdentityType = identityType
        protected set

    fun promoteToMember() {
        check(identityType == IdentityType.GUEST) { "이미 MEMBER 입니다." }
        identityType = IdentityType.MEMBER
    }

    fun updateNickname(newNickname: String) {
        require(newNickname.isNotBlank()) { "닉네임은 공백일 수 없습니다." }
        require(newNickname.length <= 16) { "닉네임은 16자 이하여야 합니다." }
        nickname = newNickname
    }

    fun softDelete() {
        deletedAt = LocalDateTime.now()
    }
}
