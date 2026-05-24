package com.depromeet.piki.ocr.domain

// Gemini 가 반환하는 상품 영역 박스. 좌표는 0~1000 normalized (Gemini 표준 [ymin, xmin, ymax, xmax]).
// 실제 픽셀 환산·크롭은 ImageCropper 가 원본 이미지 크기에 맞춰 수행한다.
data class BoundingBox(
    val yMin: Int,
    val xMin: Int,
    val yMax: Int,
    val xMax: Int,
) {
    companion object {
        const val NORMALIZED_MAX = 1000

        // 네 좌표가 모두 있고 0~1000 범위에 정상 순서(min < max)일 때만 만든다.
        // 하나라도 빠지거나 비정상이면 null — 크롭을 건너뛰는 신호다.
        fun ofNormalizedOrNull(
            yMin: Int?,
            xMin: Int?,
            yMax: Int?,
            xMax: Int?,
        ): BoundingBox? {
            val y0 = yMin ?: return null
            val x0 = xMin ?: return null
            val y1 = yMax ?: return null
            val x1 = xMax ?: return null
            val range = 0..NORMALIZED_MAX
            if (y0 !in range || x0 !in range || y1 !in range || x1 !in range) return null
            if (y1 <= y0 || x1 <= x0) return null
            return BoundingBox(yMin = y0, xMin = x0, yMax = y1, xMax = x1)
        }
    }
}
