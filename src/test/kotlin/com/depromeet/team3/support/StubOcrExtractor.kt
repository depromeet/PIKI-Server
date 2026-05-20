package com.depromeet.team3.support

import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.ocr.domain.OcrImage
import com.depromeet.team3.ocr.service.OcrExtractor

// OCR 외부 호출(Gemini Vision)을 통합 테스트에서 격리하기 위한 stub.
// StubProductExtractor 와 동일 정책: 모든 통합 테스트가 같은 IntegrationTestSupport 컨텍스트를
// 공유하므로 이 빈도 단일 인스턴스다. 매 테스트가 본문에서 build 람다를 명시적으로 세팅한다.
//
// default build 는 throw 다. 명시 세팅을 빠뜨리면 즉시 IllegalStateException 으로 깨져
// "이전 테스트의 람다가 살아남아 다음 테스트에 영향을 주는" 함정을 차단한다.
//
// 필드 이름이 `build` 인 이유: 인터페이스 메서드 `extract(image)` 와 같은 이름의 var 를 두면
// `override fun extract = extract(image)` 가 자기 재귀가 된다. StubProductExtractor 와 동일 패턴.
class StubOcrExtractor : OcrExtractor {
    var build: (OcrImage) -> Product = {
        error("stub.build 를 테스트 본문에서 명시 세팅해야 한다. CLAUDE.md '테스트 셋업 원칙' 참고.")
    }

    override fun extract(image: OcrImage): Product = build(image)
}
