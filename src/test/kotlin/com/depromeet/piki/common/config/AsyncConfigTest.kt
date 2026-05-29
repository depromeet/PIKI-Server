package com.depromeet.piki.common.config

import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import kotlin.test.Test

class AsyncConfigTest {
    // notificationExecutor 의 거부 정책 회귀 가드.
    // 이 태스크는 @Async + @TransactionalEventListener(AFTER_COMMIT) 로 발행 트랜잭션이 커밋된 그 스레드 위에서
    // 동기 submit 된다. 기본 AbortPolicy 였다면 포화 시 TaskRejectedException 을 던져, AFTER_COMMIT 콜백을 타고
    // 발행부(ItemParsingService.markReady/markFailed) 호출 스택으로 역류해 정상 item 에 "전이 실패" 오경보를 남긴다.
    // 알림은 best-effort 라 포화 시 조용히 버려야 하므로, 거부 정책이 예외를 던지지 않음(drop)을 검증한다.
    @Test
    fun `notificationExecutor 는 포화로 태스크가 거부돼도 예외를 던지지 않고 버린다`() {
        val executor = AsyncConfig().notificationExecutor() as ThreadPoolTaskExecutor
        try {
            val pool = executor.threadPoolExecutor
            // 거부 정책을 직접 호출한다 — AbortPolicy 였다면 이 호출이 RejectedExecutionException 을 던진다.
            assertDoesNotThrow {
                pool.rejectedExecutionHandler.rejectedExecution({ /* 거부될 더미 태스크 */ }, pool)
            }
        } finally {
            executor.shutdown()
        }
    }
}
