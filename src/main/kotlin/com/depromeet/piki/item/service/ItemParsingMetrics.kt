package com.depromeet.piki.item.service

import io.micrometer.core.instrument.MeterRegistry

// 파싱 단건의 종결 결과(READY/FAILED)를 result·reason 라벨로 센다 — 추출 실패가 트래픽에서 얼마나·왜 나는지 관측한다(#506).
// 메트릭 이름·라벨 키를 한 곳에 고정해 여러 emit 경로(워커·recover)가 같은 키 집합을 쓰게 한다: 키가 어긋나면
// Prometheus 가 뒤 시계열을 조용히 드롭하기 때문(#465 product.extract 사건). 모든 호출이 result·reason 둘 다 채운다.
// host 는 등록 URL 마다 달라 카디널리티가 무한히 커질 수 있어 라벨에 넣지 않는다 — 호스트별 실패는 로그(safeLogString)로 본다.
object ItemParsingMetrics {
    const val METRIC = "item.parsing"
    const val TAG_RESULT = "result"
    const val TAG_REASON = "reason"

    const val RESULT_READY = "ready"
    const val RESULT_FAILED = "failed"

    // 성공.
    const val REASON_NONE = "none"

    // 워커 확정 실패 — 상품 페이지 아님·추출값 신뢰 불가(ProductSnapshotException). 클라이언트 입력 계약 위반.
    const val REASON_NOT_PRODUCT = "not_product"

    // 추출은 됐으나 READY 전이가 값 검증에 막힘(이름 없음 등).
    const val REASON_READY_REJECTED = "ready_rejected"

    // recover — 일시 오류 재실행이 상한을 소진. "우리 파이프라인이 끝내 못 끝낸" 진짜 실패라 가장 주목할 reason.
    const val REASON_RETRY_EXHAUSTED = "retry_exhausted"

    // recover — 되살릴 원본이 없음(이미지: 원본이 메모리 ByteArray 라 크래시 시 소실 / orphan: link 부재).
    const val REASON_NO_SOURCE = "no_source"

    fun record(
        registry: MeterRegistry,
        result: String,
        reason: String,
    ) {
        registry.counter(METRIC, TAG_RESULT, result, TAG_REASON, reason).increment()
    }
}
