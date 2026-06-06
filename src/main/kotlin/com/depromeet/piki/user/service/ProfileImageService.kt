package com.depromeet.piki.user.service

import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.user.domain.ProfileImageFile
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.domain.UserException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

// 프로필 이미지 업로드 오케스트레이션. 외부 호출(S3 업로드)은 트랜잭션 밖에서 끝내고,
// 영속화는 UserService.updateProfileImageUrl(@Transactional) 의 짧은 트랜잭션에 위임한다
// (## 트랜잭션 경계 — 외부 호출은 트랜잭션 밖, self-invocation 회피를 위해 별도 빈으로 분리).
@Service
class ProfileImageService(
    private val userService: UserService,
    private val imageStorage: ImageStorage,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun updateProfileImage(
        userId: UUID,
        bytes: ByteArray,
        contentType: String?,
    ): User {
        // 형식 검증(빈 바이트·미지원 MIME)을 업로드 전에 끝낸다 — 실패 시 즉시 400(UserException).
        val image = ProfileImageFile.of(bytes, contentType)
        // 업로드 전 존재·탈퇴 사전 확인으로 orphan S3 객체를 방지한다(최후 보루는 updateProfileImageUrl).
        val user = userService.findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser(userId) }
        val key = "profiles/$userId/${UUID.randomUUID()}.${image.extension}"
        val url = imageStorage.upload(image.bytes, key, image.mimeType) // 트랜잭션 밖, 실패 시 502
        log.info("프로필 이미지 업로드 완료: userId={}, key={}", userId, key)
        return userService.updateProfileImageUrl(userId, url)
    }
}
