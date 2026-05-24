package com.depromeet.piki.ocr.service

import com.depromeet.piki.ocr.domain.BoundingBox
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageCropperTest {
    private val cropper = ImageCropper()

    private fun pngBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }

    @Test
    fun `정상 bbox 로 크롭하면 normalized 비율만큼의 PNG 를 돌려준다`() {
        val bytes = pngBytes(1000, 1000)
        // normalized 100~500 → 1000px 기준 100~500px → 400x400
        val cropped = cropper.crop(bytes, BoundingBox(yMin = 100, xMin = 100, yMax = 500, xMax = 500))

        assertNotNull(cropped)
        val image = ImageIO.read(ByteArrayInputStream(cropped))
        assertEquals(400, image.width)
        assertEquals(400, image.height)
    }

    @Test
    fun `디코딩 불가한 바이트(미지원 포맷 등)는 null`() {
        assertNull(cropper.crop(byteArrayOf(1, 2, 3), BoundingBox(yMin = 100, xMin = 100, yMax = 500, xMax = 500)))
    }

    @Test
    fun `원본 경계를 넘는 끝 좌표는 클램핑되어 크롭에 성공한다`() {
        val bytes = pngBytes(1000, 1000)
        // xMax/yMax=1000 → 픽셀 환산 시 경계를 넘지만 클램핑으로 크롭 성공
        val cropped = cropper.crop(bytes, BoundingBox(yMin = 900, xMin = 900, yMax = 1000, xMax = 1000))

        assertNotNull(cropped)
    }
}
