package com.depromeet.piki.product.service.structured

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.PageContent
import com.depromeet.piki.product.service.ProductSnapshot
import java.math.RoundingMode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

// fetch 된 HTML 의 구조화 데이터(JSON-LD schema.org/Product · OpenGraph)를 코드로 파싱해
// LLM 호출 없이 ProductSnapshot 을 만든다. 필수 필드(name+currentPrice)가 검증을 통과하면 Extracted,
// 미달·부재·검증위반이면 사유를 담은 Miss 를 돌려 오케스트레이터가 Gemini fallback 으로 넘어가게 한다.
//
// jsoup 은 마크업에서 <script ld+json>·<meta og:*> 블록을 정확히 꺼내는 책임만 지고, JSON-LD 값 자체는
// Jackson 3 트리(JsonNode)로 다룬다. 깨진 ld+json 한 덩어리가 전체를 죽이지 않도록 각 script 를 runCatching 으로 격리한다.
@Component
class StructuredDataExtractor(
    private val objectMapper: ObjectMapper,
) {
    // 오케스트레이터가 파싱한 Document 를 공유받아 읽기만 한다(Document 를 변형하지 않으므로 이후 Gemini fallback 과 안전하게 공유).
    // 우선순위: JSON-LD(구조적 가격을 정확히 들고 있음) > OpenGraph(가격 표준 태그가 없어 보조).
    // 둘 다 실패하면 더 데이터에 근접했던 사유(worse)를 reason 으로 보고한다.
    fun extract(
        document: Document,
        link: ProductLink,
    ): StructuredExtraction =
        when (val fromJsonLd = fromJsonLd(document, link)) {
            is StructuredExtraction.Extracted -> fromJsonLd
            is StructuredExtraction.Miss ->
                when (val fromOpenGraph = fromOpenGraph(document, link)) {
                    is StructuredExtraction.Extracted -> fromOpenGraph
                    is StructuredExtraction.Miss -> worse(fromJsonLd, fromOpenGraph)
                }
        }

    // 단독 호출·테스트 편의: HTML 을 직접 파싱해 위임한다. 운영 경로는 오케스트레이터가 Document 를 만들어 공유한다.
    // baseUri 는 html 의 출처인 최종 URL(finalUrl) 기준, 정체성으로 넘기는 link 는 원본 유지.
    fun extract(page: PageContent): StructuredExtraction = extract(Jsoup.parse(page.html, page.finalUrl.value.toString()), page.link)

    // 두 실패 사유 중 데이터에 더 근접한(=정보량이 큰) 쪽. enum 선언 순서가 아니라 명시 rank 로 비교한다(선언 순서 변경에 독립).
    private fun worse(
        a: StructuredExtraction.Miss,
        b: StructuredExtraction.Miss,
    ): StructuredExtraction.Miss = if (a.rank >= b.rank) a else b

    // --- JSON-LD (schema.org/Product) ---

    private fun fromJsonLd(
        document: Document,
        link: ProductLink,
    ): StructuredExtraction {
        val products =
            document
                // type 속성에 charset 파라미터·따옴표·공백 변형이 붙어도 application/ld+json 으로 인식한다(jsoup 이 파싱한 attr 기준).
                .select("script[type]")
                .filter { it.attr("type").trim().startsWith("application/ld+json", ignoreCase = true) }
                .flatMap { script ->
                    val root = runCatching { objectMapper.readTree(script.data()) }.getOrNull()
                    root?.let { collectProductNodes(it) } ?: emptyList()
                }
        // Product 노드가 하나도 없으면 구조화 데이터 부재.
        if (products.isEmpty()) return StructuredExtraction.Miss.NO_DATA

        // 시드는 최저 rank(NO_DATA)에서 시작해 노드 결과로 올린다. 노드가 있으므로 toSnapshotFromProduct 는
        // 항상 MISSING_FIELD 이상(또는 Extracted)을 주어, 첫 반복에서 곧바로 실제 사유로 덮인다.
        var worst: StructuredExtraction.Miss = StructuredExtraction.Miss.NO_DATA
        // 앞 Product 가 검증에 실패해도(요약용 불완전 노드 등) 뒤의 완전한 Product 까지 모두 시도한다.
        for (product in products) {
            when (val result = toSnapshotFromProduct(product, link)) {
                is StructuredExtraction.Extracted -> return result
                is StructuredExtraction.Miss -> worst = worse(worst, result)
            }
        }
        return worst
    }

    private fun toSnapshotFromProduct(
        product: JsonNode,
        link: ProductLink,
    ): StructuredExtraction {
        val offer = firstOffer(product)
        return toResult(
            link = link,
            name = textOf(product.get("name")),
            imageUrl = imageUrlOf(product),
            priceText = textOf(priceNode(offer)),
            currency = textOf(offer?.get("priceCurrency")),
        )
    }

    // 최상위 배열 / @graph 래핑 / ItemList.itemListElement[].item 중첩을 재귀로 평탄화해 모든 Product 노드를 모은다.
    // 첫 후보가 불완전해도 뒤 후보로 넘어갈 수 있게 전부 수집한다. JsonNode 순회는 안전한 인덱스 접근(size/get)으로.
    private fun collectProductNodes(node: JsonNode): List<JsonNode> {
        val products = mutableListOf<JsonNode>()
        collectProductNodesInto(node, products)
        return products
    }

    private fun collectProductNodesInto(
        node: JsonNode,
        acc: MutableList<JsonNode>,
    ) {
        if (node.isArray) {
            for (i in 0 until node.size()) collectProductNodesInto(node.get(i), acc)
            return
        }
        node.get("@graph")?.let { collectProductNodesInto(it, acc) }
        node.get("itemListElement")?.let { list ->
            for (i in 0 until list.size()) {
                list.get(i).get("item")?.let { collectProductNodesInto(it, acc) }
            }
        }
        if (isProductType(node)) acc.add(node)
    }

    private fun isProductType(node: JsonNode): Boolean {
        val type = node.get("@type") ?: return false
        if (type.isArray) {
            for (i in 0 until type.size()) {
                if (isProductTypeValue(type.get(i))) return true
            }
            return false
        }
        return isProductTypeValue(type)
    }

    private fun isProductTypeValue(node: JsonNode): Boolean =
        textOf(node)?.equals("Product", ignoreCase = true) ?: false

    // offers 는 객체 또는 배열(AggregateOffer 의 offers 배열 등). 배열이면 첫 원소를 쓴다.
    private fun firstOffer(product: JsonNode): JsonNode? {
        val offers = product.get("offers") ?: return null
        return when {
            offers.isArray -> offers.takeIf { it.size() > 0 }?.get(0)
            else -> offers
        }
    }

    // price 우선순위: offers.price → offers.priceSpecification.price → AggregateOffer.lowPrice.
    private fun priceNode(offer: JsonNode?): JsonNode? {
        offer ?: return null
        offer.get("price")?.let { return it }
        offer.get("priceSpecification")?.get("price")?.let { return it }
        offer.get("lowPrice")?.let { return it }
        return null
    }

    private fun imageUrlOf(product: JsonNode): String? {
        val image = product.get("image") ?: return null
        return firstImageUrl(image)
    }

    // image 는 문자열 URL · ImageObject(url·contentUrl) · 그 배열 중 하나다. 첫 유효 URL 을 뽑는다.
    // schema.org ImageObject 는 url 또는 contentUrl 로 실제 주소를 담으므로(29cm 는 contentUrl) 둘 다 본다.
    private fun firstImageUrl(node: JsonNode): String? =
        when {
            node.isArray -> (0 until node.size()).firstNotNullOfOrNull { firstImageUrl(node.get(it)) }
            node.isObject -> textOf(node.get("url")) ?: textOf(node.get("contentUrl"))
            else -> textOf(node)
        }

    // --- OpenGraph (JSON-LD 가 실패했을 때의 보조 경로) ---

    private fun fromOpenGraph(
        document: Document,
        link: ProductLink,
    ): StructuredExtraction {
        val name = stripSiteSuffix(metaContent(document, "og:title"), metaContent(document, "og:site_name"))
        val imageUrl = metaContent(document, "og:image")
        // 가격은 OG 표준(product:price:amount) 우선, 없으면 embedded JS state 에서 보강한다(resolvePrice).
        val (priceText, currency) = resolvePrice(document)
        // OG 관련 태그(title·price·image·currency)가 하나도 없으면 구조화 데이터 부재. 하나라도 있으면 toResult 가 missing/invalid 를 가린다.
        // currency 도 포함해, 통화 태그만 단독으로 있는 페이지가 no_data 로 오분류되지 않게 한다(부분 제공 → missing_field).
        if (listOfNotNull(name, priceText, imageUrl, currency).isEmpty()) return StructuredExtraction.Miss.NO_DATA
        return toResult(link = link, name = name, imageUrl = imageUrl, priceText = priceText, currency = currency)
    }

    // 가격·통화 해석. OG 표준 가격 태그(product:price:amount)가 있으면 그대로, 없으면 embedded JS state 에서 보강한다.
    // OG 가 이름·이미지는 주지만 가격을 JS state(window.__PRELOADED_STATE__ 등)에만 둔 SPA(예: 유니클로)를 LLM 없이
    // 추출하기 위한 특화 경로다 — 가격이 거대 state 깊숙이 있어 Gemini fallback 의 토큰 상한에 안 맞는 사이트를 파서가 직접 건진다.
    // OG 표준이 있으면 그대로 써 불필요한 state 파싱을 피한다.
    private fun resolvePrice(document: Document): Pair<String?, String?> {
        // currency(product:price:currency)는 amount 유무와 독립으로 읽는다 — 통화 태그만 단독으로 있는 페이지가
        // no_data 로 오분류되지 않게(부분 제공 → missing_field). amount 가 있으면 그대로, 없으면 embedded state 로 보강.
        val ogCurrency = metaContent(document, "product:price:currency")
        metaContent(document, "product:price:amount")?.let { return it to ogCurrency }
        val embedded = priceFromEmbeddedState(document) ?: return null to ogCurrency
        return embedded.first to (embedded.second ?: ogCurrency)
    }

    // embedded JS state 의 JSON 에서 (가격, 통화)를 찾는다. 유니클로식 가격 컨테이너
    // "prices":{"base":{"value":N,"currency":{"code":C}}} 를 트리에서 탐색한다.
    private fun priceFromEmbeddedState(document: Document): Pair<String, String?>? {
        val state = embeddedStateJson(document) ?: return null
        val prices = findPricesNode(state) ?: return null
        val base = prices.path("base")
        val value = base.path("value").takeIf { it.isNumber }?.asString() ?: return null
        val currency = base.path("currency").path("code").takeIf { it.isString }?.asString()
        return value to currency
    }

    // 두 형태의 embedded state 를 JsonNode 로 읽는다: <script id="__NEXT_DATA__" type="application/json"> 의 순수 JSON,
    // 또는 window.__PRELOADED_STATE__ = {...} JS 할당. 후자는 할당 뒤에 코드가 붙을 수 있어 균형 중괄호로 객체만 떼낸다.
    private fun embeddedStateJson(document: Document): JsonNode? {
        document
            .selectFirst("script#__NEXT_DATA__")
            ?.data()
            ?.let { runCatching { objectMapper.readTree(it) }.getOrNull() }
            ?.let { return it }
        val script = document.select("script").firstOrNull { it.data().contains("__PRELOADED_STATE__") } ?: return null
        val raw = script.data()
        val start = raw.indexOf('{', raw.indexOf("__PRELOADED_STATE__"))
        if (start < 0) return null
        val json = extractBalancedJson(raw, start) ?: return null
        return runCatching { objectMapper.readTree(json) }.getOrNull()
    }

    // '{' 부터 문자열 리터럴·이스케이프를 고려해 짝이 맞는 '}' 까지 잘라낸다(JS 할당 뒤 trailing 코드 제거).
    private fun extractBalancedJson(
        text: String,
        start: Int,
    ): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    // JSON 트리를 재귀로 훑어 "prices":{"base":{"value":number}} 형태의 prices 노드를 찾는다(유니클로 상품 가격 컨테이너).
    // JsonNode 는 자식(object 값·array 원소)에 대한 Iterable 이라 자식을 직접 순회한다. value 노드는 자식이 없어 멈춘다.
    private fun findPricesNode(node: JsonNode): JsonNode? {
        if (node.path("prices").path("base").path("value").isNumber) return node.path("prices")
        for (child in node) {
            findPricesNode(child)?.let { return it }
        }
        return null
    }

    private fun metaContent(
        document: Document,
        property: String,
    ): String? = document.selectFirst("meta[property=$property]")?.attr("content")?.ifBlank { null }

    // og:title 끝의 사이트명 꼬리표(" | 무신사" 등)를 제거한다. og:title 은 페이지 제목이라 사이트명이 붙기 쉬운데,
    // 그게 상품명으로 새지 않도록 og:site_name 과 일치하는 접미만 떼어낸다(없거나 안 맞으면 원본 유지 — host 무관 일반 규칙).
    private fun stripSiteSuffix(
        title: String?,
        siteName: String?,
    ): String? {
        title ?: return null
        val site = siteName?.trim()?.ifBlank { null } ?: return title
        // 구분자(" | "·" - ") + site_name 으로 시작하는 꼬리표를 떼낸다. 정확 일치(끝이 딱 site_name)뿐 아니라
        // site_name 뒤에 국가·언어 코드가 붙은 경우(유니클로 "상품명 | UNIQLO KR", site_name "UNIQLO")도 잘라낸다.
        // 여러 번 나오면 마지막 위치 기준(상품명 본문의 구분자는 보존). 없거나 안 맞으면 원본 유지(host 무관 일반 규칙).
        for (separator in listOf(" | ", " - ")) {
            val marker = "$separator$site"
            val idx = title.lastIndexOf(marker)
            if (idx >= 0) return title.substring(0, idx).trim()
        }
        return title
    }

    // --- 공통 ---

    // 필수 필드(name+price)가 모두 있고 정규화·범위검증을 통과하면 Extracted.
    //   name·priceText 부재 → MISSING_FIELD (가격은 raw 텍스트 유무로 판단해, 값이 있으나 못 뽑은 경우와 구분한다)
    //   price 파싱 불가(음수·범위초과·비숫자) → INVALID_VALUE
    //   fromExtracted 범위 위반(name 길이초과 등) → INVALID_VALUE
    //   정규화 후 name 이 blank→null (OG site suffix 제거로 빈 문자열이 된 경우 등) → MISSING_FIELD
    private fun toResult(
        link: ProductLink,
        name: String?,
        imageUrl: String?,
        priceText: String?,
        currency: String?,
    ): StructuredExtraction {
        name ?: return StructuredExtraction.Miss.MISSING_FIELD
        priceText ?: return StructuredExtraction.Miss.MISSING_FIELD
        val price = parsePrice(priceText) ?: return StructuredExtraction.Miss.INVALID_VALUE
        val snapshot =
            runCatching { ProductSnapshot.fromExtracted(link, name, imageUrl, price, currency) }
                .getOrNull() ?: return StructuredExtraction.Miss.INVALID_VALUE
        snapshot.name ?: return StructuredExtraction.Miss.MISSING_FIELD
        return StructuredExtraction.Extracted(snapshot)
    }

    private fun textOf(node: JsonNode?): String? {
        node ?: return null
        return node.takeIf { it.isString || it.isNumber }?.asString()?.ifBlank { null }
    }

    // "39,000" · "39000.00" · "₩39,000" · 숫자형(textOf 로 문자열화) → 39000. 음수·정수화 불가는 null.
    private fun parsePrice(raw: String?): Int? {
        val text = raw?.ifBlank { null } ?: return null
        val cleaned = text.replace(PRICE_NOISE, "")
        val decimal = cleaned.toBigDecimalOrNull() ?: return null
        // toInt() 는 Int 범위를 넘으면 하위 비트로 wrap 해 이상값이 통과할 수 있다. 소수는 버리고(DOWN),
        // 범위를 벗어나면 intValueExact 가 예외를 던지게 해 null 로 거른다.
        return runCatching { decimal.setScale(0, RoundingMode.DOWN).intValueExact() }
            .getOrNull()
            ?.takeIf { it >= 0 }
    }

    companion object {
        // 통화기호·천단위 콤마·공백 등 숫자/소수점/부호 외 문자를 제거. 맨 앞 '-'(음수)는 살려 음수 배제가 동작하게 둔다.
        private val PRICE_NOISE = Regex("[^0-9.\\-]")
    }
}
