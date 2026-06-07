package com.depromeet.piki.support

import com.depromeet.piki.product.service.gemini.GeminiClient

// 외부 LLM 호출 경계를 통합 테스트에서 격리하는 stub. build 람다로 응답을 교체하고,
// invocations 카운터로 "구조화 우선 파싱이 성공하면 LLM 을 호출하지 않는다"를 단언한다.
// default build 는 throw — 명시 세팅을 빠뜨리면 즉시 깨진다. 매 테스트가 본문에서 reset()+build 를 세팅한다.
class StubGeminiClient : GeminiClient {
    var invocations = 0
        private set

    var build: (Any) -> Any = {
        error("stub.build 를 테스트 본문에서 명시 세팅해야 한다. CLAUDE.md '테스트 셋업 원칙' 참고.")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Req : Any, Res : Any> generateContent(
        request: Req,
        resultType: Class<Res>,
    ): Res {
        invocations++
        return build(request) as Res
    }

    fun reset() {
        invocations = 0
    }
}
