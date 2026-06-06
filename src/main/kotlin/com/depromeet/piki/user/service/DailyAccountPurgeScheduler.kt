package com.depromeet.piki.user.service

import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

// 30일(설정값) 파기 스케줄러. grace 기간을 넘긴 MEMBER tombstone 들의 soft-delete 된 콘텐츠(wishes, notifications)를
// 영구 하드삭제한다. tombstone users 행 자체는 영구 보존한다 — 익명·PII 가 없고, 공유 토너먼트 참조
// (tournament_items.userId 등)를 유지해야 하기 때문(삭제하면 참조가 dangling 된다).
//
// 매일 새벽 1회(cron, KST) 실행. chunk 단위로 끊어 한 번에 메모리·트랜잭션을 키우지 않으며, 유저별 파기는
// 멱등이라(이미 0건이면 no-op) 재실행에 안전하다.
//
// 단일 인스턴스 기준의 @Scheduled 다 — 멀티 인스턴스로 확장되면 중복 실행 방지(ShedLock 등)가 필요하다
// (StaleProcessingItemSweeper·SseHeartbeatScheduler 와 동일 관례).
@Component
class DailyAccountPurgeScheduler(
    private val userRepository: UserRepository,
    private val withdrawalPersistenceService: WithdrawalPersistenceService,
    private val properties: AccountWithdrawalProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // KST 기준 매일 새벽 4시. 트래픽이 가장 낮은 시간대에 배치성 파기를 돌린다.
    @Scheduled(cron = PURGE_CRON, zone = PURGE_ZONE)
    fun purge() {
        val cutoff = graceCutoff(LocalDateTime.now(), properties.graceDays)
        var purged = 0
        // chunk 를 돌며 더 이상 대상이 없을 때까지 반복. purgeContent 가 같은 트랜잭션에서 content_purged_at 을 찍어
        // 다음 스캔 결과집합에서 이 유저를 영구 제외하므로, 정상 흐름에서는 매 chunk 가 새 행만 본다(offset/cursor 불필요).
        // 이로써 이미 파기한 tombstone 을 매일 재스캔하는 O(N^2) 가 사라지고, 빈 배치가 나오면 종료한다.
        // 단, 특정 유저의 파기가 영속적으로 실패하면(마커 미설정) 같은 chunk 가 반복 반환되어 무한 루프가 된다.
        // seen 으로 "이번 실행에서 진전이 있었나"만 추적해, 한 chunk 가 전부 이미 본 id 면(새 진전 0) 종료한다.
        // (정상 진전 시엔 마커가 찍혀 다음 chunk 에 새 id 만 와 seen 이 계속 커진다.)
        val seen = mutableSetOf<UUID>()
        while (true) {
            val ids = userRepository.findIdsToPurge(cutoff, IdentityType.MEMBER, CHUNK_SIZE)
            if (ids.isEmpty()) break
            val progressed = ids.any { it !in seen }
            if (!progressed) {
                log.error("탈퇴 콘텐츠 파기 진전 없음 — 동일 chunk 반복으로 종료(파기 영속 실패 추정) 남은={}건", ids.size)
                break
            }
            ids.forEach { userId ->
                seen.add(userId)
                runCatching { withdrawalPersistenceService.purgeContent(userId) }
                    .onFailure { e -> log.error("탈퇴 콘텐츠 파기 실패 userId={}", userId, e) }
                purged++
            }
        }
        log.info("탈퇴 콘텐츠 파기 배치 완료 graceDays={} cutoff={} 대상={}건", properties.graceDays, cutoff, purged)
    }

    companion object {
        // grace 기간을 넘긴 기준 시각. now 에서 graceDays 만큼 과거 — 이 시각 이전에 탈퇴(deletedAt)한 tombstone 이 파기 대상.
        // 순수 함수라 단위 테스트로 경계(29일/31일)를 검증한다.
        fun graceCutoff(
            now: LocalDateTime,
            graceDays: Long,
        ): LocalDateTime = now.minusDays(graceDays)

        // 매일 04:00:00 (KST). cron 도 상수로 둬 매직 스트링을 박지 않는다.
        private const val PURGE_CRON = "0 0 4 * * *"
        private const val PURGE_ZONE = "Asia/Seoul"

        // 한 번에 스캔·파기할 유저 수. 메모리·트랜잭션 크기를 제한한다.
        private const val CHUNK_SIZE = 100
    }
}
