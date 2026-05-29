package com.depromeet.piki.common.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

// 등록 시 item 파싱(외부 LLM 호출, read-timeout 60s)을 HTTP 응답과 분리해 백그라운드로 돌리기 위한 executor.
// 단일 인스턴스 MVP 기준의 보수적 풀 크기다. 운영 트래픽이 보이면 application.yml 로 빼 튜닝한다.
@Configuration
@EnableAsync
class AsyncConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean(ITEM_PARSING_EXECUTOR)
    fun itemParsingExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 100
            setThreadNamePrefix("item-parsing-")
            // 포화 시 기본 AbortPolicy 로 거부한다. 호출 스레드(톰캣)에서 동기 실행하는 CallerRunsPolicy 는
            // 외부 LLM 호출(최대 60s)로 톰캣 워커 풀을 고갈시켜 무관한 API 까지 번지므로 쓰지 않는다.
            // 거부는 호출부(WishlistService.register, TournamentItemService.addItemsFromImages 등)가 잡아
            // 해당 item 을 즉시 FAILED 로 떨어뜨린다(PROCESSING 방치 금지).
            initialize()
        }

    // 알림 디스패치(AFTER_COMMIT 리스너)를 발행 트랜잭션·톰캣 워커와 분리하기 위한 executor.
    // 알림 작업은 DB 저장 + 채널 전달이라 item 파싱(외부 LLM 60s)보다 가벼워 풀을 작게 둔다.
    // 포화 시 로그만 남기고 버린다 — 알림은 best-effort 이고 원본은 이미 DB 에 영속화되므로 유실돼도 복원 가능.
    //
    // itemParsingExecutor 와 달리 기본 AbortPolicy(거부 시 throw)를 쓰지 않는다: 이 태스크는
    // @Async + @TransactionalEventListener(AFTER_COMMIT) 로 발행 트랜잭션이 커밋된 그 스레드 위에서
    // 동기 submit 된다. AbortPolicy 면 거부 예외(TaskRejectedException)가 AFTER_COMMIT 콜백을 타고
    // 발행부(ItemParsingService.markReady/markFailed) 호출 스택으로 역류해, 이미 정상 커밋된 item 에
    // "READY/FAILED 전이 실패" 오경보를 남긴다(item 상태 자체는 가드로 보존되지만 로그·메트릭이 오염된다).
    // 따라서 거부를 throw 대신 drop+warn 으로 처리해 "유실 허용" 의도를 실제로 구현한다.
    @Bean(NOTIFICATION_EXECUTOR)
    fun notificationExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 200
            setThreadNamePrefix("notification-")
            setRejectedExecutionHandler { _, executor ->
                log.warn(
                    "알림 executor 포화로 태스크 거부 — 알림 1건 유실 (activeCount={}, queueSize={})",
                    executor.activeCount,
                    executor.queue.size,
                )
            }
            initialize()
        }

    companion object {
        const val ITEM_PARSING_EXECUTOR = "itemParsingExecutor"
        const val NOTIFICATION_EXECUTOR = "notificationExecutor"
    }
}
