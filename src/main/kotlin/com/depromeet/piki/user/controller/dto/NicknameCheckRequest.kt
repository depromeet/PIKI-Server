package com.depromeet.piki.user.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "닉네임 중복 체크 요청 (query parameter)")
data class NicknameCheckRequest(
    @field:NotBlank(message = NICKNAME_BLANK_MESSAGE)
    @field:Size(max = 10, message = NICKNAME_SIZE_MESSAGE)
    @field:Schema(description = "확인할 닉네임 (최대 10자)", example = "새닉네임")
    val nickname: String,
) {
    // Bean Validation 위반 메시지의 single source. OpenAPI example(UserApiExamples)이 같은 상수를 참조해
    // "필드 검증 문구가 @field 와 example 두 곳에서 따로 노는" 어긋남을 컴파일 타임에 막는다.
    companion object {
        const val NICKNAME_BLANK_MESSAGE = "nickname 은 비어 있거나 공백만으로 구성될 수 없다."
        const val NICKNAME_SIZE_MESSAGE = "nickname 은 10자 이하여야 한다."
    }
}
