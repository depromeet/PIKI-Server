package com.depromeet.piki.common.storage

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.time.Duration

@Configuration
class S3Config(
    private val s3Properties: S3Properties,
) {
    // 자격증명은 DefaultCredentialsProvider 체인으로 해결한다 — EC2 는 instance role,
    // 로컬은 환경변수/프로파일. 코드에 access key 를 박지 않는다.
    // 업로드가 무한정 매달리지 않도록 호출/시도 타임아웃을 둔다 (외부 호출 경계).
    @Bean
    fun s3Client(): S3Client =
        S3Client
            .builder()
            .region(Region.of(s3Properties.region))
            .overrideConfiguration(
                ClientOverrideConfiguration
                    .builder()
                    .apiCallTimeout(Duration.ofSeconds(10))
                    .apiCallAttemptTimeout(Duration.ofSeconds(5))
                    .build(),
            ).build()
}
