package com.depromeet.piki.common.imageproxy

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.HttpURLConnection
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

    fun fetch(url: String): FetchedImage {
        validateDomain(url)
        return try {
            val response: ResponseEntity<ByteArray> =
                client
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .onStatus({ it.isError || it.is3xxRedirection }) { _, res ->
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
