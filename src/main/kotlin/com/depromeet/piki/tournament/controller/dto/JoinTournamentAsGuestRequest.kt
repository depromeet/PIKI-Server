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
        const val INVITE_CODE_PATTERN_MESSAGE = "초대 코드는 영어 대문자 3자리 + 숫자 3자리 형식이어야 합니다."
        const val NICKNAME_BLANK_MESSAGE = "닉네임은 비어 있을 수 없습니다."
        const val NICKNAME_MAX_MESSAGE = "닉네임은 10자 이하여야 합니다."
    }
}
