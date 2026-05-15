package com.depromeet.team3.wishlist.service

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.domain.ProductSnapshot
import com.depromeet.team3.product.service.ProductExtractor
import com.depromeet.team3.wishlist.repository.WishRepository
import com.depromeet.team3.wishlist.service.dto.WishRegisterResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WishlistService(
    private val productExtractor: ProductExtractor,
    private val wishRepository: WishRepository,
    private val wishPersistenceService: WishPersistenceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // register 전체를 @Transactional 로 묶으면 외부 fetch + Gemini 호출 (read-timeout 60s)
    // 동안 DB 커넥션이 잡혀 풀 고갈 → 다른 API 까지 latency 폭증으로 번진다.
    // 외부 호출은 트랜잭션 바깥에서 끝내고, 영속화는 별도 빈에 위임해 proxy 를 통해 호출.
    fun register(
        rawUrl: String,
        userId: UUID,
    ): WishRegisterResult {
        val link = ProductLink.parse(rawUrl)

        // dedup 검사를 추출 전에 먼저 — 중복이면 LLM 호출 비용 자체를 회피.
        if (wishRepository.existsByUserIdAndProductLink(userId, link)) {
            throw WishException.alreadyExists(userId = userId, link = link)
        }

        val product = extractWithLatencyLog(link)
        return wishPersistenceService.persist(userId, product)
    }

    private fun extractWithLatencyLog(link: ProductLink): ProductSnapshot {
        val started = System.nanoTime()
        val product = productExtractor.extract(link)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        log.info("extract latency: total={}ms url={}", elapsedMs, link.safeLogString())
        return product
    }
}
