package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.http.PageFetchException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

// 상품 URL 추출의 공개 진입점(ProductLinkExtractor). 두 전략을 "우리 먼저, 막히면 헤드리스" 공식으로 엮는다:
//   1) plain(DefaultProductLinkExtractor)   : 정적 HTTP + 구조화/LLM. 싸고 빠른 기본 경로.
//   2) headless(HeadlessProductLinkExtractor): 차단 우회 브라우저. 비싸고 느려, plain 이 "차단" 으로 막힌 경우에만 탄다.
//
// 이 클래스는 seam(골격)이다 — 헤드리스 소비자(piki-scraper)가 아직 없으므로 product.extract.headless.enabled 기본값이
// false 이고, 그동안은 plain 에 그대로 위임해 현재 동작과 100% 동일하다(behavior-neutral). 소비자가 붙으면
// HeadlessProductLinkExtractor 를 구현하고 이 플래그만 켜면 된다.
//
// 중요: 에스컬레이션 축(plain 차단 → headless)은 outbox 재시도 축(일시 오류 → 같은 plain 재시도, #461)과 직교한다.
// 차단은 재시도 축에서 이미 "즉시 FAILED(영구)" 라 그 슬롯(attemptCount)에 얹을 수 없다. 그래서 여기서 별도로 판정한다.
@Component
class FallbackProductLinkExtractor(
    @Qualifier(LinkExtractionStrategy.PLAIN) private val plain: LinkExtractionStrategy,
    @Qualifier(LinkExtractionStrategy.HEADLESS) private val headless: LinkExtractionStrategy,
    private val meterRegistry: MeterRegistry,
    private val headlessProperties: HeadlessExtractionProperties,
) : ProductLinkExtractor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun extract(link: ProductLink): ProductSnapshot {
        // 헤드리스가 꺼져 있으면(기본값) plain 에 그대로 위임한다 — 결과도 예외도 그대로 전파돼 현재 동작과 동일하다.
        if (!headlessProperties.enabled) return plain.extract(link)

        // (확장 지점) 호출량 x 느린-실패 낭비가 큰 host 는 여기서 plain 을 건너뛰고 headless 로 직행하되, 소량(canary)만
        // plain 으로 흘려 차단 해제를 감시한다. host 별 정책·canary 비율은 헤드리스 구현·운영 메트릭이 확정할 때 붙인다.

        return try {
            plain.extract(link)
        } catch (e: Exception) {
            if (!shouldEscalate(e)) throw e
            log.info("extract escalate=headless cause={} url={}", e::class.simpleName, link.safeLogString())
            // product.extract 카운터는 {via, reason} 두 라벨 키를 모든 경로에서 동일하게 유지한다(라벨 키 집합이
            // 갈라지면 Prometheus 가 뒤 시계열을 조용히 드롭). plain 성공은 DefaultProductLinkExtractor 가 via=structured/llm 로
            // 이미 세고, 차단 fetch 실패는 파서 도달 전 throw 라 카운터를 안 올리므로, 여기 via=headless 는 이중 집계가 아니다.
            meterRegistry.counter(EXTRACT_METRIC, TAG_VIA, VIA_HEADLESS, TAG_REASON, REASON_ESCALATED).increment()
            headless.extract(link)
        }
    }

    // plain 실패가 "차단이라 headless 면 뚫릴 수 있음" 인지 판정한다. 어떤 fetch 실패가 그런지는 PageFetchException.escalatable
    // 이 단일 진실이고(던지는 지점이 표시), 여기선 그 플래그만 본다 — 집합은 provisional. SSRF 로 우리가 막은
    // host(blockedHost)는 escalatable=false 라 절대 여기로 오지 않는다(뚫어선 안 되는 내부망).
    private fun shouldEscalate(e: Throwable): Boolean = (e as? PageFetchException)?.escalatable == true

    companion object {
        private const val EXTRACT_METRIC = "product.extract"
        private const val TAG_VIA = "via"
        private const val TAG_REASON = "reason"
        private const val VIA_HEADLESS = "headless"
        private const val REASON_ESCALATED = "escalated"
    }
}
