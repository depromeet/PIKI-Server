package com.depromeet.piki.image.service

import com.depromeet.piki.common.config.AsyncConfig
import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.image.domain.PendingUpload
import com.depromeet.piki.image.domain.PendingUploadContext
import com.depromeet.piki.image.repository.PendingUploadRepository
import com.depromeet.piki.tournament.service.TournamentItemPersistenceService
import com.depromeet.piki.wishlist.service.WishPersistenceService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

// 이미지 등록 v2 폴링 백스톱 — 클라 confirm 에 의존하지 않고 "업로드된 pending 을 서버가 스스로 확인해 등록"한다.
// SQS 같은 이벤트 인프라 없이, 기존 outbox 폴링(ItemParsingScheduler)과 같은 방식으로 동작한다:
//   1. 아직 안 만료된 pending 을 집어 S3 HEAD(exists)로 업로드 여부 확인 → 올라온 것을 confirm 과 같은 배치 단위로 claim + 등록.
//   2. 유효기간이 지난 pending 은 정리하되, 업로드는 됐는데 등록이 밀린 것은 유실 대신 마지막으로 한 번 등록을 시도한다.
// 등록은 confirm 과 같은 registerClaimedImages(claim = FOR UPDATE 삭제)를 거치므로, confirm 과 동시에 같은 key 를
// 다뤄도 한쪽만 이긴다(멱등).
//
// HEAD·등록은 외부 호출·짧은 트랜잭션이라 공유 스케줄러 스레드가 아니라 전용 @Async executor 에서 돈다
// (ItemParsingScheduler 가 파싱을 @Async 로 빼는 것과 같은 결) — 그대로 스케줄러 스레드에서 돌면 S3 지연 시
// 파싱 dispatch·SSE heartbeat 등 다른 @Scheduled 를 굶긴다. 재진입 가드로 이전 폴링과 겹치지 않게 한다.
//
// enabled=false 로 두면 @Scheduled 자동 실행만 끈다(통합 테스트가 stub exists 로 발급 매핑을 조용히 등록해 오염되는 것을 막고,
// 폴링 테스트는 pollOnce() 를 직접 호출해 결정적으로 검증한다).
@Component
class PendingUploadPollingScheduler(
    private val pendingUploadRepository: PendingUploadRepository,
    private val imageStorage: ImageStorage,
    private val wishPersistenceService: WishPersistenceService,
    private val tournamentItemPersistenceService: TournamentItemPersistenceService,
    @Value("\${image.upload-polling-enabled:true}") private val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 이전 폴링이 아직 도는 중이면(느린 S3 등) 겹쳐 돌지 않게 한다.
    private val running = AtomicBoolean(false)

    @Async(AsyncConfig.IMAGE_POLLING_EXECUTOR)
    @Scheduled(fixedDelayString = "\${image.upload-poll-interval-ms:1000}")
    fun poll() {
        if (!enabled) return
        // 스케줄러 스레드는 @Async 로 즉시 반환되고, 실제 폴링은 전용 풀에서 돈다. 이전 폴링이 아직 돌면 이번 주기는 건너뛴다.
        if (!running.compareAndSet(false, true)) return
        try {
            pollOnce()
        } finally {
            running.set(false)
        }
    }

    // 폴링 1회 — 자동 실행(poll)과 테스트 수동 호출이 공유하는 실제 로직.
    fun pollOnce() {
        val now = LocalDateTime.now()
        registerUploaded(now)
        expireStale(now)
    }

    // 대기 중 pending 중 S3 에 올라온 것을, confirm 과 같은 배치 단위(같은 user·context·tournament)로 묶어 등록한다.
    // 단건씩 등록하면 정원 판정이 건별로 갈려 confirm 의 all-or-nothing 과 어긋나므로(폴링만 partial-fill), 그룹 배치로 맞춘다.
    private fun registerUploaded(now: LocalDateTime) {
        pendingUploadRepository
            .findLiveForPolling(now, now.minus(POLL_GRACE), BATCH_SIZE)
            .filter { isUploaded(it) }
            .groupBy { RegisterGroup(it.userId, it.context, it.tournamentId) }
            .forEach { (group, uploads) ->
                // 그룹 등록 실패(정원 초과 등)는 격리한다 — 다른 그룹을 멈추지 않고, 이 그룹은 다음 폴링이 재시도.
                runCatching { registerGroup(group, uploads.map { it.imageKey }) }
                    .onFailure { e -> log.warn("pending 그룹(user={}, ctx={}) 등록 실패(다음 폴링 재시도): {}", group.userId, group.context, e.message) }
            }
    }

    // 만료된(발급 후 유효기간 지난) pending 을 정리한다. 단 업로드는 성공했으나 등록이 밀린 채 만료된 것을 그냥 지우면
    // at-least-once 가 깨지므로, 지우기 전에 S3 존재를 확인해 올라와 있으면 마지막으로 등록을 시도한다.
    private fun expireStale(now: LocalDateTime) {
        pendingUploadRepository.findExpired(now, BATCH_SIZE).forEach { upload ->
            val uploaded =
                runCatching { imageStorage.exists(upload.imageKey) }
                    .getOrElse { e ->
                        // 존재 확인 자체가 실패(S3 장애)면 유실 위험이 있으니 이번 정리를 보류하고 다음 폴링에 맡긴다.
                        log.warn("만료 pending {} 존재 확인 실패, 정리 보류: {}", upload.imageKey, e.message)
                        return@forEach
                    }
            if (!uploaded) {
                // 업로드 없이 유효기간이 지난 발급 — 정리한다(raw 는 애초에 없다).
                pendingUploadRepository.deleteAll(listOf(upload))
                return@forEach
            }
            // 업로드는 됐는데 등록이 만료까지 밀렸다 — 마지막으로 등록을 시도하고, 그래도 실패(정원 초과 등 영구 사유)면
            // 무한 재시도 대신 폐기하고 경고를 남긴다(운영자 인지 + raw 는 items/raw/ lifecycle 이 정리).
            runCatching { registerGroup(RegisterGroup(upload.userId, upload.context, upload.tournamentId), listOf(upload.imageKey)) }
                .onFailure { e ->
                    log.warn("업로드됐으나 등록 못 한 채 만료된 pending {} 폐기: {}", upload.imageKey, e.message)
                    pendingUploadRepository.deleteAll(listOf(upload))
                }
        }
    }

    // HEAD 는 외부 호출 — 실패(일시 장애)면 "안 올라옴"으로 확정하지 않고 이번 주기만 건너뛴다(live 라 다음 폴링이 재시도).
    private fun isUploaded(upload: PendingUpload): Boolean =
        runCatching { imageStorage.exists(upload.imageKey) }
            .getOrElse { e ->
                log.warn("pending {} 존재 확인 실패, 이번 주기 건너뜀: {}", upload.imageKey, e.message)
                false
            }

    private fun registerGroup(
        group: RegisterGroup,
        imageKeys: List<String>,
    ) {
        when (group.context) {
            PendingUploadContext.WISH ->
                wishPersistenceService.registerClaimedImages(imageKeys, group.userId)
            PendingUploadContext.TOURNAMENT ->
                tournamentItemPersistenceService.registerClaimedImages(
                    imageKeys,
                    group.userId,
                    // TOURNAMENT 매핑은 팩토리가 tournamentId 를 강제하므로 정상 흐름엔 항상 있다(없으면 코드 버그).
                    group.tournamentId ?: error("TOURNAMENT pending 그룹에 tournamentId 가 없다"),
                )
        }
    }

    // confirm 이 (user, context, tournament) 하나로 배치 등록하는 것과 같은 grouping key — 폴링도 이 단위로 묶어 원자성을 맞춘다.
    private data class RegisterGroup(
        val userId: UUID,
        val context: PendingUploadContext,
        val tournamentId: Long?,
    )

    companion object {
        private const val BATCH_SIZE = 100

        // 폴링은 confirm(빠른 경로)이 처리할 시간을 준 뒤에만 개입한다 — 발급 후 이 시간이 지난 pending 만 백스톱 대상으로 삼아,
        // confirm 과 같은 key 를 다투는 레이스(부분 응답·재시도 오탐·불필요한 HEAD)를 시간으로 분리해 줄인다.
        private val POLL_GRACE: Duration = Duration.ofSeconds(15)
    }
}
