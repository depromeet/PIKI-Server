package com.depromeet.team3.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.UUID

// user_detail 테이블에는 deleted_at 컬럼이 없어 BaseEntity 상속 대신 직접 auditing 어노테이션을 사용한다.
@Entity
@Table(name = "user_detail")
@EntityListeners(AuditingEntityListener::class)
class UserDetail(
    @Id
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    val userId: UUID,
    profileImage: String = "default",
    email: String,
    provider: String,
    socialId: String,
) {
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

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    fun updateProfileImage(newProfileImage: String) {
        profileImage = newProfileImage
    }

    fun updateEmail(newEmail: String) {
        email = newEmail
    }
}
