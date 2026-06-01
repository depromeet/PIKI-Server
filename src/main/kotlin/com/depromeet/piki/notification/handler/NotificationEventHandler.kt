package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import org.springframework.core.GenericTypeResolver
import java.util.UUID
import kotlin.reflect.KClass

// 도메인 이벤트(item/tournament 등이 발행하는 사실)를 알림으로 변환하는 단위.
// 도메인은 자기 이벤트만 발행하고, 알림 도메인이 이 핸들러로 구독해 수신자·변수·refId 를 결정한다.
// 결합 방향: 알림 -> 도메인 (단방향). 도메인은 알림 패키지를 import 하지 않는다.
//
// eventType(Dispatcher 라우팅 키)은 타입 인자 E 에서 자동 도출한다 — 구현체가 `::class` 로 같은 타입을
// 한 번 더 적던 중복을 없앤다. 제네릭이 (1) 메서드 시그니처의 컴파일타임 타입 안전과 (2) eventType 도출을
// 모두 담당하므로, 둘이 어긋날 수 없다(같은 한 소스에서 나온다).
abstract class NotificationEventHandler<E : Any>(
    // 템플릿 조회 키.
    val notificationType: NotificationType,
) {
    // Dispatcher 라우팅 키 — 타입 인자 E 를 도출해 채운다. (도출 로직은 resolveEventType 에 격리)
    val eventType: KClass<E> = resolveEventType()

    // 도메인 이벤트의 어느 필드가 알림의 딥링크·역조회 키(refId)인지 결정한다.
    // 도메인 이벤트는 itemId·tournamentId 등 자기 식별자만 알 뿐 refId 개념을 모른다 —
    // 알림 도메인이 자기 표현(Notification.refId)으로 끌어내는 책임을 진다.
    abstract fun resolveRefId(event: E): Long

    // 수신자 (개인=본인 / 협업=참가자 fan-out). refId 로 위시·토너먼트를 역조회해 결정한다.
    // Set 으로 둬 "수신자는 중복 없는 집합"을 타입에 박는다 — owner + 참가자를 합쳐도 같은 유저에게
    // 알림 row 가 중복 저장되지 않는다(#236 fan-out 안전).
    abstract fun resolveRecipients(event: E): Set<UUID>

    // 템플릿 변수 (예: actorName). 변수 없는 알림은 기본값 emptyMap 을 그대로 쓰고 override 하지 않는다.
    open fun resolveVariables(event: E): Map<String, String> = emptyMap()

    // 타입 인자 E 를 Spring 의 제네릭 추출 헬퍼로 풀어낸다. 서브클래스 생성 시 javaClass 는 실제 구현체라,
    // 그 슈퍼타입 NotificationEventHandler<E> 의 첫 타입 인자가 E 다. (private 라 가상 호출이 아니므로 init 안전)
    @Suppress("UNCHECKED_CAST")
    private fun resolveEventType(): KClass<E> =
        (
            GenericTypeResolver.resolveTypeArgument(javaClass, NotificationEventHandler::class.java)
                ?: error("${javaClass.simpleName} 의 이벤트 타입 인자(E)를 해석할 수 없습니다")
        ).kotlin as KClass<E>
}
