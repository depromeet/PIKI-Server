package com.depromeet.team3.common.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.hibernate.Hibernate
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.Objects

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity<ID : Any> {
    // 영속화 후에만 채워지는 자동 생성 PK 등을 표현하기 위한 nullable getter.
    // 외부에서는 getId() 만 사용한다. 미영속 인스턴스는 엔티티가 아니므로 동등성 검사·해시
    // 시도 자체를 거부해 데이터 정합성이 사일런트로 깨지는 것을 막는다.
    abstract fun getIdOrNull(): ID?

    fun getId(): ID = getIdOrNull() ?: error(MISSING_ID)

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseEntity<*>) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        val thisId = getIdOrNull() ?: error(MISSING_ID)
        val otherId = other.getIdOrNull() ?: error(MISSING_ID)
        return thisId == otherId
    }

    override fun hashCode(): Int = Objects.hash(getIdOrNull() ?: error(MISSING_ID))

    companion object {
        private const val MISSING_ID = "엔티티가 영속화되기 전에는 id 가 없다. 미영속 인스턴스에 동등성 검사·해시·외부 노출을 사용하지 않는다."
    }
}
