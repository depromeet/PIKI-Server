package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.ProductLink

data class PageContent(
    // 사용자가 등록한 원본 URL. 저장·식별(item.link)에 쓰는 정체성이라 redirect 와 무관하게 원본을 유지한다.
    val link: ProductLink,
    val html: String,
    // redirect 를 따라간 최종 페이지 URL. html 의 출처이므로 상대 URL resolve(Jsoup baseUri)는 이 값 기준이어야 한다.
    // redirect 가 없으면 link 와 같다. fetch 의 런타임 산출물이라 DB 에 저장하지 않는다(경로·IP 는 매 fetch 새로 구함).
    val finalUrl: ProductLink = link,
)
