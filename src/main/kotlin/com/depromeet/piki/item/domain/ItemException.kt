package com.depromeet.piki.item.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// 도메인 예외지만 HttpMappable 로 status·category 를 직접 들고 있다. 도메인이 전송 계층(HTTP)을 아는
// 형태는 순수 DDD 에선 피하지만, "사유 + status" 를 예외 정의 한 곳에서 보는 응집도를 위해 의식적으로
// 택한 트레이드오프다 (WishException 과 동일). status 매핑을 핸들러로 분리하는 대안은 #181 에서 검토.
class ItemException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        // 등록 완료(READY)된 item 은 링크에서 기계 추출한 사실이라 클라이언트가 직접 수정할 수 없다.
        // 갱신은 서버 재추출 경로로만 들어오고, 클라이언트 보정은 추출 실패(FAILED) 항목에 한정된다.
        fun alreadyReady(): ItemException =
            ItemException(
                "이미 등록 완료된 상품은 수정할 수 없습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )

        // 파싱 중(PROCESSING)인 item 은 백그라운드 워커가 결과를 채우는 중이라 클라이언트가 끼어들 수 없다.
        fun stillProcessing(): ItemException =
            ItemException(
                "아직 처리 중인 상품은 수정할 수 없습니다.",
                ErrorCategory.CONFLICT,
                HttpStatus.CONFLICT,
            )
    }
}
