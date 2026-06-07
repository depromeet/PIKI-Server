package com.depromeet.piki.support

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.PageContent
import com.depromeet.piki.product.service.PageFetcher

// 외부 HTTP fetch 경계를 통합 테스트에서 격리하는 stub. 매 테스트가 본문에서 build 람다를 명시 세팅한다.
// default build 는 throw — 명시 세팅을 빠뜨리면 즉시 깨져 "이전 테스트의 build 가 살아남는" 함정을 차단한다.
class StubPageFetcher : PageFetcher {
    var build: (ProductLink) -> PageContent = {
        error("stub.build 를 테스트 본문에서 명시 세팅해야 한다. CLAUDE.md '테스트 셋업 원칙' 참고.")
    }

    override fun fetch(link: ProductLink): PageContent = build(link)
}
