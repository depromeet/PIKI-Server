package com.depromeet.team3.common.domain

data class Product(
    val name: Field<String>,
    val price: Field<Int>,
    val category: Field<String>,
) {
    /**
     * OCR로 추출된 필드의 상태.
     *
     * - [NotFound]: 이미지에서 추출 실패
     * - [Inferred]: 값은 있지만 이미지에 명시되어 있지 않아 추론한 결과 (boundingBox 없음)
     * - [Extracted]: 이미지에서 직접 읽어낸 값 (boundingBox 보장)
     */
    sealed interface Field<out T> {
        data object NotFound : Field<Nothing>

        data class Inferred<T>(
            val value: T,
        ) : Field<T>

        data class Extracted<T>(
            val value: T,
            val boundingBox: BoundingBox,
        ) : Field<T>
    }
}
