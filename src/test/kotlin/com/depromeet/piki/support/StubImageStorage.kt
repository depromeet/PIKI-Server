package com.depromeet.piki.support

import com.depromeet.piki.common.storage.ImageStorage

// S3 업로드를 통합 테스트에서 격리하는 stub. 기본 동작은 key 로 URL 을 만드는 순수 변환이라
// 이전 테스트 상태가 누수될 위험이 없어, ProductExtractor stub 과 달리 default 를 고정 URL 로 둔다.
// S3 실패(502) 시나리오가 필요한 테스트만 본문에서 behavior 를 throw 람다로 교체한다(직접 복원).
// 호출 검증이 필요하면 uploadedKeys 를 본다.
class StubImageStorage : ImageStorage {
    val uploadedKeys = mutableListOf<String>()

    // deleteByPrefix 호출 기록. 삭제는 부수효과뿐이라 default 가 throw 가 아니라 단순 기록이라 안전하다
    // (upload 와 같은 결 — 명시 세팅을 강제할 동적 응답이 없다). 호출 검증은 deletedPrefixes 를 본다.
    val deletedPrefixes = mutableListOf<String>()

    // 기본 동작 — key 로 고정 URL 생성. 502 시나리오 테스트가 behavior 를 throw 람다로 교체한 뒤 이 값으로 복원한다.
    val defaultBehavior: (ByteArray, String, String) -> String = { _, key, _ -> "$BASE_URL/$key" }
    var behavior: (ByteArray, String, String) -> String = defaultBehavior

    override fun upload(
        bytes: ByteArray,
        key: String,
        contentType: String,
    ): String {
        val url = behavior(bytes, key, contentType)
        uploadedKeys.add(key)
        return url
    }

    override fun deleteByPrefix(prefix: String) {
        deletedPrefixes.add(prefix)
    }

    companion object {
        // 실제 S3ImageStorage 와 같은 "{publicBaseUrl}/{key}" 형식을 흉내 내도록, 테스트 application.yml 의
        // s3.public-base-url 과 동일하게 둔다. 공지 이미지 rehost(#561)가 "이미 우리 S3 인 URL"을 publicBaseUrl
        // prefix 로 판별하므로, 이 값이 어긋나면 재저장 시 stub URL 을 외부로 오인해 다시 fetch 하려 한다.
        const val BASE_URL = "https://test-bucket.s3.ap-northeast-2.amazonaws.com"
    }
}
