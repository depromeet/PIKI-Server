package com.depromeet.piki.notification.service.dto

// 읽음 처리 명령 — 요청 DTO(NotificationReadRequest)의 XOR 검증을 통과한 "정확히 한 가지" 의도를 타입으로 고정한다.
// 서비스는 when + sealed 로 분기해 nullable 잡탕 분기를 피한다.
sealed interface NotificationReadCommand {
    // 본인 안읽음 알림 전부 읽음 (전체 읽음 버튼).
    data object All : NotificationReadCommand

    // 지정한 알림들만 읽음 (단건 클릭 등). 본인 소유만 반영되고 타인/없는 id 는 무영향.
    data class Ids(
        val ids: List<Long>,
    ) : NotificationReadCommand
}
