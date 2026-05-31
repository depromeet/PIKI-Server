package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import java.util.UUID
import kotlin.reflect.KClass

// 도메인 이벤트(item/tournament 등이 발행하는 사실)를 알림으로 변환하는 단위.
// 도메인은 자기 이벤트만 발행하고, 알림 도메인이 이 핸들러로 구독해 수신자·변수·refId 를 결정한다.
// 결합 방향: 알림 -> 도메인 (단방향). 도메인은 알림 패키지를 import 하지 않는다.
//
// 타입 인자 E 는 메서드 시그니처를 구체 이벤트 타입으로 고정해 캐스팅 없이 안전하게 쓰게 한다.
// eventType(Dispatcher 라우팅 키)은 생성자로 명시받는다 — `<E>` 와 `E::class` 가 생성자 한 줄에 나란히
// 오므로 어긋나면 컴파일 에러(KClass<E> 자리에 다른 타입은 못 넣는다). reflection 으로 도출하지 않는다.
abstract class NotificationEventHandler<E : Any>(
    // Dispatcher 라우팅 키 — 이 핸들러가 처리할 도메인 이벤트 타입.
    val eventType: KClass<E>,
    // 템플릿 조회 키.
    val notificationType: NotificationType,
) {
    // 도메인 이벤트의 어느 필드가 알림의 딥링크·역조회 키(refId)인지 결정한다.
    // 도메인 이벤트는 itemId·tournamentId 등 자기 식별자만 알 뿐 refId 개념을 모른다 —
    // 알림 도메인이 자기 표현(Notification.refId)으로 끌어내는 책임을 진다.
    abstract fun resolveRefId(event: E): Long

    // 수신자 (개인=본인 / 협업=참가자 fan-out). refId 로 위시·토너먼트를 역조회해 결정한다.
    abstract fun resolveRecipients(event: E): List<UUID>

    // 템플릿 변수 (예: actorName). 변수 없는 알림은 기본값 emptyMap 을 그대로 쓰고 override 하지 않는다.
    open fun resolveVariables(event: E): Map<String, String> = emptyMap()
}
