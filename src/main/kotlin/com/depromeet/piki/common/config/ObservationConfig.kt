package com.depromeet.piki.common.config

import io.micrometer.observation.ObservationPredicate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.scheduling.support.ScheduledTaskObservationContext

// 관측 노이즈를 observation 단계에서 제외한다 — predicate 가 false 면 그 observation 이 NOOP 이 되어 span·메트릭이
// 아예 생기지 않는다. Spring Boot 가 @Bean ObservationPredicate 를 ObservationRegistry 에 자동 적용한다.
//
// 제외 대상은 "우리 API 표면도, 실제 작업도 아닌" 둘뿐이다:
//  - @Scheduled 폴링(ItemParsingScheduler.dispatch 매 1s · recover 매 15s): 할 일이 없어도(claim 0건) 메서드
//    실행마다 span 을 만들어 한산한 prod 의 Tempo 트레이스를 이 폴링으로 가득 채운다. 실제 파싱 작업은 워커가
//    독립 "item.parse" span 으로 따로 남기므로(AsyncItemParsingWorker 가 @Async 라 이 폴링 trace 가 전파되지 않고
//    자기 trace 를 새로 연다) 폴링을 꺼도 파싱 추적은 그대로 보존된다. ScheduledTaskObservationContext 타입으로
//    식별해 observation name 문자열에 의존하지 않는다(컴파일 안전).
//  - actuator 요청(EC2 내부 Alloy 의 /actuator/prometheus scrape 등): 우리 API 가 아니라 수집기 트래픽이라 노이즈다.
//
// 실제 API 요청(http.server.requests, /actuator 외)·item.parse·그 밖의 observation 은 그대로 둔다.
@Configuration
class ObservationConfig {
    @Bean
    fun filterNoiseObservations(): ObservationPredicate =
        ObservationPredicate { _, context ->
            when {
                context is ScheduledTaskObservationContext -> false
                context is ServerRequestObservationContext &&
                    (context.carrier?.requestURI?.startsWith("/actuator") ?: false) -> false
                else -> true
            }
        }
}
