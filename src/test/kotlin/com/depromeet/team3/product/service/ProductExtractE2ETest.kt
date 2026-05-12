package com.depromeet.team3.product.service

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.service.gemini.GeminiProductExtractor
import com.depromeet.team3.product.service.gemini.GeminiProperties
import com.depromeet.team3.product.service.http.HttpPageFetcher
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.concurrent.TimeUnit

/**
 * 실제 Gemini API + 외부 쇼핑몰 fetch 를 호출하는 E2E 측정 테스트.
 *
 * 비용·외부 의존성·quota 가 발생하므로 기본은 @Disabled. 측정이 필요할 때만 명시적으로 enable.
 * GEMINI_API_KEY 가 OS env 또는 .env 에 있다고 가정한다.
 */
@Disabled("실제 Gemini API 호출 + 외부 쇼핑몰 fetch. 측정 필요 시 수동으로 enable 후 실행.")
class ProductExtractE2ETest {
    private val pageFetcher = HttpPageFetcher()
    private val objectMapper = jacksonObjectMapper()
    private val properties =
        GeminiProperties(
            apiKey = System.getenv("GEMINI_API_KEY"),
            model = "gemini-3-flash-preview",
        )
    private val extractor =
        GeminiProductExtractor(
            objectMapper = objectMapper,
            geminiProperties = properties,
            pageFetcher = pageFetcher,
        )

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    fun `여러 URL 을 N회씩 호출해 latency 및 추출 결과를 누적 측정한다`() {
        val results = mutableListOf<Outcome>()
        URLS.forEach { rawUrl ->
            repeat(ITERATIONS) { i ->
                val link = ProductLink.parse(rawUrl)
                val started = System.nanoTime()
                val outcome =
                    try {
                        val product = extractor.extract(link)
                        val ms = (System.nanoTime() - started) / 1_000_000
                        Outcome(
                            url = rawUrl,
                            attempt = i + 1,
                            status = "OK",
                            name = product.name,
                            currentPrice = product.currentPrice,
                            elapsedMs = ms,
                            error = null,
                        )
                    } catch (e: Exception) {
                        val ms = (System.nanoTime() - started) / 1_000_000
                        Outcome(
                            url = rawUrl,
                            attempt = i + 1,
                            status = "FAIL",
                            name = null,
                            currentPrice = null,
                            elapsedMs = ms,
                            error = "${e.javaClass.simpleName}: ${e.message}",
                        )
                    }
                results += outcome
                println(formatLine(outcome))
            }
        }
        printSummary(results)
    }

    private fun formatLine(o: Outcome): String =
        if (o.status == "OK") {
            "[${o.attempt}/$ITERATIONS] ${o.elapsedMs}ms  ${o.url}  name=${o.name}  price=${o.currentPrice}"
        } else {
            "[${o.attempt}/$ITERATIONS] ${o.elapsedMs}ms  ${o.url}  FAIL  ${o.error}"
        }

    private fun printSummary(results: List<Outcome>) {
        println("\n=== summary ===")
        results.groupBy { it.url }.forEach { (url, rs) ->
            val ok = rs.count { it.status == "OK" }
            val avg = rs.map { it.elapsedMs }.average().toInt()
            val prices = rs.filter { it.status == "OK" }.map { it.currentPrice }.distinct()
            println("$url: $ok/${rs.size} OK  avg=${avg}ms  uniquePrice=$prices")
        }
    }

    data class Outcome(
        val url: String,
        val attempt: Int,
        val status: String,
        val name: String?,
        val currentPrice: Int?,
        val elapsedMs: Long,
        val error: String?,
    )

    companion object {
        private const val ITERATIONS = 3

        // SSR / CSR / JSON-LD 보유 / 보유 안 함 등 다양한 패턴으로 섞었음.
        private val URLS =
            listOf(
                "https://www.musinsa.com/products/1551840",
                "https://www.musinsa.com/products/6029415",
                "https://www.29cm.co.kr/products/3915051",
                "https://www.29cm.co.kr/products/1437795",
            )
    }
}
