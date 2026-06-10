package com.depromeet.piki.auth.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "개발용 MEMBER 생성 요청")
data class DevUserCreateRequest(
    @field:NotBlank(message = NICKNAME_REQUIRED_MESSAGE)
    @field:Schema(description = "닉네임", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    val nickname: String,
) {
    // Bean Validation 위반 메시지의 single source. OpenAPI example(AuthApiExamples)이 같은 상수를 참조해
    // "필드 검증 문구가 @field 와 example 두 곳에서 따로 노는" 어긋남을 컴파일 타임에 막는다.
    companion object {
        const val NICKNAME_REQUIRED_MESSAGE = "닉네임은 필수입니다."
    }
}
