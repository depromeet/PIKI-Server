package com.depromeet.team3.product.service.gemini

import com.fasterxml.jackson.annotation.JsonInclude
import java.net.URI

// Gemini JSON Schema 파서는 `"properties": null` 같은 잉여 null 필드를 스키마 위반으로 취급한다.
// 직렬화 단계에서 null 필드를 전부 생략해 요청 페이로드를 최소한의 형태로 유지한다.
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiExtractionRequest(
    val generationConfig: GenerationConfig,
    val contents: List<Content>,
) {
    data class GenerationConfig(
        val responseMimeType: String,
        val responseJsonSchema: JsonSchema,
    )

    data class Content(
        val parts: List<Part>,
    )

    data class Part(
        val text: String,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class JsonSchema(
        val type: String,
        val description: String? = null,
        val properties: Map<String, JsonSchema>? = null,
        val items: JsonSchema? = null,
        val required: List<String>? = null,
        val nullable: Boolean? = null,
    )

    companion object {
        // 서버에서 직접 fetch 한 HTML 을 in-context 로 받기 때문에 Gemini 의 url_context tool 을 쓰지 않는다.
        // CSR 페이지의 inline JSON-LD 등 정적 HTML 안의 정보를 LLM 이 직접 보고 추출하도록 유도.
        private val SYSTEM_PROMPT =
            """
            You are a product information extractor. Given the URL and the HTML of a product page,
            extract information for the MAIN product of the page.

            **Input**:
            - URL: the product page URL
            - HTML: raw HTML content of that page (may include <script type="application/ld+json"> JSON-LD,
              <meta property="og:..."> Open Graph tags, inline JSON state, and visible body text)

            **Strategy**:
            1. Prefer JSON-LD product schema (Schema.org Product) — fields like offers.price, priceSpecification.price,
               priceCurrency, name, image. This is the most reliable source.
            2. Then Open Graph meta tags (og:title, og:image, og:description) and visible price text.
            3. If no reliable price source exists, return null for unknown fields. DO NOT GUESS.

            Ignore related products, recommended items, advertisements, and sidebar content.

            **Fields**:
            1. isProductPage (boolean, required): true if the page describes a single identifiable product for sale.
               false if this is a list/search/category page, an article, or non-commerce content.
            2. name (string): The product name exactly as displayed. null if isProductPage is false.
            3. regularPrice (integer): The original (pre-discount) price. Remove currency symbols, commas, decimals.
               If only a single price is shown (no discount), put it here.
            4. discountedPrice (integer): The final discounted price. null if no discount is shown.
            5. currency (string): ISO 4217 code (KRW, USD, JPY, EUR, etc.). Infer from page language/locale if ambiguous.
            6. imageUrl (string): ABSOLUTE URL of the primary product image. Prefer og:image meta tag,
               fallback to the main product image. Resolve relative URLs against the page URL.

            **Price rules**:
            - Single price, no discount indicator → regularPrice only, discountedPrice null.
            - Both original and sale prices visible → regularPrice = original, discountedPrice = sale.
            - JSON-LD priceSpecification with priceType="StrikethroughPrice" → that price is regularPrice,
              the outer offers.price is discountedPrice.
            - Do NOT extract discount rate. The server computes it from regularPrice and discountedPrice.

            Respond with JSON only, matching the provided schema. Handle any language.
            """.trimIndent()

        private val EXTRACTION_SCHEMA =
            JsonSchema(
                type = "object",
                properties =
                    mapOf(
                        "isProductPage" to JsonSchema(type = "boolean"),
                        "name" to JsonSchema(type = "string", nullable = true),
                        "regularPrice" to JsonSchema(type = "integer", nullable = true),
                        "discountedPrice" to JsonSchema(type = "integer", nullable = true),
                        "currency" to JsonSchema(type = "string", nullable = true),
                        "imageUrl" to JsonSchema(type = "string", nullable = true),
                    ),
                required = listOf("isProductPage"),
            )

        fun forHtmlExtraction(
            url: URI,
            html: String,
        ): GeminiExtractionRequest =
            GeminiExtractionRequest(
                generationConfig =
                    GenerationConfig(
                        responseMimeType = "application/json",
                        responseJsonSchema = EXTRACTION_SCHEMA,
                    ),
                contents =
                    listOf(
                        Content(
                            parts =
                                listOf(
                                    Part(text = SYSTEM_PROMPT),
                                    Part(text = "URL: $url\n\nHTML:\n$html"),
                                ),
                        ),
                    ),
            )
    }
}
