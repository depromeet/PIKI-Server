package com.depromeet.piki.announcement.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// 공지 본문 이미지 rehost(외부 URL → 우리 S3, #561) 실패 예외.
// 운영자가 붙여넣은 이미지 주소가 원인이라(멀쩡한 호출이 정상 작성으로 도달 가능) 커스텀 예외로 둔다.
// message 는 운영자 대면 고정 문구 — SSRF 차단 사유 등 내부 정보를 노출하지 않는다(구체 사유는 로그로).
class AnnouncementImageException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        // 지원 목록(png·jpeg·gif·webp) 밖의 형식.
        fun unsupportedType(): AnnouncementImageException =
            AnnouncementImageException(
                "지원하지 않는 이미지 형식입니다. (png·jpg·gif·webp 만 가능)",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 선언한 형식과 실제 바이트(매직바이트)가 다르거나 깨진 파일.
        fun malformed(): AnnouncementImageException =
            AnnouncementImageException(
                "이미지 파일이 올바르지 않습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 허용 용량 초과.
        fun tooLarge(): AnnouncementImageException =
            AnnouncementImageException(
                "이미지 용량이 너무 큽니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 외부 주소에서 이미지를 가져오지 못함(네트워크·404·이미지 아님 등). 구체 사유는 로그로 남긴다.
        fun fetchFailed(): AnnouncementImageException =
            AnnouncementImageException(
                "이미지 주소에서 이미지를 가져오지 못했습니다. 주소를 확인해주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // SSRF 방어로 차단한 주소(https 아님 · 사설/내부 IP). 차단 사유는 노출하지 않는다(로그로만).
        fun blockedUrl(): AnnouncementImageException =
            AnnouncementImageException(
                "허용되지 않는 이미지 주소입니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
