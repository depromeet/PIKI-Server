package com.depromeet.piki.user.domain

import com.depromeet.piki.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "user_details")
class UserDetail(
    userId: UUID,
    email: String,
    provider: String,
    socialId: String,
) : BaseEntity<UUID>() {
    @Id
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private val userId: UUID = userId

    override fun getIdOrNull(): UUID = userId

    @Column(nullable = false)
    var email: String = email
        protected set

    @Column(nullable = false, length = 20)
    var provider: String = provider
        protected set

    @Column(name = "social_id", nullable = false)
    var socialId: String = socialId
        protected set

    fun updateEmail(newEmail: String) {
        email = newEmail
    }
}
