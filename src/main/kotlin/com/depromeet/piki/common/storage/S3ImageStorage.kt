package com.depromeet.piki.common.storage

import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class S3ImageStorage(
    private val s3Client: S3Client,
    private val s3Properties: S3Properties,
) : ImageStorage {
    override fun upload(
        bytes: ByteArray,
        key: String,
        contentType: String,
    ): String =
        // AWS SDK 예외(네트워크·권한·버킷 장애)를 계약 예외(502)로 변환해 호출부가 일관되게 다루게 한다.
        runCatching {
            // S3 object key 는 raw 로 저장하고, 반환 URL 만 경로 인코딩한다.
            s3Client.putObject(
                PutObjectRequest
                    .builder()
                    .bucket(s3Properties.bucket)
                    .key(key)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromBytes(bytes),
            )
            "${s3Properties.publicBaseUrl.trimEnd('/')}/${encodePath(key)}"
        }.getOrElse { e -> throw ImageStorageException.uploadFailed(e) }

    // key 의 각 경로 세그먼트를 URL 인코딩한다 ('/' 는 구분자로 보존). 공백/한글/예약문자 키도
    // 접근 가능한 URL 이 되도록 한다 (URLEncoder 의 '+' 는 path 에선 '%20' 이어야 한다).
    private fun encodePath(key: String): String =
        key
            .split("/")
            .joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20") }
}
