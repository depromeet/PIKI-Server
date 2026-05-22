package com.depromeet.team3.ocr.service

import com.depromeet.team3.ocr.domain.BoundingBox
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// 원본 이미지에서 bbox(0~1000 normalized) 영역만 잘라 PNG 바이트로 돌려준다.
// ImageIO 가 디코딩하는 PNG/JPEG 만 지원한다. HEIC/WebP 등 디코딩 불가·실패 시 null(크롭 스킵).
@Component
class ImageCropper {
    private val log = LoggerFactory.getLogger(javaClass)

    fun crop(
        bytes: ByteArray,
        boundingBox: BoundingBox,
    ): ByteArray? {
        // ImageIO.read 는 디코더가 없으면(HEIC 등) null 을 반환한다 — 예외가 아니라 null 분기.
        val source = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null

        val cropX = scale(boundingBox.xMin, source.width)
        val cropY = scale(boundingBox.yMin, source.height)
        // 끝 좌표는 올림이 아니라 폭/높이로 환산하고, 원본 경계를 넘지 않게 클램핑한다.
        val cropWidth = (scale(boundingBox.xMax, source.width) - cropX).coerceAtLeast(1).coerceAtMost(source.width - cropX)
        val cropHeight = (scale(boundingBox.yMax, source.height) - cropY).coerceAtLeast(1).coerceAtMost(source.height - cropY)

        return runCatching {
            val cropped = source.getSubimage(cropX, cropY, cropWidth, cropHeight)
            ByteArrayOutputStream().use { out ->
                ImageIO.write(cropped, "png", out)
                out.toByteArray()
            }
        }.getOrElse { e ->
            log.warn("이미지 크롭 실패: {}", e.message)
            null
        }
    }

    // normalized(0~1000) 좌표를 픽셀로 환산하고 [0, dimension) 으로 클램핑한다.
    private fun scale(
        normalized: Int,
        dimension: Int,
    ): Int = (normalized.toLong() * dimension / BoundingBox.NORMALIZED_MAX).toInt().coerceIn(0, dimension - 1)
}
