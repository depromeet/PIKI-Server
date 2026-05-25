package com.depromeet.piki.product.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = false)
class ProductLinkConverter : AttributeConverter<ProductLink, String> {
    override fun convertToDatabaseColumn(attribute: ProductLink?): String? = attribute?.toString()

    // 읽기 시점에 parse 실패가 그대로 IllegalArgumentException 으로 새면
    // 한 행이 깨졌을 때 리스트/페이징 쿼리 전체가 500 으로 떨어진다.
    // 도메인 예외로 감싸 호출 측이 row 단위로 식별·격리할 수 있도록 한다.
    override fun convertToEntityAttribute(dbData: String?): ProductLink? =
        dbData?.let {
            runCatching { ProductLink.parse(it) }
                .getOrElse { e ->
                    throw IllegalStateException("저장된 product link 가 정책에 어긋납니다: $it", e)
                }
        }
}
