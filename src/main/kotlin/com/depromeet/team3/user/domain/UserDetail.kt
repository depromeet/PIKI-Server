package com.depromeet.team3.user.domain

import com.depromeet.team3.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "user_detail")
class UserDetail(
    userId: UUID,
    profileImage: String = "default",
    email: String,
    provider: String,
    socialId: String,
) : BaseEntity<UUID>() {
    @Id
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private val userId: UUID = userId

    override fun getIdOrNull(): UUID = userId

    @Column(name = "profile_image", nullable = false)
    var profileImage: String = profileImage
        protected set

    @Column(nullable = false)
    var email: String = email
        protected set

    @Column(nullable = false, length = 20)
    var provider: String = provider
        protected set

    @Column(name = "social_id", nullable = false)
    var socialId: String = socialId
        protected set

    fun updateProfileImage(newProfileImage: String) {
        profileImage = newProfileImage
    }

    fun updateEmail(newEmail: String) {
        email = newEmail
    }
}
