package com.depromeet.piki.common.storage

import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
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

    override fun deleteByPrefix(prefix: String) {
        // AWS SDK 예외를 upload 와 동일하게 계약 예외(502)로 변환한다. 호출부(탈퇴)가 best-effort 로 감싸지만
        // 일관성을 위해 같은 변환을 둔다 — 던지는 예외 타입이 경계 전체에서 동일해야 호출부가 한 가지로 다룬다.
        runCatching {
            // ListObjectsV2 는 한 번에 최대 1000개를 돌려준다. isTruncated 면 continuation token 으로 다음 페이지를 이어 받는다.
            var continuationToken: String? = null
            while (true) {
                val listed =
                    s3Client.listObjectsV2(
                        ListObjectsV2Request
                            .builder()
                            .bucket(s3Properties.bucket)
                            .prefix(prefix)
                            .continuationToken(continuationToken)
                            .build(),
                    )
                // 비어 있으면 DeleteObjectsRequest 가 검증 실패하므로 빈 배치는 건너뛴다 (prefix no-op).
                listed
                    .contents()
                    .map { ObjectIdentifier.builder().key(it.key()).build() }
                    .takeIf { it.isNotEmpty() }
                    ?.let { ids ->
                        s3Client.deleteObjects(
                            DeleteObjectsRequest
                                .builder()
                                .bucket(s3Properties.bucket)
                                .delete(Delete.builder().objects(ids).build())
                                .build(),
                        )
                    }
                continuationToken = listed.nextContinuationToken().takeIf { listed.isTruncated } ?: break
            }
        }.getOrElse { e -> throw ImageStorageException.uploadFailed(e) }
    }

    // key 의 각 경로 세그먼트를 URL 인코딩한다 ('/' 는 구분자로 보존). 공백/한글/예약문자 키도
    // 접근 가능한 URL 이 되도록 한다 (URLEncoder 의 '+' 는 path 에선 '%20' 이어야 한다).
    private fun encodePath(key: String): String =
        key
            .split("/")
            .joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20") }
}
