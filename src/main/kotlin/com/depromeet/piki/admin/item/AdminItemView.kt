package com.depromeet.piki.admin.item

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemStatus

// 백오피스 목록 행 — item(정체성)과 그 최신 snapshot(추출값·상태)을 묶은 뷰 모델.
// 표시값(name·price·currency·status)은 snapshot 에서, 정체성(id·sourceUrl)은 item 에서 온다.
// snapshot 이 없으면(이론상 닿지 않음) 표시값은 비운다.
data class AdminItemView(
    val id: Long?,
    val sourceUrl: String?,
    val name: String?,
    val currentPrice: Int?,
    val currency: String?,
    val status: ItemStatus?,
) {
    companion object {
        fun from(
            item: Item,
            snapshot: ItemSnapshot?,
        ): AdminItemView =
            AdminItemView(
                id = item.getIdOrNull(),
                sourceUrl = item.link?.toString(),
                name = snapshot?.name,
                currentPrice = snapshot?.currentPrice,
                currency = snapshot?.currency,
                status = snapshot?.status,
            )
    }
}
