package com.depromeet.piki.product.service.structured

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.PageContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 구조화 파서는 순수 컴포넌트라 Spring·DB 없이 HTML 문자열을 직접 넣어 분기를 망라한다.
// 성공 = name+currentPrice 가 검증을 통과한 ProductSnapshot, 실패/미달 = null(→오케스트레이터가 LLM fallback).
class StructuredDataExtractorTest {
    private val extractor = StructuredDataExtractor(jacksonObjectMapper())

    // --- JSON-LD 변형 견고성 ---

    @Test
    fun `최상위 단일 Product 에서 name 과 price 를 뽑는다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd(
                        """{"@type":"Product","name":"나이키 에어포스","offers":{"@type":"Offer","price":"99000","priceCurrency":"KRW"}}""",
                    ),
                ),
            )

        assertEquals("나이키 에어포스", snapshot?.name)
        assertEquals(99_000, snapshot?.currentPrice)
        assertEquals("KRW", snapshot?.currency)
    }

    @Test
    fun `@graph 로 래핑된 Product 를 평탄화해 뽑는다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd(
                        """{"@graph":[{"@type":"BreadcrumbList"},{"@type":"Product","name":"그래프상품","offers":{"price":"50000"}}]}""",
                    ),
                ),
            )

        assertEquals("그래프상품", snapshot?.name)
        assertEquals(50_000, snapshot?.currentPrice)
    }

    @Test
    fun `최상위 배열 안의 Product 를 뽑는다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd(
                        """[{"@type":"WebSite"},{"@type":"Product","name":"배열상품","offers":{"price":"30000"}}]""",
                    ),
                ),
            )

        assertEquals("배열상품", snapshot?.name)
        assertEquals(30_000, snapshot?.currentPrice)
    }

    @Test
    fun `ItemList 의 itemListElement 안의 Product 를 뽑는다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd(
                        """{"@type":"ItemList","itemListElement":[{"@type":"ListItem","item":{"@type":"Product","name":"리스트상품","offers":{"price":"25000"}}}]}""",
                    ),
                ),
            )

        assertEquals("리스트상품", snapshot?.name)
        assertEquals(25_000, snapshot?.currentPrice)
    }

    @Test
    fun `@type 이 배열이고 Product 를 포함하면 인식한다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd(
                        """{"@type":["Product","IndividualProduct"],"name":"멀티타입","offers":{"price":"15000"}}""",
                    ),
                ),
            )

        assertEquals("멀티타입", snapshot?.name)
        assertEquals(15_000, snapshot?.currentPrice)
    }

    @Test
    fun `offers 가 배열이면 첫 유효 price 를 쓴다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd("""{"@type":"Product","name":"오퍼배열","offers":[{"price":"12000"},{"price":"99999"}]}"""),
                ),
            )

        assertEquals(12_000, snapshot?.currentPrice)
    }

    @Test
    fun `offers_price 가 없으면 priceSpecification_price 를 쓴다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd(
                        """{"@type":"Product","name":"스펙","offers":{"@type":"Offer","priceSpecification":{"@type":"UnitPriceSpecification","price":"77000"}}}""",
                    ),
                ),
            )

        assertEquals(77_000, snapshot?.currentPrice)
    }

    @Test
    fun `AggregateOffer 의 lowPrice 를 currentPrice 로 쓴다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd(
                        """{"@type":"Product","name":"애그리게이트","offers":{"@type":"AggregateOffer","lowPrice":"11000","highPrice":"22000"}}""",
                    ),
                ),
            )

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

        val snapshot = extractor.extract(pageOf(html))

        assertEquals("정상상품", snapshot?.name)
        assertEquals(40_000, snapshot?.currentPrice)
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
            extractor.extract(
                pageOf(jsonLd("""{"@type":"Product","name":"상품","offers":{"price":"$priceText"}}""")),
            )

        assertEquals(expected, snapshot?.currentPrice)
    }

    @Test
    fun `price 가 숫자형이어도 정수로 파싱한다`() {
        val snapshot =
            extractor.extract(
                pageOf(jsonLd("""{"@type":"Product","name":"숫자가격","offers":{"price":39000}}""")),
            )

        assertEquals(39_000, snapshot?.currentPrice)
    }

    @ParameterizedTest
    @ValueSource(strings = ["무료", "Sold Out", "-100"])
    fun `가격을 정수로 파싱할 수 없으면(필수 필드 미달) null 을 반환한다`(priceText: String) {
        val snapshot =
            extractor.extract(
                pageOf(jsonLd("""{"@type":"Product","name":"상품","offers":{"price":"$priceText"}}""")),
            )

        assertNull(snapshot)
    }

    // --- 이미지 ---

    @Test
    fun `image 가 배열이면 첫 원소를 imageUrl 로 쓴다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd(
                        """{"@type":"Product","name":"이미지","image":["https://cdn.example.com/1.jpg","https://cdn.example.com/2.jpg"],"offers":{"price":"5000"}}""",
                    ),
                ),
            )

        assertEquals("https://cdn.example.com/1.jpg", snapshot?.imageUrl)
    }

    @Test
    fun `https 가 아닌 image 는 imageUrl 만 null 이고 나머지로 성공한다`() {
        val snapshot =
            extractor.extract(
                pageOf(
                    jsonLd(
                        """{"@type":"Product","name":"비https이미지","image":"http://cdn.example.com/p.jpg","offers":{"price":"5000"}}""",
                    ),
                ),
            )

        assertEquals("비https이미지", snapshot?.name)
        assertNull(snapshot?.imageUrl)
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

        val snapshot = extractor.extract(pageOf(html))

        assertEquals("오지상품", snapshot?.name)
        assertEquals(45_000, snapshot?.currentPrice)
        assertEquals("https://cdn.example.com/og.jpg", snapshot?.imageUrl)
        assertEquals("KRW", snapshot?.currency)
    }

    @Test
    fun `og 에 title 만 있고 가격이 없으면 필수 필드 미달로 null 을 반환한다`() {
        val html =
            """<html><head><meta property="og:title" content="이름만"/></head><body></body></html>"""

        assertNull(extractor.extract(pageOf(html)))
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

        val snapshot = extractor.extract(pageOf(html))

        assertEquals("제이슨엘디", snapshot?.name)
        assertEquals(10_000, snapshot?.currentPrice)
    }

    // --- 필수 필드 / 검증 미달 → null (fallback 신호) ---

    @Test
    fun `name 만 있고 price 가 없으면 null 을 반환한다`() {
        assertNull(
            extractor.extract(pageOf(jsonLd("""{"@type":"Product","name":"이름만"}"""))),
        )
    }

    @Test
    fun `price 만 있고 name 이 없으면 null 을 반환한다`() {
        assertNull(
            extractor.extract(pageOf(jsonLd("""{"@type":"Product","offers":{"price":"10000"}}"""))),
        )
    }

    @Test
    fun `name 이 공백뿐이면 정규화 후 null 이 되어 null 을 반환한다`() {
        assertNull(
            extractor.extract(pageOf(jsonLd("""{"@type":"Product","name":"   ","offers":{"price":"10000"}}"""))),
        )
    }

    @Test
    fun `name 이 512자를 초과하면(범위 위반) null 을 반환한다`() {
        val longName = "가".repeat(513)
        assertNull(
            extractor.extract(pageOf(jsonLd("""{"@type":"Product","name":"$longName","offers":{"price":"10000"}}"""))),
        )
    }

    @Test
    fun `Product 가 아닌 페이지(구조화 데이터 없음)는 null 을 반환한다`() {
        val html = """<html><head><title>그냥 글</title></head><body><p>본문</p></body></html>"""
        assertNull(extractor.extract(pageOf(html)))
    }

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
