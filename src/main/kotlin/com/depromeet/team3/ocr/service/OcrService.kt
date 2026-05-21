package com.depromeet.team3.ocr.service

import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.ocr.domain.OcrImage
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class OcrService(
    private val productImageExtractor: ProductImageExtractor,
) {
    // 입력 검증(빈 파일 · MIME 타입)은 OcrImage.of 가 담당한다. 서비스는 위임만 한다.
    fun extract(image: MultipartFile): Product = productImageExtractor.extract(OcrImage.of(image.bytes, image.contentType))
}
