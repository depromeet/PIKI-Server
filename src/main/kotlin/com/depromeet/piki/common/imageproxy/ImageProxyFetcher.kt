package com.depromeet.piki.common.imageproxy

import org.slf4j.LoggerFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration

interface ImageProxyFetcher {
    fun fetch(url: String): FetchedImage
}

@Component
class DefaultImageProxyFetcher(
    private val properties: ImageProxyProperties,
) : ImageProxyFetcher {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client =
        org.springframework.web.client.RestClient
            .builder()
            .requestFactory(
                // 리다이렉트 자동 추적 차단 — 화이트리스트 도메인에서 내부망(IMDS 등)으로 유도하는 SSRF 방어.
                // 3xx 는 onStatus 에서 fetchFailed() 로 즉시 실패 처리한다.
                object : SimpleClientHttpRequestFactory() {
                    init {
                        setConnectTimeout(Duration.ofSeconds(properties.timeoutSeconds))
                        setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds))
                    }

                    override fun prepareConnection(
                        connection: HttpURLConnection,
                        httpMethod: String,
                    ) {
                        super.prepareConnection(connection, httpMethod)
                        connection.instanceFollowRedirects = false
                    }
                },
            ).build()

    override fun fetch(url: String): FetchedImage {
        validateDomain(url)
        return try {
            client
                .get()
                .uri(URI.create(url))
                .exchange { _, response ->
                    if (response.statusCode.isError || response.statusCode.is3xxRedirection) {
                        log.warn("image-proxy fetch 실패: status={}", response.statusCode)
                        throw ImageProxyException.fetchFailed()
                    }
                    val contentType =
                        response.headers.contentType?.takeIf { it.type == "image" }
                            ?: run {
                                log.warn("image-proxy 잘못된 Content-Type: {}", response.headers.contentType)
                                throw ImageProxyException.fetchFailed()
                            }
                    val bytes = readWithLimit(response.body, properties.maxBytes)
                    FetchedImage(bytes = bytes, contentType = contentType.toString())
                }
        } catch (e: ImageProxyException) {
            throw e
        } catch (e: Exception) {
            log.warn("image-proxy 네트워크 오류: {}", e.message)
            throw ImageProxyException.fetchFailed()
        }
    }

    private fun readWithLimit(stream: InputStream, maxBytes: Long): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var totalRead = 0L
        stream.use {
            var n: Int
            while (it.read(chunk).also { n = it } != -1) {
                totalRead += n
                if (totalRead > maxBytes) throw ImageProxyException.imageTooLarge()
                buffer.write(chunk, 0, n)
            }
        }
        return buffer.toByteArray()
    }

    private fun validateDomain(url: String) {
        val host =
            try {
                val uri = URI.create(url)
                if (uri.scheme != "https") throw ImageProxyException.blockedDomain()
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
