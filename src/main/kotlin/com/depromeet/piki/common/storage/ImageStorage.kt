package com.depromeet.piki.common.storage

// 이미지 바이트를 저장소(S3 등)에 올리고 공개 접근 URL 을 돌려준다.
// 크롭 이미지 외에도 재사용할 수 있도록 저장 구현(S3)과 분리한다.
interface ImageStorage {
    fun upload(
        bytes: ByteArray,
        key: String,
        contentType: String,
    ): String
}
