package com.depromeet.piki.common.logging

// MDC 키 단일 출처. JwtAuthenticationFilter 가 인증된 요청에 userId 를 넣고, logback 콘솔 패턴(application.yml
// 의 %X{userId})·ECS 구조화 출력이 같은 키로 값을 읽는다. 키 문자열을 바꾸면 application.yml 의 %X{userId} 도
// 함께 바꿔야 한다(yaml 은 이 상수를 import 할 수 없어 리터럴이 양쪽에 존재 — 주석으로 연결).
//
// traceId/spanId 는 brave(Micrometer Tracing)가 자체 키로 MDC 에 넣으므로 여기서 정의하지 않는다.
// userId 는 한 유저의 여러 요청을 가로질러 묶는 상관추적 키다 — traceId 가 요청 1건을 묶는다면 userId 는
// 그 유저의 로그인→플레이→… 여정 전체를 grep 으로 잇는다. userId(UUID)는 크리덴셜이 아니고 내부 가명
// 식별자라 마스킹 없이 그대로 싣는다(역추적은 DB join 이라는 별도 권한 경계를 거친다).
object LoggingKeys {
    const val USER_ID = "userId"

    // /admin 백오피스 요청의 슬랙 actor(표시명). AdminAccessFilter 가 세션 신원에서 꺼내 요청 내내 MDC 에 얹어
    // 도메인 로그에 찍히게 하고, request attribute 로도 심어 AccessLogFilter 가 access log 한 줄에 재주입한다
    // (userId 와 같은 흐름 — JwtAuthenticationFilter↔AccessLogFilter). userId(UUID 가명 식별자)와 의미가 달라
    // 슬롯을 재사용하지 않고 별도 키로 둔다. prod ECS JSON 은 MDC 를 필드로 자동 출력하므로 별도 설정 없이
    // adminActor 필드로 뜬다(평문 콘솔 패턴엔 명시 안 함 — 비-admin 요청까지 빈 슬롯이 붙는 노이즈를 피한다).
    const val ADMIN_ACTOR = "adminActor"
}
