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
    provider: String,
    socialId: String,
    email: String? = null,
) : BaseEntity<UUID>() {
    @Id
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private val userId: UUID = userId

    override fun getIdOrNull(): UUID = userId

    @Column(nullable = false, length = 20)
    var provider: String = provider
        protected set

    @Column(name = "social_id", nullable = false)
    var socialId: String = socialId
        protected set

    // 소셜에서 수집한 email (마케팅·알림·복구용). 애플 Private Relay 거부·카카오 미수집·기존 가입자는 null.
    @Column(name = "email", length = 255)
    var email: String? = email
        protected set

    // 매 로그인 upsert 용. null 이면 기존 값을 유지한다 — provider 가 email 을 안 줄 때(애플 2회차 등)
    // 이미 저장된 email 을 null 로 덮어쓰지 않기 위함이다.
    fun updateEmail(newEmail: String?) {
        newEmail ?: return
        email = newEmail
    }
}
