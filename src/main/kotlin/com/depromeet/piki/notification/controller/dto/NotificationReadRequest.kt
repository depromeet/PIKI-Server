package com.depromeet.piki.notification.controller.dto

import com.depromeet.piki.notification.service.dto.NotificationReadCommand
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue

// 읽음 처리 요청 — all=true(전체 읽음) XOR ids(지정 단건/다건). 정확히 하나만 유효(둘 다·둘 다 없음·빈 ids 는 400).
@Schema(description = "알림 읽음 처리 요청 — all=true(전체) 또는 ids(지정) 중 정확히 하나")
data class NotificationReadRequest(
    @field:Schema(description = "true 면 본인 안읽음 알림 전부 읽음 처리 (전체 읽음 버튼). ids 와 동시 사용 불가", nullable = true, example = "true")
    val all: Boolean? = null,
    @field:Schema(description = "읽음 처리할 알림 id 목록 (단건 클릭은 [id] 1개). all 과 동시 사용 불가", nullable = true, example = "[1024]")
    val ids: List<Long>? = null,
) {
    // all XOR ids — 정확히 한쪽만. 둘 다·둘 다 없음·빈 ids 는 400(입력 경계 계약, OAuthLoginRequest 패턴).
    @get:JsonIgnore
    @get:AssertTrue(message = VALID_SELECTION_MESSAGE)
    val validSelection: Boolean
        get() {
            val byAll = all == true
            val byIds = !ids.isNullOrEmpty()
            return byAll xor byIds
        }

    // validSelection 통과 후 호출 — all=true 면 All, 아니면 ids 는 non-null·non-empty 가 보장된다(불변식).
    fun toCommand(): NotificationReadCommand =
        if (all == true) {
            NotificationReadCommand.All
        } else {
            NotificationReadCommand.Ids(requireNotNull(ids) { "validSelection 통과 시 ids 는 non-null 이다" })
        }

    companion object {
        // Bean Validation 메시지를 const 로 빼 ApiExamples 가 같은 상수를 참조하게 한다(detail single-source).
        // ids 개수 상한은 두지 않는다 — 대량 읽음은 all=true 가 담당하고, ids 는 본인 알림 선택(보통 단건)이라
        // 인증·본인 한정·멱등 + HTTP 본문 크기 상한으로 이미 안전하다(임의 캡으로 정상 요청을 400 으로 막지 않는다).
        const val VALID_SELECTION_MESSAGE = "all=true 또는 ids 중 정확히 하나만 보내야 합니다."
    }
}
