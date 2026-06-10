package com.depromeet.piki.product.service.structured

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.PageContent
import com.depromeet.piki.product.service.ProductSnapshot
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.assertEquals

// 구조화 파서는 순수 컴포넌트라 Spring·DB 없이 HTML 문자열을 직접 넣어 분기를 망라한다.
// 성공 = name+currentPrice 가 검증을 통과한 Extracted(snapshot), 실패 = 사유를 담은 Miss(→오케스트레이터가 LLM fallback).
// 실패 케이스는 reason(no_data/missing_field/invalid_value)까지 단언해 reason 분류 로직도 함께 검증한다.
class StructuredDataExtractorTest {
    private val extractor = StructuredDataExtractor(jacksonObjectMapper())

    // --- JSON-LD 변형 견고성 ---

    @Test
    fun `최상위 단일 Product 에서 name 과 price 를 뽑는다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":"Product","name":"나이키 에어포스","offers":{"@type":"Offer","price":"99000","priceCurrency":"KRW"}}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("나이키 에어포스", snapshot?.name)
        assertEquals(99_000, snapshot?.currentPrice)
        assertEquals("KRW", snapshot?.currency)
    }

    @Test
    fun `@graph 로 래핑된 Product 를 평탄화해 뽑는다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@graph":[{"@type":"BreadcrumbList"},{"@type":"Product","name":"그래프상품","offers":{"price":"50000"}}]}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("그래프상품", snapshot?.name)
        assertEquals(50_000, snapshot?.currentPrice)
    }

    @Test
    fun `최상위 배열 안의 Product 를 뽑는다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """[{"@type":"WebSite"},{"@type":"Product","name":"배열상품","offers":{"price":"30000"}}]""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("배열상품", snapshot?.name)
        assertEquals(30_000, snapshot?.currentPrice)
    }

    @Test
    fun `ItemList 의 itemListElement 안의 Product 를 뽑는다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":"ItemList","itemListElement":[{"@type":"ListItem","item":{"@type":"Product","name":"리스트상품","offers":{"price":"25000"}}}]}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("리스트상품", snapshot?.name)
        assertEquals(25_000, snapshot?.currentPrice)
    }

    @Test
    fun `@type 이 배열이고 Product 를 포함하면 인식한다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":["Product","IndividualProduct"],"name":"멀티타입","offers":{"price":"15000"}}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("멀티타입", snapshot?.name)
        assertEquals(15_000, snapshot?.currentPrice)
    }

    @Test
    fun `offers 가 배열이면 첫 유효 price 를 쓴다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd("""{"@type":"Product","name":"오퍼배열","offers":[{"price":"12000"},{"price":"99999"}]}"""),
                    ),
                ).snapshotOrNull()

        assertEquals(12_000, snapshot?.currentPrice)
    }

    @Test
    fun `offers_price 가 없으면 priceSpecification_price 를 쓴다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":"Product","name":"스펙","offers":{"@type":"Offer","priceSpecification":{"@type":"UnitPriceSpecification","price":"77000"}}}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals(77_000, snapshot?.currentPrice)
    }

    @Test
    fun `AggregateOffer 의 lowPrice 를 currentPrice 로 쓴다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":"Product","name":"애그리게이트","offers":{"@type":"AggregateOffer","lowPrice":"11000","highPrice":"22000"}}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals(11_000, snapshot?.currentPrice)
    }

    @Test
    fun `깨진 ld+json 덩어리가 있어도 다른 정상 덩어리에서 뽑는다`() {
        val html =
            """
            <html><head>
            <script type="application/ld+json">{ this is broken json )</script>
            <script type="application/ld+json">{"@type":"Product","name":"정상상품","offers":{"price":"40000"}}</script>
            </head><body></body></html>
            """.trimIndent()

        val snapshot = extractor.extract(pageOf(html)).snapshotOrNull()

        assertEquals("정상상품", snapshot?.name)
        assertEquals(40_000, snapshot?.currentPrice)
    }

    @Test
    fun `script type 에 charset 파라미터가 붙어도 JSON-LD 를 파싱한다`() {
        val html =
            """<html><head><script type="application/ld+json; charset=utf-8">{"@type":"Product","name":"차셋상품","offers":{"price":"12000"}}</script></head><body></body></html>"""

        val snapshot = extractor.extract(pageOf(html)).snapshotOrNull()

        assertEquals("차셋상품", snapshot?.name)
        assertEquals(12_000, snapshot?.currentPrice)
    }

    @Test
    fun `앞 Product 가 검증 미달이면 같은 배열의 뒤 완전한 Product 를 쓴다`() {
        // 첫 Product 는 name 만(price 없음), 둘째 Product 는 name+price.
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """[{"@type":"Product","name":"요약"},{"@type":"Product","name":"상세상품","offers":{"price":"45000"}}]""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("상세상품", snapshot?.name)
        assertEquals(45_000, snapshot?.currentPrice)
    }

    @Test
    fun `앞 script 의 Product 가 검증 미달이면 다음 script 의 완전한 Product 를 쓴다`() {
        val html =
            """
            <html><head>
            <script type="application/ld+json">{"@type":"Product","name":"요약만"}</script>
            <script type="application/ld+json">{"@type":"Product","name":"완전상품","offers":{"price":"30000"}}</script>
            </head><body></body></html>
            """.trimIndent()

        assertEquals("완전상품", extractor.extract(pageOf(html)).snapshotOrNull()?.name)
    }

    // --- 가격 문자열 파싱 ---

    @ParameterizedTest
    @CsvSource(
        "'99,000', 99000",
        "'99000.00', 99000",
        "'₩99,000', 99000",
        "'$99', 99",
        "' 99000 ', 99000",
    )
    fun `가격 문자열의 통화기호·콤마·소수·공백을 정제해 정수로 파싱한다`(
        priceText: String,
        expected: Int,
    ) {
        val snapshot =
            extractor
                .extract(
                    pageOf(jsonLd("""{"@type":"Product","name":"상품","offers":{"price":"$priceText"}}""")),
                ).snapshotOrNull()

        assertEquals(expected, snapshot?.currentPrice)
    }

    @Test
    fun `price 가 숫자형이어도 정수로 파싱한다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(jsonLd("""{"@type":"Product","name":"숫자가격","offers":{"price":39000}}""")),
                ).snapshotOrNull()

        assertEquals(39_000, snapshot?.currentPrice)
    }

    @ParameterizedTest
    @ValueSource(strings = ["무료", "Sold Out", "-100"])
    fun `가격 텍스트는 있으나 정수로 파싱할 수 없으면 invalid_value 로 fallback 한다`(priceText: String) {
        // 가격 노드(텍스트)는 존재하므로 missing 이 아니라 invalid_value — 파서의 정규화 보강 여지를 가리킨다.
        val result =
            extractor.extract(
                pageOf(jsonLd("""{"@type":"Product","name":"상품","offers":{"price":"$priceText"}}""")),
            )

        assertEquals(StructuredExtraction.Miss.INVALID_VALUE, result)
    }

    @Test
    fun `가격이 Int 범위를 초과하면 invalid_value 로 fallback 한다`() {
        // 999999999999 는 Int.MAX(약 21억)를 한참 초과 — toInt 로는 wrap 되지만 intValueExact 로 거른다.
        val result =
            extractor.extract(
                pageOf(jsonLd("""{"@type":"Product","name":"초대형가격","offers":{"price":"999999999999"}}""")),
            )

        assertEquals(StructuredExtraction.Miss.INVALID_VALUE, result)
    }

    // --- 이미지 ---

    @Test
    fun `image 가 배열이면 첫 원소를 imageUrl 로 쓴다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":"Product","name":"이미지","image":["https://cdn.example.com/1.jpg","https://cdn.example.com/2.jpg"],"offers":{"price":"5000"}}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("https://cdn.example.com/1.jpg", snapshot?.imageUrl)
    }

    @Test
    fun `https 가 아닌 image 는 imageUrl 만 null 이고 나머지로 성공한다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":"Product","name":"비https이미지","image":"http://cdn.example.com/p.jpg","offers":{"price":"5000"}}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("비https이미지", snapshot?.name)
        assertEquals(null, snapshot?.imageUrl)
    }

    @Test
    fun `image 가 ImageObject 면 contentUrl 을 imageUrl 로 뽑는다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":"Product","name":"이미지객체","image":{"@type":"ImageObject","contentUrl":"https://cdn.example.com/c.jpg"},"offers":{"price":"5000"}}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("https://cdn.example.com/c.jpg", snapshot?.imageUrl)
    }

    @Test
    fun `image 가 ImageObject 배열이면 첫 원소의 contentUrl 을 뽑는다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":"Product","name":"이미지배열","image":[{"@type":"ImageObject","contentUrl":"https://cdn.example.com/1.jpg"},{"@type":"ImageObject","contentUrl":"https://cdn.example.com/2.jpg"}],"offers":{"price":"5000"}}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("https://cdn.example.com/1.jpg", snapshot?.imageUrl)
    }

    @Test
    fun `ImageObject 에 url 과 contentUrl 이 둘 다 있으면 url 을 우선한다`() {
        val snapshot =
            extractor
                .extract(
                    pageOf(
                        jsonLd(
                            """{"@type":"Product","name":"우선순위","image":{"@type":"ImageObject","url":"https://cdn.example.com/url.jpg","contentUrl":"https://cdn.example.com/content.jpg"},"offers":{"price":"5000"}}""",
                        ),
                    ),
                ).snapshotOrNull()

        assertEquals("https://cdn.example.com/url.jpg", snapshot?.imageUrl)
    }

    // --- OpenGraph (JSON-LD 가 없을 때의 보조 경로) ---

    @Test
    fun `JSON-LD 가 없고 og 태그에 name 과 가격이 있으면 og 로 뽑는다`() {
        val html =
            """
            <html><head>
            <meta property="og:title" content="오지상품"/>
            <meta property="og:image" content="https://cdn.example.com/og.jpg"/>
            <meta property="product:price:amount" content="45000"/>
            <meta property="product:price:currency" content="KRW"/>
            </head><body></body></html>
            """.trimIndent()

        val snapshot = extractor.extract(pageOf(html)).snapshotOrNull()

        assertEquals("오지상품", snapshot?.name)
        assertEquals(45_000, snapshot?.currentPrice)
        assertEquals("https://cdn.example.com/og.jpg", snapshot?.imageUrl)
        assertEquals("KRW", snapshot?.currency)
    }

    @Test
    fun `og 에 title 만 있고 가격이 없으면 missing_field 로 fallback 한다`() {
        // og 태그는 있으나(no_data 아님) 가격이 없어 필수 필드 미달.
        val html =
            """<html><head><meta property="og:title" content="이름만"/></head><body></body></html>"""

        assertEquals(StructuredExtraction.Miss.MISSING_FIELD, extractor.extract(pageOf(html)))
    }

    @Test
    fun `JSON-LD 가 성공하면 og 태그는 보지 않는다`() {
        val html =
            """
            <html><head>
            <script type="application/ld+json">{"@type":"Product","name":"제이슨엘디","offers":{"price":"10000"}}</script>
            <meta property="og:title" content="오지제목"/>
            <meta property="product:price:amount" content="99999"/>
            </head><body></body></html>
            """.trimIndent()

        val snapshot = extractor.extract(pageOf(html)).snapshotOrNull()

        assertEquals("제이슨엘디", snapshot?.name)
        assertEquals(10_000, snapshot?.currentPrice)
    }

    @Test
    fun `og 경로에서 og_site_name 꼬리표를 제거한다`() {
        val html =
            """
            <html><head>
            <meta property="og:title" content="상품명 [Grey] - 사이즈 & 후기 | 무신사"/>
            <meta property="og:site_name" content="무신사"/>
            <meta property="product:price:amount" content="35100"/>
            </head><body></body></html>
            """.trimIndent()

        assertEquals("상품명 [Grey] - 사이즈 & 후기", extractor.extract(pageOf(html)).snapshotOrNull()?.name)
    }

    @Test
    fun `og_site_name 이 하이픈 구분자로 붙어도 제거한다`() {
        val html =
            """
            <html><head>
            <meta property="og:title" content="가방 - 29CM"/>
            <meta property="og:site_name" content="29CM"/>
            <meta property="product:price:amount" content="10000"/>
            </head><body></body></html>
            """.trimIndent()

        assertEquals("가방", extractor.extract(pageOf(html)).snapshotOrNull()?.name)
    }

    @Test
    fun `og_site_name 이 없으면 og_title 을 그대로 쓴다`() {
        val html =
            """
            <html><head>
            <meta property="og:title" content="상품명 | 사이트"/>
            <meta property="product:price:amount" content="10000"/>
            </head><body></body></html>
            """.trimIndent()

        assertEquals("상품명 | 사이트", extractor.extract(pageOf(html)).snapshotOrNull()?.name)
    }

    // --- 실패 사유(reason) 분류 ---

    @Test
    fun `name 만 있고 price 가 없으면 missing_field 로 fallback 한다`() {
        assertEquals(
            StructuredExtraction.Miss.MISSING_FIELD,
            extractor.extract(pageOf(jsonLd("""{"@type":"Product","name":"이름만"}"""))),
        )
    }

    @Test
    fun `price 만 있고 name 이 없으면 missing_field 로 fallback 한다`() {
        assertEquals(
            StructuredExtraction.Miss.MISSING_FIELD,
            extractor.extract(pageOf(jsonLd("""{"@type":"Product","offers":{"price":"10000"}}"""))),
        )
    }

    @Test
    fun `name 이 공백뿐이면 missing_field 로 fallback 한다`() {
        assertEquals(
            StructuredExtraction.Miss.MISSING_FIELD,
            extractor.extract(pageOf(jsonLd("""{"@type":"Product","name":"   ","offers":{"price":"10000"}}"""))),
        )
    }

    @Test
    fun `name 이 512자를 초과하면(범위 위반) invalid_value 로 fallback 한다`() {
        val longName = "가".repeat(513)
        assertEquals(
            StructuredExtraction.Miss.INVALID_VALUE,
            extractor.extract(pageOf(jsonLd("""{"@type":"Product","name":"$longName","offers":{"price":"10000"}}"""))),
        )
    }

    @Test
    fun `Product 도 og 태그도 없으면 no_data 로 fallback 한다`() {
        val html = """<html><head><title>그냥 글</title></head><body><p>본문</p></body></html>"""
        assertEquals(StructuredExtraction.Miss.NO_DATA, extractor.extract(pageOf(html)))
    }

    @Test
    fun `여러 Product 가 모두 실패하면 더 근접한 사유를 reason 으로 보고한다`() {
        // 첫 노드: price 없음(missing_field), 둘째 노드: price 음수(invalid_value). 성공 노드 없음 → 더 근접한 invalid_value.
        val html =
            jsonLd(
                """[{"@type":"Product","name":"미달"},{"@type":"Product","name":"음수","offers":{"price":"-100"}}]""",
            )
        assertEquals(StructuredExtraction.Miss.INVALID_VALUE, extractor.extract(pageOf(html)))
    }

    @Test
    fun `JSON-LD 는 부재하고 OG 가격이 파싱 불가면 cross-source worse 로 invalid_value 를 보고한다`() {
        // JSON-LD Product 없음(no_data) + OG 는 title·가격텍스트가 있으나 파싱 불가(invalid_value).
        // extract() 가 두 소스를 worse 로 합쳐 더 근접한 invalid_value 를 골라야 한다.
        val html =
            """
            <html><head>
            <meta property="og:title" content="오지상품"/>
            <meta property="product:price:amount" content="무료"/>
            </head><body></body></html>
            """.trimIndent()
        assertEquals(StructuredExtraction.Miss.INVALID_VALUE, extractor.extract(pageOf(html)))
    }

    @Test
    fun `og 에 통화만 있고 다른 태그가 없으면 no_data 가 아니라 missing_field 로 fallback 한다`() {
        // product:price:currency 만 단독으로 있는 페이지 — OG 태그가 존재하므로 no_data 가 아니라 필수 필드 미달(missing_field).
        val html =
            """<html><head><meta property="product:price:currency" content="KRW"/></head><body></body></html>"""
        assertEquals(StructuredExtraction.Miss.MISSING_FIELD, extractor.extract(pageOf(html)))
    }

    private fun StructuredExtraction.snapshotOrNull(): ProductSnapshot? = (this as? StructuredExtraction.Extracted)?.snapshot

    private fun jsonLd(body: String): String =
        """
        <html><head>
        <script type="application/ld+json">$body</script>
        </head><body></body></html>
        """.trimIndent()

    private fun pageOf(html: String): PageContent = PageContent(link = ProductLink.parse(URL), html = html)

    companion object {
        private const val URL = "https://shop.example.com/products/42"
    }
}
