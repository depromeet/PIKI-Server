package com.depromeet.piki.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

// 모든 통합 테스트가 공유하는 단일 컨텍스트(캐시 보존). admin 백오피스(#249/#489/#526) 흐름도 이 컨텍스트에서 검증한다:
// - admin.enabled=true: admin 빈(서비스·스케줄러·게이트)을 로드해 테스트 가능하게 한다.
// - admin.local-bypass=true: /admin 게이트(AdminAccessFilter)를 우회해 컨트롤러 흐름을 슬랙 세션 없이 호출한다.
// - admin.scheduler-auto-dispatch=false: 스케줄러 주기 폴링을 꺼 테스트 중 예약이 백그라운드로 발사돼 flaky 해지는 걸 막는다
//   (예약/overdue 로직은 dispatchDue() 를 테스트가 직접 호출해 결정적으로 검증한다).
@SpringBootTest(
    properties = [
        "admin.enabled=true",
        "admin.local-bypass=true",
        "admin.scheduler-auto-dispatch=false",
    ],
)
@Import(TestcontainersConfig::class, IntegrationStubs::class)
abstract class IntegrationTestSupport
