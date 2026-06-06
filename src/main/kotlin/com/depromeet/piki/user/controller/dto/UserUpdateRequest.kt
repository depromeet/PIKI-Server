package com.depromeet.piki.user.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "유저 정보 수정 요청")
data class UserUpdateRequest(
    // 게스트도 호출 가능한 PATCH 라 모든 필드 nullable. 추후 회원 전용 필드 추가 시에도 같은 PATCH 가
    // 자기 권한 안의 필드만 수정하게 된다.
    @field:Size(min = 1, max = 10, message = NICKNAME_SIZE_MESSAGE)
    @field:Schema(description = "변경할 닉네임 (선택, 최대 10자)", example = "새닉네임", nullable = true)
    val nickname: String?,
) {
    // Bean Validation 위반 메시지의 single source. OpenAPI example(UserApiExamples)이 같은 상수를 참조해
    // "필드 검증 문구가 @field 와 example 두 곳에서 따로 노는" 어긋남을 컴파일 타임에 막는다.
    companion object {
        const val NICKNAME_SIZE_MESSAGE = "닉네임은 1자 이상 10자 이하여야 한다."
    }
}
