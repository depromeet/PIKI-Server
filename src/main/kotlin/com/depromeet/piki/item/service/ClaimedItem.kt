package com.depromeet.piki.item.service

import com.depromeet.piki.product.domain.ProductLink

// 디스패처가 PENDING → PROCESSING 으로 claim 한 작업. claim 트랜잭션 안에서 link 를 꺼내 두어,
// 트랜잭션 밖에서 도는 워커가 detached 엔티티를 다시 만지지 않고 link 만으로 파싱하게 한다.
data class ClaimedItem(
    val itemId: Long,
    val link: ProductLink,
)
