package com.depromeet.piki.product.service.http

import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.HttpURLConnection

// 상품 페이지 fetch 용 RestClient 빈. HttpPageFetcher 안에서 직접 만들면 테스트가 가짜 응답을 끼울 수 없어,
// 생성을 이 Config 로 분리해 주입한다(MockRestServiceServer 로 redirect 루프를 검증하기 위함). 운영 동작은 동일.
@Configuration
class PageFetchHttpClientConfig {
    @Bean
    fun pageFetchRestClient(observationRegistry: ObservationRegistry): RestClient {
        // JDK 레벨 자동 redirect 추적은 끈다(instanceFollowRedirects=false). 자동 추적은 Location 점프 전에 SSRF·도메인
        // 검증을 끼울 수 없어 사설 IP·외부 도메인 우회를 연다. 대신 HttpPageFetcher 가 3xx 를 직접 받아 수동으로 따라간다.
        val requestFactory =
            object : SimpleClientHttpRequestFactory() {
                override fun prepareConnection(
                    connection: HttpURLConnection,
                    httpMethod: String,
                ) {
                    super.prepareConnection(connection, httpMethod)
                    connection.instanceFollowRedirects = false
                }
            }.apply {
                setConnectTimeout(CONNECT_TIMEOUT_MS)
                setReadTimeout(READ_TIMEOUT_MS)
            }
        return RestClient
            .builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,*/*;q=0.8")
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko,en;q=0.9")
            // 상품 페이지 fetch 가 trace 의 한 구간(HTTP client span)으로 잡히게 한다.
            .observationRegistry(observationRegistry)
            .build()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 15_000

        // 기본 RestClient UA 는 일부 사이트에서 차단되므로 실제 브라우저 UA 로 위장.
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
