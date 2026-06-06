package com.depromeet.piki.notification.fcm.controller.dto

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.notification.fcm.domain.UserDevice
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

@Schema(description = "[DEV] FCM 즉시 발송 요청 — 본문의 토큰으로 바로 푸시(등록 불필요). FE 가 Xcode 에서 받은 토큰을 붙여 발송 경로를 확인한다.")
data class DevPushRequest(
    @field:NotBlank
    @field:Size(max = UserDevice.MAX_TOKEN_LENGTH)
    @field:Schema(
        description = "발송할 FCM 토큰 (Xcode 등에서 실시간으로 받은 값)",
        example = "fGcServerTokenSample:APA91bF...",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val token: String,
    @field:Size(max = Notification.MAX_TEXT_LENGTH)
    @field:Schema(description = "푸시 제목", example = DEFAULT_TITLE, defaultValue = DEFAULT_TITLE)
    val title: String = DEFAULT_TITLE,
    @field:Size(max = Notification.MAX_TEXT_LENGTH)
    @field:Schema(description = "푸시 본문", example = DEFAULT_BODY, defaultValue = DEFAULT_BODY)
    val body: String = DEFAULT_BODY,
) {
    // 받는 쪽(Notification)이 매핑을 책임진다. 테스트 발송은 딥링크 대상이 없어 더미 type·refId.
    // userId 는 발송 메시지에 실리지 않고 throwaway Notification 구성에만 쓰여, 호출자(인증 유저)를 그대로 넣는다.
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

        private const val TEST_REF_ID = 0L
    }
}
