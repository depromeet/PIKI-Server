package com.depromeet.piki.common.storage

// 이미지 바이트를 저장소(S3 등)에 올리고 공개 접근 URL 을 돌려준다.
// 크롭 이미지 외에도 재사용할 수 있도록 저장 구현(S3)과 분리한다.
interface ImageStorage {
    fun upload(
        bytes: ByteArray,
        key: String,
        contentType: String,
    ): String

    // prefix 하의 모든 객체를 삭제한다. 회원 탈퇴 시 유저 프로필 이미지(profiles/{userId}/)를 통째로 파기하는 데 쓴다.
    // prefix 에 객체가 없으면 no-op. 객체가 없는 경우에도 안전하게 호출할 수 있다.
    fun deleteByPrefix(prefix: String)
}
