package com.depromeet.piki.common.storage

// 이미지 바이트를 저장소(S3 등)에 올리고 공개 접근 URL 을 돌려준다.
// 크롭 이미지 외에도 재사용할 수 있도록 저장 구현(S3)과 분리한다.
interface ImageStorage {
    fun upload(
        bytes: ByteArray,
        key: String,
        contentType: String,
    ): String

    // 저장소에서 객체를 바이트·content-type 으로 읽어온다. 이미지 파싱 워커가 등록 시 적재한 raw 원본을
    // outbox 재실행 시점에 다시 읽는 데 쓴다. 객체가 없거나 스토리지 장애면 ImageStorageException(502, RETRYABLE).
    fun download(key: String): StoredImage

    // prefix 하의 모든 객체를 삭제한다. 회원 탈퇴 시 유저 프로필 이미지(profiles/{userId}/)를 통째로 파기하는 데 쓴다.
    // prefix 에 객체가 없으면 no-op. 객체가 없는 경우에도 안전하게 호출할 수 있다.
    fun deleteByPrefix(prefix: String)

    // 단일 객체를 삭제한다 — 파싱이 끝난 raw 원본(items/raw/...)을 회수하는 데 쓴다. 객체가 없어도 no-op(멱등).
    fun delete(key: String)
}

// 저장소에서 읽어온 객체 — 바이트와 content-type. 워커가 ProductImage.of 로 재구성한다.
// content-type 은 업로드 시 저장한 값으로, 없을 수 있어 nullable 이다(ProductImage.of 가 null 을 400 으로 거른다).
class StoredImage(
    val bytes: ByteArray,
    val contentType: String?,
)
