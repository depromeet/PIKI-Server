package com.depromeet.team3.ocr.service.gemini

data class GeminiOcrRequest(
    val generationConfig: GenerationConfig,
    val contents: List<Content>,
) {
    data class GenerationConfig(
        val responseMimeType: String,
        val responseSchema: Schema,
    )

    data class Content(
        val parts: List<Part>,
    )

    sealed interface Part {
        data class Text(val text: String) : Part
        data class Image(val inlineData: InlineData) : Part
    }

    data class InlineData(
        val mimeType: String,
        val data: String,
    )

    data class Schema(
        val type: SchemaType,
        val properties: Map<String, Schema>? = null,
        val items: Schema? = null,
        val required: List<String>? = null,
        val nullable: Boolean? = null,
    )

    enum class SchemaType {
        OBJECT,
        ARRAY,
        STRING,
        INTEGER,
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are a product information extractor. The user captured a product page to identify the product they are interested in.

            **Intent inference**: The user wants information about the MAIN product on the page. Ignore related products, recommended items, ads, and sidebar content. Focus on the primary product that occupies the central area of the page.

            Extract the following for the main product only:

            1. **name**: The product name exactly as displayed. null if not found.
            2. **price**: The price as an integer (remove currency symbols, commas). If multiple prices exist, use the final/sale price. null if not found.
            3. **category**: The category for the product (e.g. "식품", "음료", "생활용품", "의류", "전자기기", "화장품" etc.). If the category is explicitly shown on the page (e.g. breadcrumb, tag), use that text. Otherwise infer from the product. null if completely unclear.
            4. **currency**: The ISO 4217 currency code of the price, inferred from the currency symbol or context (e.g. "KRW" for ₩/원, "USD" for $, "JPY" for ¥/円, "EUR" for €). null if there is no price or the currency is unclear.

            Return information for the single main product only. Do NOT include related/recommended/ad products.
            Handle any language (Korean, Japanese, English, etc.).
        """.trimIndent()

        private val PRODUCT_SCHEMA = Schema(
            type = SchemaType.OBJECT,
            properties = mapOf(
                "name" to Schema(type = SchemaType.STRING, nullable = true),
                "price" to Schema(type = SchemaType.INTEGER, nullable = true),
                "category" to Schema(type = SchemaType.STRING, nullable = true),
                "currency" to Schema(type = SchemaType.STRING, nullable = true),
            ),
        )

        fun forImageAnalysis(base64Image: String, mimeType: String): GeminiOcrRequest =
            GeminiOcrRequest(
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    responseSchema = PRODUCT_SCHEMA,
                ),
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part.Text(SYSTEM_PROMPT),
                            Part.Image(InlineData(mimeType = mimeType, data = base64Image)),
                        ),
                    ),
                ),
            )
    }
}
