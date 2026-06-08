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

    // 소셜에서 수집한 email (마케팅·알림·복구용). 미동의·애플 2회차 미제공·카카오 미인증/휴면·기존 가입자(backfill 전)는 null.
    @Column(name = "email", length = 255)
    var email: String? = email
        protected set

    // 매 로그인 upsert 용. null 이면 기존 값을 유지한다 — provider 가 email 을 안 줄 때(애플 2회차 등)
    // 이미 저장된 email 을 null 로 덮어쓰지 않기 위함이다.
    // 한계(인지된 제품 결정): "이번 로그인에 email 이 null" 은 "provider 가 안 줌"과 "사용자가 동의 철회함"을
    // 구분하지 못한다(둘 다 응답에서 email 이 빠짐). 따라서 동의 철회 후에도 기존 email 이 그대로 남는다.
    // 철회 시 비우려면 별도 신호(예: 카카오 email_needs_agreement)를 모델링해 명시적으로 clear 해야 한다.
    fun updateEmail(newEmail: String?) {
        newEmail ?: return
        email = newEmail
    }
}
