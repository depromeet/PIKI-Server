package com.depromeet.piki.item.domain

// item 의 파싱 생애주기. 등록은 외부 LLM 추출(긴 latency)을 백그라운드로 미루므로,
// 클라이언트가 "담는 중 / 완성 / 등록 실패" 를 구분할 수 있도록 상태를 서버가 명시적으로 갖는다.
enum class ItemStatus {
    // URL 등록 직후 커밋된 상태(outbox 적재). 아직 디스패처가 집지 않아 파싱이 시작 전이다.
    // 작업의 진실 원천은 인메모리 큐가 아니라 이 DB 행이라, @Async 유실과 무관하게 반드시 한 번은 claim 된다.
    // 이미지 등록 경로는 PENDING 을 거치지 않고 곧장 PROCESSING 으로 시작한다(@Async 직접 트리거).
    PENDING,

    // 디스패처가 claim 했거나(URL) 등록 즉시 시작된(이미지) 파싱 진행 중. name·currentPrice·imageUrl 은 아직 비어 있다.
    // 워커 크래시·실행 누락으로 여기 갇힌 행은 recover 가 재실행으로 되살린다(execution at-least-once, #461) — 재시도 중도 이 상태다.
    PROCESSING,

    // 파싱 완료. 추출된 상품 정보가 채워졌다.
    READY,

    // 파싱 실패. 두 갈래다: (1) 확정 실패 — 상품 페이지가 아니거나(notProductPage) 추출값을 신뢰할 수 없음(untrustworthyValue).
    // 재시도해도 결과가 같으므로 워커가 즉시 종결한다. (2) 재시도 소진 — 일시 외부 오류·크래시로 실행이 끝나지 않은 행을
    // recover 가 재실행 상한(현재 2회)까지 되살려봤으나 끝내 완료하지 못함. 동기 400 이 아니라 상태로 표현해 클라이언트가 SSE 알림으로 인지한다.
    FAILED,
}
