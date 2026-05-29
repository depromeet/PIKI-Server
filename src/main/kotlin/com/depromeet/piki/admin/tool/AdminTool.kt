package com.depromeet.piki.admin.tool

/**
 * admin 챗봇이 Gemini function calling 으로 노출하는 화이트리스트 작업 한 개.
 *
 * 새 작업(수정·삭제·상태조작 등)을 더하려면 이 인터페이스를 구현한 `@Component` 를 추가하기만 하면 된다 —
 * 레지스트리·멀티턴 루프·2단계 확인 흐름은 [isWrite] 분기로 그대로 재사용된다.
 */
interface AdminTool {
    /** Gemini functionDeclaration.name. 화이트리스트 키이자 [AdminToolRegistry] 의 중복 불가 식별자. */
    val name: String

    /** 모델이 "언제 이 도구를 쓸지" 판단하는 설명. */
    val description: String

    /** 파라미터 JSON schema. functionDeclaration.parameters 로 직렬화된다. */
    val parameters: ToolSchema

    /** read=즉시 실행([execute] 가 Executed). write=2단계 확인(execute 는 검증·미리보기만, 실제 실행은 [commit]). */
    val isWrite: Boolean

    /**
     * read: 즉시 실행해 결과 payload 를 담은 [ToolResult.Executed].
     * write: 부수효과 없이 검증+미리보기만 해서 [ToolResult.PendingConfirmation] (실제 실행은 승인 후 [commit]).
     * 검증 실패는 [ToolResult.Failed].
     */
    fun execute(args: Map<String, Any?>): ToolResult

    /**
     * write tool 의 승인 후 실제 실행. functionResponse 로 모델에 돌려줄 결과 payload 를 반환한다.
     * read tool 은 호출되지 않는다(기본 구현이 즉시 깨진다). 구현체는 영속화 트랜잭션을 위해 `@Transactional` 을 단다.
     */
    fun commit(args: Map<String, Any?>): Map<String, Any?> = error("commit 은 write tool 만 지원한다: $name")
}
