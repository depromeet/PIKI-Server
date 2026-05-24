package com.depromeet.piki.ocr.service

import com.depromeet.piki.ocr.domain.BoundingBox
import com.depromeet.piki.product.service.ProductSnapshot

// OCR 추출 결과. 상품 정보(snapshot)와 크롭에 쓸 상품 영역(boundingBox)을 함께 담는다.
// boundingBox 는 박스를 못 잡았거나 비정상이면 null — 이 경우 크롭을 건너뛰고 imageUrl 은 비운다.
data class OcrExtraction(
    val snapshot: ProductSnapshot,
    val boundingBox: BoundingBox?,
)
