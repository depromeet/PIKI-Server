package com.depromeet.piki.tournament.controller.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class JoinTournamentAsGuestRequest(
    @field:Pattern(regexp = "[A-Z]{3}\\d{3}", message = INVITE_CODE_PATTERN_MESSAGE)
    val inviteCode: String?,
    @field:NotBlank(message = NICKNAME_BLANK_MESSAGE)
    @field:Size(max = 10, message = NICKNAME_MAX_MESSAGE)
    val nickname: String,
) {
    // Bean Validation 위반 메시지의 single source. OpenAPI example(TournamentApiExamples)이 같은 상수를 참조해
    // "필드 검증 문구가 @field 와 example 두 곳에서 따로 노는" 어긋남을 컴파일 타임에 막는다.
    companion object {
        const val INVITE_CODE_PATTERN_MESSAGE = "초대 코드 형식이 올바르지 않아요."
        const val NICKNAME_BLANK_MESSAGE = "닉네임을 입력해 주세요."
        const val NICKNAME_MAX_MESSAGE = "닉네임은 10자까지 입력할 수 있어요."
    }
}
