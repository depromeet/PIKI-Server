package com.depromeet.piki.notification.service

import com.depromeet.piki.common.storage.S3Properties
import org.springframework.stereotype.Component

// 시스템 알림(actor 없음)의 기본 아바타 URL 발급(#473). 아이콘 파일은 이미지 버킷의 `defaults/push-icon.svg` 로
// 사전 업로드돼 있고(랜덤 기본 프사와 같은 공개 `defaults/` 경로), 여기선 그 공개 URL 만 조립한다.
// publicBaseUrl 로 조립하므로 dev/prod 버킷 차이를 흡수한다(DefaultProfileImages 와 같은 방식 — 주소를 박지 않는다).
// payload imageUrl 은 actor 스냅샷이 있으면 그것을, 없으면(시스템) 이 URL 을 채워 항상 비지 않게 한다.
@Component
class DefaultPushImage(
    s3Properties: S3Properties,
) {
    val url: String = "${s3Properties.publicBaseUrl.trimEnd('/')}/$KEY"

    companion object {
        const val KEY = "defaults/push-icon.svg"
    }
}
