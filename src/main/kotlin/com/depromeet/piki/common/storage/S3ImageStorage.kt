package com.depromeet.piki.common.storage

import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@Component
class S3ImageStorage(
    private val s3Client: S3Client,
    private val s3Properties: S3Properties,
) : ImageStorage {
    override fun upload(
        bytes: ByteArray,
        key: String,
        contentType: String,
    ): String {
        s3Client.putObject(
            PutObjectRequest
                .builder()
                .bucket(s3Properties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes),
        )
        return "${s3Properties.publicBaseUrl.trimEnd('/')}/$key"
    }
}
