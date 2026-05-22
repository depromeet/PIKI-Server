package com.depromeet.team3.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "s3")
data class S3Properties(
    val bucket: String,
    val region: String = "ap-northeast-2",
    // 업로드한 객체의 공개 베이스 URL (예: https://{bucket}.s3.{region}.amazonaws.com). 끝 슬래시는 무시한다.
    val publicBaseUrl: String,
) {
    init {
        require(bucket.isNotBlank()) { "s3.bucket 이 비어 있습니다." }
        require(publicBaseUrl.isNotBlank()) { "s3.public-base-url 이 비어 있습니다." }
    }
}
