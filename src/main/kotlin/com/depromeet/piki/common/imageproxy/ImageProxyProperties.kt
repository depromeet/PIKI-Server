package com.depromeet.piki.common.imageproxy

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "image.proxy")
data class ImageProxyProperties(
    val allowedDomains: List<String>,
    val maxBytes: Long = 5 * 1024 * 1024,
    val timeoutSeconds: Long = 10,
)
