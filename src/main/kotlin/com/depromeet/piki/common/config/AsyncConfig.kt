package com.depromeet.piki.common.config

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

    companion object {
        const val ITEM_PARSING_EXECUTOR = "itemParsingExecutor"
    }
}
