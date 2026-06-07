package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.notification.domain.Recipient
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentUser
import com.depromeet.piki.tournament.event.TournamentItemAdded
import com.depromeet.piki.tournament.event.TournamentJoined
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.repository.UserRepository
import com.depromeet.piki.wishlist.domain.Wish
import com.depromeet.piki.wishlist.repository.WishRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 핸들러의 수신자(resolveRecipients)·변수(resolveVariables) 도출은 DB 역조회에 의존하므로 통합으로 검증한다.
// 라우팅은 수신자에 묶이므로(#408) resolveRecipients 가 Recipient(userId, routing) 집합을 돌려준다 — 같은 파싱 아이템이라도
// 위시 주인은 WISH, 토너먼트 adder 는 각자 토너먼트로 갈리는지를 단언한다. @Transactional 자동 롤백.
@Transactional
class NotificationRecipientResolutionIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var itemAddedHandler: TournamentItemAddedHandler

    @Autowired private lateinit var joinedHandler: TournamentJoinedHandler

    @Autowired private lateinit var parsingCompletedHandler: ItemParsingCompletedHandler

    @Autowired private lateinit var parsingFailedHandler: ItemParsingFailedHandler

    @Autowired private lateinit var tournamentUserRepository: TournamentUserRepository

    @Autowired private lateinit var tournamentItemRepository: TournamentItemRepository

    @Autowired private lateinit var wishRepository: WishRepository

    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `토너먼트 아이템 추가 수신자는 참가자에서 추가한 본인을 뺀 집합이다`() {
        val tournamentId = 1001L
        val actor = UUID.randomUUID()
        val other1 = UUID.randomUUID()
        val other2 = UUID.randomUUID()
        listOf(actor, other1, other2).forEach { tournamentUserRepository.save(TournamentUser(tournamentId, it)) }

        val recipients = itemAddedHandler.resolveRecipients(TournamentItemAdded(tournamentId, actor))

        // 토너먼트 알림은 라우팅 컨텍스트가 없다(refId=tournamentId 로 충분) — Recipient.routing 은 전부 null.
        assertEquals(setOf(other1, other2), recipients.map { it.userId }.toSet())
        assertTrue(recipients.mapNotNull { it.routing }.isEmpty())
    }

    @Test
    fun `토너먼트 참여 수신자는 기존 참가자에서 새로 들어온 본인을 뺀 집합이다`() {
        val tournamentId = 1002L
        val joiner = UUID.randomUUID()
        val existing1 = UUID.randomUUID()
        val existing2 = UUID.randomUUID()
        // AFTER_COMMIT 라 참여자 본인도 이미 목록에 있다고 보고, 본인을 제외하는지 본다.
        listOf(joiner, existing1, existing2).forEach { tournamentUserRepository.save(TournamentUser(tournamentId, it)) }

        val recipients = joinedHandler.resolveRecipients(TournamentJoined(tournamentId, joiner))

        assertEquals(setOf(existing1, existing2), recipients.map { it.userId }.toSet())
    }

    @Test
    fun `토너먼트 아이템 추가 변수 actorName 은 행위자 닉네임이다`() {
        val tournamentId = 1003L
        val actor = UUID.randomUUID()
        userRepository.save(User(id = actor, nickname = "홍길동", profileImage = "https://x/p.jpg", identityType = IdentityType.GUEST))

        val variables = itemAddedHandler.resolveVariables(TournamentItemAdded(tournamentId, actor))

        assertEquals(mapOf("actorName" to "홍길동"), variables)
    }

    @Test
    fun `행위자 유저를 못 찾으면 actorName 은 fallback 으로 채운다`() {
        val variables = itemAddedHandler.resolveVariables(TournamentItemAdded(1004L, UUID.randomUUID()))

        assertEquals(mapOf("actorName" to ActorNameResolver.UNKNOWN_ACTOR), variables)
    }

    @Test
    fun `파싱 완료 수신자 - 위시로만 담긴 아이템은 그 위시 주인들이고 모두 WISH 라우팅이다`() {
        val itemId = 2001L
        val owner1 = UUID.randomUUID()
        val owner2 = UUID.randomUUID()
        wishRepository.save(Wish(owner1, itemId))
        wishRepository.save(Wish(owner2, itemId))

        val recipients = parsingCompletedHandler.resolveRecipients(ItemParsingCompleted(itemId))

        assertEquals(
            setOf(
                Recipient(owner1, NotificationRouting.Wish),
                Recipient(owner2, NotificationRouting.Wish),
            ),
            recipients,
        )
    }

    @Test
    fun `파싱 완료 수신자 - 토너먼트로 담긴 아이템은 올린 본인이고 그 토너먼트 좌표로 라우팅된다`() {
        val itemId = 2002L
        val tournamentId = 1005L
        val adder = UUID.randomUUID()
        val otherParticipant = UUID.randomUUID()
        // otherParticipant 는 추가 시점에 TOURNAMENT_ITEM_ADDED 로 갱신하므로 파싱완료는 안 받는다 — 참가자로 깔아두고 제외를 확인.
        listOf(adder, otherParticipant).forEach { tournamentUserRepository.save(TournamentUser(tournamentId, it)) }
        val tournamentItem = tournamentItemRepository.saveAll(listOf(TournamentItem(tournamentId, itemId, adder))).first()

        val recipients = parsingCompletedHandler.resolveRecipients(ItemParsingCompleted(itemId))

        assertEquals(
            setOf(Recipient(adder, NotificationRouting.Tournament(tournamentId, tournamentItem.getId()))),
            recipients,
        )
    }

    @Test
    fun `파싱 완료 수신자 - 위시 주인은 WISH, 토너먼트 adder 는 각자 토너먼트로 라우팅된다 (수신자별 라우팅)`() {
        val itemId = 2003L
        val tournamentId = 1006L
        val wishOwner = UUID.randomUUID()
        val adder = UUID.randomUUID()
        val otherParticipant = UUID.randomUUID()
        wishRepository.save(Wish(wishOwner, itemId))
        listOf(adder, otherParticipant).forEach { tournamentUserRepository.save(TournamentUser(tournamentId, it)) }
        val tournamentItem = tournamentItemRepository.saveAll(listOf(TournamentItem(tournamentId, itemId, adder))).first()

        val recipients = parsingCompletedHandler.resolveRecipients(ItemParsingCompleted(itemId))

        // 핵심: 같은 아이템이어도 위시 주인은 /archive(WISH), adder 는 자기 토너먼트로 — 라우팅이 수신자마다 갈린다.
        assertEquals(
            setOf(
                Recipient(wishOwner, NotificationRouting.Wish),
                Recipient(adder, NotificationRouting.Tournament(tournamentId, tournamentItem.getId())),
            ),
            recipients,
        )
    }

    @Test
    fun `파싱 완료 수신자 - 한 유저가 위시·토너먼트 양쪽이면 위시를 우선한다`() {
        val itemId = 2005L
        val tournamentId = 1007L
        val user = UUID.randomUUID()
        wishRepository.save(Wish(user, itemId))
        tournamentItemRepository.saveAll(listOf(TournamentItem(tournamentId, itemId, user)))

        val recipients = parsingCompletedHandler.resolveRecipients(ItemParsingCompleted(itemId))

        assertEquals(setOf(Recipient(user, NotificationRouting.Wish)), recipients)
    }

    @Test
    fun `파싱 완료 수신자 - 어디에도 안 담긴 아이템은 빈 집합이다`() {
        val recipients = parsingCompletedHandler.resolveRecipients(ItemParsingCompleted(999_999L))

        assertTrue(recipients.isEmpty())
    }

    @Test
    fun `파싱 실패 핸들러도 완료와 동일한 역조회 규칙을 쓴다`() {
        val itemId = 2004L
        val owner = UUID.randomUUID()
        wishRepository.save(Wish(owner, itemId))

        val recipients = parsingFailedHandler.resolveRecipients(ItemParsingFailed(itemId))

        assertEquals(setOf(Recipient(owner, NotificationRouting.Wish)), recipients)
    }
}
