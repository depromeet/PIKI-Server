package com.depromeet.piki.support

import com.depromeet.piki.common.storage.ImageStorage

// S3 업로드를 통합 테스트에서 격리하는 stub. upload 는 key 로 URL 을 만드는 순수 변환이라
// 이전 테스트 상태가 누수될 위험이 없어, ProductExtractor stub 과 달리 default 를 고정 URL 로 둔다.
// 호출 검증이 필요하면 uploadedKeys 를 본다.
class StubImageStorage : ImageStorage {
    val uploadedKeys = mutableListOf<String>()

    override fun upload(
        bytes: ByteArray,
        key: String,
        contentType: String,
    ): String {
        uploadedKeys.add(key)
        return "$BASE_URL/$key"
    }

    companion object {
        const val BASE_URL = "https://stub-images.example.com"
    }
}
