package com.depromeet.piki.support

import com.depromeet.piki.common.imageproxy.FetchedImage
import com.depromeet.piki.common.imageproxy.ImageProxyFetcher

class StubImageProxyFetcher : ImageProxyFetcher {
    var handler: (url: String) -> FetchedImage = {
        error("stub.handler 를 테스트 본문에서 명시 세팅해야 한다.")
    }

    override fun fetch(url: String): FetchedImage = handler(url)
}
