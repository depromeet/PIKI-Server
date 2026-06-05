package com.depromeet.piki.common.config

import com.depromeet.piki.support.IntegrationTestSupport
import net.ttddyy.observation.boot.autoconfigure.DataSourceObservationBeanPostProcessor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.assertTrue

// datasource-micrometer 가 Spring Boot 4 에서 autoconfig 되는지 검증한다. 부팅 성공만으론 autoconfig 가 조용히
// skip 됐는지 알 수 없어(Boot 4 공식 호환 명시 없음), 핵심 빈(DataSourceObservationBeanPostProcessor) 등록을
// 직접 단언해 negative control 로 둔다. 이 BeanPostProcessor 가 DataSource 를 observation proxy(JDK dynamic
// proxy)로 감싸 JDBC 쿼리를 trace span 으로 잡는다(이 작업의 목적). 미등록이면 SQL span 이 안 생긴다.
class DataSourceObservationIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `datasource-micrometer autoconfig 가 적용돼 DataSource 를 observation proxy 로 감싼다`() {
        assertTrue(
            applicationContext.getBeanNamesForType(DataSourceObservationBeanPostProcessor::class.java).isNotEmpty(),
            "datasource-micrometer 의 DataSourceObservationBeanPostProcessor 가 등록돼야 SQL 이 span 으로 잡힌다",
        )
    }
}
