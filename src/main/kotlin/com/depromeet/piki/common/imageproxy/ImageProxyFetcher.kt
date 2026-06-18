package com.depromeet.piki.common.imageproxy

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI
import java.time.Duration

@Component
class ImageProxyFetcher(
    private val properties: ImageProxyProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(properties.timeoutSeconds))
                    setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds))
                },
            ).build()

    fun fetch(url: String): FetchedImage {
        validateDomain(url)
        return try {
            val response: ResponseEntity<ByteArray> =
                client
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .onStatus({ it.isError }) { _, res ->
                        log.warn("image-proxy fetch 실패: status={}", res.statusCode)
                        throw ImageProxyException.fetchFailed()
                    }.toEntity(ByteArray::class.java)

            val bytes = response.body ?: throw ImageProxyException.fetchFailed()
            if (bytes.size > properties.maxBytes) throw ImageProxyException.imageTooLarge()
            val contentType = response.headers.contentType?.toString() ?: "image/jpeg"
            FetchedImage(bytes = bytes, contentType = contentType)
        } catch (e: ImageProxyException) {
            throw e
        } catch (e: Exception) {
            log.warn("image-proxy 네트워크 오류: {}", e.message)
            throw ImageProxyException.fetchFailed()
        }
    }

    private fun validateDomain(url: String) {
        val host =
            try {
                val uri = URI.create(url)
                if (uri.scheme != "https" && uri.scheme != "http") throw ImageProxyException.blockedDomain()
                uri.host ?: throw ImageProxyException.blockedDomain()
            } catch (e: IllegalArgumentException) {
                throw ImageProxyException.blockedDomain()
            }
        if (properties.allowedDomains.none { host == it || host.endsWith(".$it") }) {
            throw ImageProxyException.blockedDomain()
        }
    }
}

data class FetchedImage(
    val bytes: ByteArray,
    val contentType: String,
)
