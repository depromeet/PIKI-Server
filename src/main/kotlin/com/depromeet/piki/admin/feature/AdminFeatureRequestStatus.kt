package com.depromeet.piki.admin.feature

/**
 * 기능 요청 처리 상태. 인박스 운영에 필요한 최소 두 단계만 둔다.
 *
 * NEW  — 막 접수돼 아직 검토 안 함.
 * DONE — 검토 완료(이슈로 승격했거나 반려해 더 볼 필요 없음). "반려"와 "완료"를 따로 나누지 않는다 —
 *        v1 인박스엔 "더 볼지 말지" 만 필요하고, 세부 사유는 승격된 GitHub 이슈가 가진다.
 */
enum class AdminFeatureRequestStatus {
    NEW,
    DONE,
}
