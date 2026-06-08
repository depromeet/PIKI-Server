package com.depromeet.piki.item.domain

// item 의 파싱 생애주기. 등록은 외부 LLM 추출(긴 latency)을 백그라운드로 미루므로,
// 클라이언트가 "담는 중 / 완성 / 등록 실패" 를 구분할 수 있도록 상태를 서버가 명시적으로 갖는다.
enum class ItemStatus {
    // URL 등록 직후 커밋된 상태(outbox 적재). 아직 디스패처가 집지 않아 파싱이 시작 전이다.
    // 작업의 진실 원천은 인메모리 큐가 아니라 이 DB 행이라, @Async 유실과 무관하게 반드시 한 번은 claim 된다.
    // 이미지 등록 경로는 PENDING 을 거치지 않고 곧장 PROCESSING 으로 시작한다(@Async 직접 트리거).
    PENDING,

    // 디스패처가 claim 했거나(URL) 등록 즉시 시작된(이미지) 파싱 진행 중. name·currentPrice·imageUrl 은 아직 비어 있다.
    PROCESSING,

    // 파싱 완료. 추출된 상품 정보가 채워졌다.
    READY,

    // 파싱 실패. 상품 페이지가 아니거나(notProductPage) 추출값을 신뢰할 수 없거나(untrustworthyValue)
    // 인스턴스 크래시 등으로 타임아웃됐다. 동기 400 이 아니라 상태로 표현해 클라이언트가 폴링으로 인지한다.
    FAILED,
}
