package com.depromeet.piki.notification.fcm.controller.dto

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.util.UUID

@Schema(description = "[DEV] FCM 즉시 발송 요청 — 인증 유저 본인의 모든 기기로 푸시를 쏜다(발송 경로 동작 확인용)")
data class DevPushRequest(
    @field:Size(max = Notification.MAX_TEXT_LENGTH)
    @field:Schema(description = "푸시 제목", example = DEFAULT_TITLE, defaultValue = DEFAULT_TITLE)
    val title: String = DEFAULT_TITLE,
    @field:Size(max = Notification.MAX_TEXT_LENGTH)
    @field:Schema(description = "푸시 본문", example = DEFAULT_BODY, defaultValue = DEFAULT_BODY)
    val body: String = DEFAULT_BODY,
) {
    // 받는 쪽(Notification)이 매핑을 책임진다 — 요청 DTO 의 toXxx(). 테스트 발송은 딥링크 대상이 없어 더미 refId/type.
    fun toNotification(userId: UUID): Notification =
        Notification(
            userId = userId,
            type = NotificationType.ITEM_PARSING_COMPLETED,
            title = title,
            body = body,
            refId = TEST_REF_ID,
        )

    companion object {
        const val DEFAULT_TITLE = "PIKI 테스트 알림"
        const val DEFAULT_BODY = "FCM 발송이 정상 동작하는지 확인하는 테스트 메시지입니다."

        // 테스트 발송은 실제 딥링크 대상(위시·토너먼트)이 없으므로 더미 refId.
        private const val TEST_REF_ID = 0L
    }
}
