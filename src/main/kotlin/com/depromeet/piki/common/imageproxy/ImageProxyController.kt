package com.depromeet.piki.common.imageproxy

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class ImageProxyController(
    private val imageProxyFetcher: ImageProxyFetcher,
) : ImageProxyApi {
    @GetMapping("/image-proxy")
    override fun proxyImage(
        @RequestParam url: String,
    ): ResponseEntity<ByteArray> {
        val image = imageProxyFetcher.fetch(url)
        return ResponseEntity
            .ok()
            .header("Content-Type", image.contentType)
            .header("Cache-Control", "public, max-age=86400")
            .body(image.bytes)
    }
}
