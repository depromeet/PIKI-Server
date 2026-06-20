package com.depromeet.piki

import com.depromeet.piki.support.IntegrationStubs
import com.depromeet.piki.support.TestcontainersConfig
import org.springframework.boot.fromApplication
import org.springframework.boot.with

// 로컬에서 Testcontainers(MySQL·Redis)를 띄운 채 앱을 실행하는 진입점 — `./gradlew bootTestRun`.
// 운영 부팅(PikiApplication)에 (1) TestcontainersConfig 로 DB·Redis 를 컨테이너로 제공하고,
// (2) IntegrationStubs 로 외부 호출(OAuth·Apple 검증·Gemini·FCM·S3)을 stub 으로 격리한다 — 키 없이도 부팅된다.
fun main(args: Array<String>) {
    fromApplication<PikiApplication>().with(TestcontainersConfig::class, IntegrationStubs::class).run(*args)
}
