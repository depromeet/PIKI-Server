package com.depromeet.team3.wishlist.domain

import com.depromeet.team3.common.domain.BaseEntity
import com.depromeet.team3.product.domain.Product
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "wishes")
class Wish(
    @Column(name = "guest_id", nullable = false, columnDefinition = "BINARY(16)")
    val guestId: UUID,
    @Embedded
    val product: Product,
) : BaseEntity<Long>() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private var id: Long? = null

    override fun getIdOrNull(): Long? = id
}
