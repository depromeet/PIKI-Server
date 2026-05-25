package com.depromeet.piki.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfig::class, IntegrationStubs::class)
abstract class IntegrationTestSupport
