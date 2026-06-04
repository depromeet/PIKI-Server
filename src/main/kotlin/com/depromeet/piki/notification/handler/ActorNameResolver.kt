package com.depromeet.piki.notification.handler

import com.depromeet.piki.user.repository.UserRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 알림 변수(actorName)용 — 행위자 userId 를 화면 표시용 닉네임으로 푼다.
// 토너먼트 알림 템플릿("${actorName}님이 …")이 공유하는 단일 도출 지점이라, fallback 정책도 여기 한 곳에 둔다.
//
// 행위자는 정상 흐름에선 방금 인증된 채로 행동한 유저라 항상 존재한다. 그래도 못 찾으면(데이터 불일치)
// 던지지 않고 fallback 으로 채운다 — 알림은 best-effort 라, 닉네임 하나 때문에 알림 전체를 떨구지 않는다.
@Component
class ActorNameResolver(
    private val userRepository: UserRepository,
) {
    fun resolve(actorId: UUID): String = userRepository.findById(actorId)?.nickname ?: UNKNOWN_ACTOR

    companion object {
        const val UNKNOWN_ACTOR = "알 수 없는 사용자"
    }
}
