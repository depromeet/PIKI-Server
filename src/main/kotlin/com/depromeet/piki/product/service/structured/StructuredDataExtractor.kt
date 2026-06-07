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
// LLM 호출 없이 ProductSnapshot 을 만든다. 필수 필드(name+currentPrice)가 검증을 통과하면 성공,
// 미달·부재·검증위반이면 null 을 돌려 오케스트레이터가 Gemini fallback 으로 넘어가게 한다.
//
// jsoup 은 마크업에서 <script ld+json>·<meta og:*> 블록을 정확히 꺼내는 책임만 지고, JSON-LD 값 자체는
// Jackson 3 트리(JsonNode)로 다룬다. 깨진 ld+json 한 덩어리가 전체를 죽이지 않도록 각 script 를 runCatching 으로 격리한다.
@Component
class StructuredDataExtractor(
    private val objectMapper: ObjectMapper,
) {
    // 오케스트레이터가 파싱한 Document 를 공유받아 읽기만 한다(Document 를 변형하지 않으므로 이후 Gemini fallback 과 안전하게 공유).
    // 우선순위: JSON-LD(구조적 가격을 정확히 들고 있음) > OpenGraph(가격 표준 태그가 없어 보조).
    fun extract(
        document: Document,
        link: ProductLink,
    ): ProductSnapshot? = fromJsonLd(document, link) ?: fromOpenGraph(document, link)

    // 단독 호출·테스트 편의: HTML 을 직접 파싱해 위임한다. 운영 경로는 오케스트레이터가 Document 를 만들어 공유한다.
    fun extract(page: PageContent): ProductSnapshot? = extract(Jsoup.parse(page.html, page.link.value.toString()), page.link)

    // --- JSON-LD (schema.org/Product) ---

    private fun fromJsonLd(
        document: Document,
        link: ProductLink,
    ): ProductSnapshot? =
        // type 속성에 charset 파라미터·따옴표·공백 변형이 붙어도 application/ld+json 으로 인식한다(jsoup 이 파싱한 attr 기준).
        document
            .select("script[type]")
            .filter { it.attr("type").trim().startsWith("application/ld+json", ignoreCase = true) }
            .flatMap { script ->
                val root = runCatching { objectMapper.readTree(script.data()) }.getOrNull()
                root?.let { collectProductNodes(it) } ?: emptyList()
            }
            // 앞 Product 가 검증에 실패해도(요약용 불완전 노드 등) 뒤의 완전한 Product 까지 모두 시도한다.
            .firstNotNullOfOrNull { product -> toSnapshotFromProduct(product, link) }

    private fun toSnapshotFromProduct(
        product: JsonNode,
        link: ProductLink,
    ): ProductSnapshot? {
        val offer = firstOffer(product)
        return toSnapshotOrNull(
            link = link,
            name = textOf(product.get("name")),
            imageUrl = imageUrlOf(product),
            price = parsePrice(textOf(priceNode(offer))),
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
    ): ProductSnapshot? =
        toSnapshotOrNull(
            link = link,
            name = stripSiteSuffix(metaContent(document, "og:title"), metaContent(document, "og:site_name")),
            imageUrl = metaContent(document, "og:image"),
            // OG 표준엔 가격이 없어 product:price:amount(OG product 확장)를 best-effort 로 시도한다.
            price = parsePrice(metaContent(document, "product:price:amount")),
            currency = metaContent(document, "product:price:currency"),
        )

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
        for (separator in listOf(" | ", " - ")) {
            val suffix = "$separator$site"
            if (title.endsWith(suffix)) return title.removeSuffix(suffix).trim()
        }
        return title
    }

    // --- 공통 ---

    // 필수 필드(name+price)가 모두 있고 정규화·범위검증을 통과해야 성공. 하나라도 미달이면 null(→fallback).
    private fun toSnapshotOrNull(
        link: ProductLink,
        name: String?,
        imageUrl: String?,
        price: Int?,
        currency: String?,
    ): ProductSnapshot? {
        name ?: return null
        price ?: return null
        // 범위 위반(가격 음수·길이 초과)은 fromExtracted 가 예외를 던지므로 흡수해 null(→fallback)로 다룬다.
        val snapshot =
            runCatching { ProductSnapshot.fromExtracted(link, name, imageUrl, price, currency) }
                .getOrNull() ?: return null
        // 정규화 후 name 이 blank→null 이 됐으면 필수 필드 상실로 보고 fallback (price 는 fromExtracted 가 보존).
        snapshot.name ?: return null
        return snapshot
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
