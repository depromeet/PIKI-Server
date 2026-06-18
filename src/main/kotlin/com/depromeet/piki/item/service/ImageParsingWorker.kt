package com.depromeet.piki.item.service

import com.depromeet.piki.image.domain.ProductImage

// PROCESSING item 의 이미지 파싱을 백그라운드로 수행하는 경계.
// 호출 즉시 반환하고 실제 파싱(외부 LLM 호출)은 비동기로 진행되어 item 을 READY/FAILED 로 전이시킨다.
// ItemParsingWorker(URL 기반) 와 분리해 두어, 이미지 경로가 URL 경로와 독립적으로 교체·확장 가능하다.
interface ImageParsingWorker {
    fun parse(
        itemId: Long,
        snapshotId: Long,
        image: ProductImage,
    )
}
