package com.depromeet.piki.user.service

import com.depromeet.piki.common.storage.S3Properties
import org.springframework.stereotype.Component

// 기본 프로필 아바타 URL 발급. 4종 기본 아바타는 S3 이미지 버킷 루트에 `user-profile-{n}.png` (n=1..COUNT) 로
// 사전 업로드돼 있고(이미지 파일 자체는 운영에서 업로드), 여기선 그 공개 URL 만 조립한다.
// publicBaseUrl 로 조립하므로 dev/prod 버킷 차이를 흡수한다(프로필 업로드와 같은 버킷이라 별도 주소 불필요).
@Component
class DefaultProfileImages(
    private val s3Properties: S3Properties,
) {
    // 1..COUNT 중 하나를 랜덤으로 골라 그 기본 아바타의 공개 URL 을 돌려준다.
    fun random(): String = urlOf((1..COUNT).random())

    // URL 조립은 publicBaseUrl 외 의존이 없는 순수 로직이라 단위 테스트로 형식을 망라한다.
    fun urlOf(index: Int): String = "${s3Properties.publicBaseUrl.trimEnd('/')}/$FILENAME_PREFIX$index.$EXTENSION"

    companion object {
        const val COUNT = 4
        const val FILENAME_PREFIX = "user-profile-"
        const val EXTENSION = "png"
    }
}
