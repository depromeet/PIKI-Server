package com.depromeet.piki.product.service.gemini

// Gemini generateContent 호출 경계. 추출기들(GeminiHtmlExtractor·GeminiProductImageExtractor)이
// 구현(GeminiHttpClient)이 아니라 이 인터페이스에만 의존하게 해, 통합 테스트가 외부 LLM 호출을
// stub 으로 격리할 수 있다 (CLAUDE.md "외부 호출 경계는 인터페이스 + stub 구현").
interface GeminiClient {
    fun <Req : Any, Res : Any> generateContent(
        request: Req,
        resultType: Class<Res>,
    ): Res
}
