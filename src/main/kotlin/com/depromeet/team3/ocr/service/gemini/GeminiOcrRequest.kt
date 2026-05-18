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
            4. **nameBoundingBox**: Bounding box object of the product NAME text area as {"ymin": Int, "xmin": Int, "ymax": Int, "xmax": Int} normalized to 0-1000. null if not found.
            5. **priceBoundingBox**: Bounding box object of the product PRICE text area as {"ymin": Int, "xmin": Int, "ymax": Int, "xmax": Int} normalized to 0-1000. null if not found.
            6. **categoryBoundingBox**: Bounding box object of the category text area as {"ymin": Int, "xmin": Int, "ymax": Int, "xmax": Int} normalized to 0-1000, ONLY if category is explicitly shown on the page. MUST be null if the category was inferred (not displayed).

            All bounding box coordinates MUST be normalized to the 0-1000 range relative to the image dimensions, NOT pixel values.

            Return information for the single main product only. Do NOT include related/recommended/ad products.
            Handle any language (Korean, Japanese, English, etc.).
        """.trimIndent()

        // Gemini 는 네이티브로 [ymin, xmin, ymax, xmax] 배열 형식을 반환하지만,
        // responseSchema 로 객체 형식을 강제하면 필드명으로 접근이 가능해져 파싱이 안정적이다.
        // 프롬프트의 BoundingBox 설명도 이 스키마와 일치하도록 객체 형태로 명시되어 있음.
        // https://ai.google.dev/gemini-api/docs/image-understanding
        private val BOUNDING_BOX_SCHEMA = Schema(
            type = SchemaType.OBJECT,
            nullable = true,
            properties = mapOf(
                "ymin" to Schema(type = SchemaType.INTEGER),
                "xmin" to Schema(type = SchemaType.INTEGER),
                "ymax" to Schema(type = SchemaType.INTEGER),
                "xmax" to Schema(type = SchemaType.INTEGER),
            ),
            required = listOf("ymin", "xmin", "ymax", "xmax"),
        )

        private val PRODUCT_SCHEMA = Schema(
            type = SchemaType.OBJECT,
            properties = mapOf(
                "name" to Schema(type = SchemaType.STRING, nullable = true),
                "price" to Schema(type = SchemaType.INTEGER, nullable = true),
                "category" to Schema(type = SchemaType.STRING, nullable = true),
                "nameBoundingBox" to BOUNDING_BOX_SCHEMA,
                "priceBoundingBox" to BOUNDING_BOX_SCHEMA,
                "categoryBoundingBox" to BOUNDING_BOX_SCHEMA,
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
