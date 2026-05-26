package com.depromeet.piki.item.service

import com.depromeet.piki.item.repository.ItemRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

// 인스턴스 재시작·크래시로 워커가 죽으면 PROCESSING 으로 영원히 남는 item 이 생긴다.
// 정상 파싱 시간(외부 LLM read-timeout 60s)을 충분히 넘긴 PROCESSING 을 주기적으로 FAILED 로 쓸어낸다.
// 단일 인스턴스 기준의 @Scheduled 다. 멀티 인스턴스로 확장되면 중복 실행 방지(ShedLock 등)가 필요하다.
@Component
class StaleProcessingItemSweeper(
    private val itemRepository: ItemRepository,
    private val itemParsingService: ItemParsingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS)
    fun sweep() {
        val cutoff = LocalDateTime.now().minusMinutes(STALE_TIMEOUT_MINUTES)
        val staleIds = itemRepository.findStaleProcessingIds(cutoff)
        if (staleIds.isEmpty()) return
        // sweep 이 조회한 직후 워커가 막 READY/FAILED 로 전이했을 수 있다(레이스).
        // 그 경우 markFailed 의 전이 불변식(check)이 깨지므로 잡아 무시한다 — 이미 정상 종료된 것이다.
        // 레이스(이미 전이됨)와 실제 전이 실패를 분리 집계해 요약 로그에서 오판을 막는다.
        var alreadyTransitioned = 0
        var transitionFailed = 0
        for (itemId in staleIds) {
            runCatching { itemParsingService.markFailed(itemId) }
                .onFailure { e ->
                    when (e) {
                        is IllegalStateException -> {
                            alreadyTransitioned++
                            log.info("item {} 는 이미 전이됨, stale 정리 생략: {}", itemId, e.message)
                        }
                        else -> {
                            transitionFailed++
                            log.error("item {} stale FAILED 전이 실패", itemId, e)
                        }
                    }
                }
        }
        val succeeded = staleIds.size - alreadyTransitioned - transitionFailed
        log.warn(
            "stale PROCESSING {}건 — FAILED 정리 {}건, 이미 전이됨(레이스) {}건, 전이 실패 {}건",
            staleIds.size,
            succeeded,
            alreadyTransitioned,
            transitionFailed,
        )
    }

    companion object {
        // 정상 파싱이 끝났어야 할 시간을 넉넉히 넘긴 기준. 이보다 오래 PROCESSING 이면 방치된 것으로 본다.
        private const val STALE_TIMEOUT_MINUTES = 5L
        private const val SWEEP_INTERVAL_MS = 60_000L
    }
}
