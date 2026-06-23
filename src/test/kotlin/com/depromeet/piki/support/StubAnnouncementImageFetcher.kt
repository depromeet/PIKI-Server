package com.depromeet.piki.support

import com.depromeet.piki.admin.announcement.AnnouncementImageFetcher
import com.depromeet.piki.admin.announcement.FetchedImage

// 공지 본문 외부 이미지 fetch(HTTP) 외부 경계 stub(#561). 실제 외부 URL 을 호출하지 않고,
// 테스트가 behavior 람다로 시나리오별 응답(바이트·content-type)이나 실패(throw)를 명시 세팅한다.
// default 는 throw — 명시 세팅을 빠뜨린 테스트가 조용히 통과하지 않게 한다(이전 테스트 build 잔존 함정 차단).
// 호출 검증은 requestedUrls 를 본다.
class StubAnnouncementImageFetcher : AnnouncementImageFetcher {
    val requestedUrls = mutableListOf<String>()

    var behavior: (String) -> FetchedImage = {
        error("StubAnnouncementImageFetcher.behavior 를 테스트 본문에서 명시 세팅해야 한다.")
    }

    override fun fetch(url: String): FetchedImage {
        requestedUrls.add(url)
        return behavior(url)
    }
}
