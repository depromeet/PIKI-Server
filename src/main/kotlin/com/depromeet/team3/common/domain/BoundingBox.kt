package com.depromeet.team3.common.domain

/**
 * 원본 이미지의 실제 픽셀로 변환하려면:
 *   pixelX = xMin / 1000.0 * imageWidth
 *   pixelY = yMin / 1000.0 * imageHeight
 *   pixelWidth = (xMax - xMin) / 1000.0 * imageWidth
 *   pixelHeight = (yMax - yMin) / 1000.0 * imageHeight
 */
data class BoundingBox(
    val yMin: Int,
    val xMin: Int,
    val yMax: Int,
    val xMax: Int,
) {
    init {
        // Gemini 공식 문서 기준 좌표는 0..1000 범위로 정규화됨.
        // https://ai.google.dev/gemini-api/docs/bounding-boxes
        require(listOf(yMin, xMin, yMax, xMax).all { it in VALID_RANGE }) {
            "BoundingBox 좌표는 $VALID_RANGE 범위여야 합니다: " +
                "(yMin=$yMin, xMin=$xMin, yMax=$yMax, xMax=$xMax)"
        }
        require(yMin <= yMax && xMin <= xMax) {
            "BoundingBox 최소 좌표는 최대 좌표보다 클 수 없습니다: " +
                "(yMin=$yMin, xMin=$xMin, yMax=$yMax, xMax=$xMax)"
        }
    }

    companion object {
        private val VALID_RANGE = 0..1000

        /**
         * 외부 입력(Gemini 응답 등) 으로부터 BoundingBox 를 생성할 때 사용한다.
         *
         * - 필드 중 하나라도 null → null
         * - 범위 위반(0..1000 밖) → null
         * - 순서 위반(min > max) → null
         *
         * 실패 시 호출자가 Field.Inferred 로 graceful fallback 할 수 있다.
         * 생성자 직접 호출 경로(내부 코드) 는 여전히 init 의 require 로 엄격히 보호된다.
         */
        fun ofOrNull(
            yMin: Int?,
            xMin: Int?,
            yMax: Int?,
            xMax: Int?,
        ): BoundingBox? {
            val y1 = yMin ?: return null
            val x1 = xMin ?: return null
            val y2 = yMax ?: return null
            val x2 = xMax ?: return null
            if (listOf(y1, x1, y2, x2).any { it !in VALID_RANGE }) return null
            if (y1 > y2 || x1 > x2) return null
            return BoundingBox(yMin = y1, xMin = x1, yMax = y2, xMax = x2)
        }
    }
}
