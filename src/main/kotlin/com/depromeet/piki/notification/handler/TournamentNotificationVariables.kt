package com.depromeet.piki.notification.handler

import com.depromeet.piki.tournament.repository.TournamentRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 토너먼트 알림 6종이 공유하는 템플릿 변수·프사 도출. actorName(닉네임)·tournamentId·tournamentName 을 한 번의
// actor 조회 + 토너먼트 조회로 채운다. 핸들러마다 중복 배선하지 않게 한 곳에 모은다 — 변수 카탈로그
// (NotificationTemplateVariables)가 선언한 것과 여기서 채우는 키가 항상 일치해야 한다(백오피스 검증·미리보기의 전제).
@Component
class TournamentNotificationVariables(
    private val actorNameResolver: ActorNameResolver,
    private val tournamentRepository: TournamentRepository,
) {
    fun context(
        tournamentId: Long,
        actorId: UUID,
    ): ActorContext {
        val actor = actorNameResolver.resolveAttributes(actorId)
        // 토너먼트가 삭제·불일치로 없으면(best-effort) fallback — 변수 하나 때문에 알림 전체를 떨구지 않는다.
        val tournamentName = tournamentRepository.findTournamentById(tournamentId)?.name ?: FALLBACK_NAME
        return ActorContext(
            variables =
                mapOf(
                    "actorName" to actor.name,
                    "tournamentId" to tournamentId.toString(),
                    "tournamentName" to tournamentName,
                ),
            imageUrl = actor.profileImage,
        )
    }

    companion object {
        private const val FALLBACK_NAME = "토너먼트"
    }
}
