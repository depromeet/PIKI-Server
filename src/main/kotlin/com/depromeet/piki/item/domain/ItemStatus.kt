package com.depromeet.piki.item.domain

// item 의 파싱 생애주기. 등록은 외부 LLM 추출(긴 latency)을 백그라운드로 미루므로,
// 클라이언트가 "담는 중 / 완성 / 등록 실패" 를 구분할 수 있도록 상태를 서버가 명시적으로 갖는다.
enum class ItemStatus {
    // 등록 직후. 파싱이 진행 중이며 name·currentPrice·imageUrl 은 아직 비어 있다.
    PROCESSING,

    // 파싱 완료. 추출된 상품 정보가 채워졌다.
    READY,

    // 파싱 실패. 상품 페이지가 아니거나(notProductPage) 추출값을 신뢰할 수 없거나(untrustworthyValue)
    // 인스턴스 크래시 등으로 타임아웃됐다. 동기 400 이 아니라 상태로 표현해 클라이언트가 폴링으로 인지한다.
    FAILED,
}
