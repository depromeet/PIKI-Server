package com.depromeet.piki.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.context.ImportTestcontainers

// Spring 이 컨테이너 라이프사이클을 직접 관리하므로 @Testcontainers JUnit extension 불필요.
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(IntegrationContainers::class)
class TestcontainersConfig
