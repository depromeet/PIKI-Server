package com.depromeet.piki.product.service.screening

import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.File

// 검수 입력 — 플랫폼 하나당 PC 직링크 묶음과 모바일 공유링크(공유하기 버튼 결과) 묶음.
// 1단계 로컬(LinkScreeningLocalE2ETest)과 2단계 dev(LinkScreeningDevApiE2ETest)가 같은 입력을 공유한다.
data class ScreeningTarget(
    val platform: String,
    val pc: List<String> = emptyList(),
    val mobile: List<String> = emptyList(),
)

// 검수 대상 URL 묶음을 환경변수 SCREENING_INPUT 이 가리키는 JSON 파일에서 읽는다.
//
// 이 환경변수가 곧 E2E 격리 게이트(@EnabledIfEnvironmentVariable)이기도 하다 — 미설정 시(CI 포함) 검수가
// 통째로 skip 되어, 무방비 E2E 가 CI 에서 실제 외부 호출을 돌리는 것을 막는다(TestConventionTest 격리 규칙 충족).
// 입력을 코드에 하드코딩하지 않고 외부 파일로 빼, 매 검수마다 코드 수정 없이 JSON 만 교체한다.
//
// JSON 형식:
//   [
//     { "platform": "퀸잇",   "pc": ["https://...", ...5], "mobile": ["https://...", ...5] },
//     { "platform": "유니클로", "pc": [...],                "mobile": [...] }
//   ]
object ScreeningInput {
    const val ENV = "SCREENING_INPUT"

    fun load(): List<ScreeningTarget> {
        val path = System.getenv(ENV) ?: error("$ENV 환경변수에 검수 입력 JSON 경로를 지정해야 한다.")
        val file = File(path)
        check(file.isFile) { "$ENV 가 가리키는 파일을 찾지 못했다: ${file.absolutePath}" }
        return jacksonObjectMapper().readValue<List<ScreeningTarget>>(file.readText())
    }
}
