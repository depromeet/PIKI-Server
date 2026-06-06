package com.depromeet.piki.item.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.domain.ProductLinkConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table

// 상품의 정체성. link(외부 URL)로 식별되며, 추출값(name·price·image·currency)·상태·이력은 전적으로
// ItemSnapshot(버전)이 보유한다 — item 자신은 추출 데이터를 들지 않는 안정적 식별 단위다(4a 에서 추출 필드를 분리).
//
// wish 와 tournament_item 이 함께 참조하는 공유 엔티티. 같은 link 라도 위시·출전마다 1:1 독립 행이라,
// 한쪽의 수정이 다른 쪽에 번지지 않는다(수정 격리). link 는 바뀌면 사실상 다른 상품이라 재등록 영역으로 보고
// 불변(val). 단 이미지 등록 경로는 URL 이 없어 link 가 null 일 수 있다 (URL 추출 / 이미지 추출 두 경로가 같은 item 을 만든다).
@Entity
@Table(name = "items")
class Item(
    @Convert(converter = ProductLinkConverter::class)
    @Column(name = "source_url", nullable = true, length = 2048)
    val link: ProductLink? = null,
) : LongBaseEntity()
