package com.depromeet.team3.wishlist.service

import com.depromeet.team3.product.domain.ProductSnapshot
import com.depromeet.team3.wishlist.domain.Wish
import com.depromeet.team3.wishlist.repository.WishRepository
import com.depromeet.team3.wishlist.service.dto.WishRegisterResult
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// WishlistService 의 register 가 외부 LLM 호출을 트랜잭션 바깥에 두도록
// 영속화만 별도 빈으로 분리. 같은 빈에서 호출하면 Spring AOP proxy 를
// 거치지 않아 @Transactional 가 무력화되기 때문이다.
@Service
class WishPersistenceService(
    private val wishRepository: WishRepository,
) {
    @Transactional
    fun persist(
        userId: UUID,
        product: ProductSnapshot,
    ): WishRegisterResult =
        try {
            val wish = wishRepository.save(Wish(userId = userId, product = product))
            WishRegisterResult(wish = wish)
        } catch (e: DataIntegrityViolationException) {
            // dedup 체크를 통과한 뒤 uk_wishes_user_source 제약에 걸린 race 케이스만
            // 409 로 매핑한다. 다른 unique / 길이 / NULL 제약 위반까지 묻혀버리면
            // 진짜 데이터 오류가 "이미 존재함" 으로 사일런트 fail 한다.
            // Hibernate 가 constraintName 을 "wishes.uk_wishes_user_source" 처럼 테이블 prefix
            // 와 함께 주는 경우가 있어 endsWith 도 함께 본다.
            val constraint =
                (e.cause as? ConstraintViolationException)?.constraintName?.lowercase()
                    ?: throw e
            val matched =
                constraint == UK_WISHES_USER_SOURCE ||
                    constraint.endsWith(".$UK_WISHES_USER_SOURCE")
            if (!matched) throw e
            throw WishException.alreadyExists(userId = userId, link = product.link)
        }

    companion object {
        private const val UK_WISHES_USER_SOURCE = "uk_wishes_user_source"
    }
}
