package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentUser
import com.depromeet.piki.tournament.event.TournamentItemAdded
import com.depromeet.piki.tournament.event.TournamentJoined
import com.depromeet.piki.tournament.event.TournamentStarted
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
// 영속 fixture(참가자·위시·토너먼트 아이템·유저)를 깔고 실제 빈으로 도출 결과를 단언한다. @Transactional 자동 롤백.
@Transactional
class NotificationRecipientResolutionIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var itemAddedHandler: TournamentItemAddedHandler

    @Autowired private lateinit var joinedHandler: TournamentJoinedHandler

    @Autowired private lateinit var startedHandler: TournamentStartedHandler

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

        assertEquals(setOf(other1, other2), recipients)
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

        assertEquals(setOf(existing1, existing2), recipients)
    }

    @Test
    fun `토너먼트 시작 수신자는 참가자에서 시작시킨 주최자(actor)를 뺀 집합이다`() {
        val tournamentId = 1007L
        val owner = UUID.randomUUID()
        val participant1 = UUID.randomUUID()
        val participant2 = UUID.randomUUID()
        // 주최자는 본인 화면이 이미 시작 상태로 넘어가므로, 나머지 참가자에게만 시작을 알린다.
        listOf(owner, participant1, participant2).forEach { tournamentUserRepository.save(TournamentUser(tournamentId, it)) }

        val recipients = startedHandler.resolveRecipients(TournamentStarted(tournamentId, owner))

        assertEquals(setOf(participant1, participant2), recipients)
    }

    @Test
    fun `토너먼트 시작 변수 actorName 은 시작시킨 주최자 닉네임이다`() {
        val tournamentId = 1008L
        val owner = UUID.randomUUID()
        userRepository.save(User(id = owner, nickname = "주최자", profileImage = "https://x/p.jpg", identityType = IdentityType.GUEST))

        val variables = startedHandler.resolveVariables(TournamentStarted(tournamentId, owner))

        assertEquals(mapOf("actorName" to "주최자"), variables)
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
    fun `파싱 완료 수신자 - 위시로만 담긴 아이템은 그 위시 주인들이다`() {
        val itemId = 2001L
        val owner1 = UUID.randomUUID()
        val owner2 = UUID.randomUUID()
        wishRepository.save(Wish(owner1, itemId))
        wishRepository.save(Wish(owner2, itemId))

        val recipients = parsingCompletedHandler.resolveRecipients(ItemParsingCompleted(itemId))

        assertEquals(setOf(owner1, owner2), recipients)
    }

    @Test
    fun `파싱 완료 수신자 - 토너먼트로 담긴 아이템은 올린 본인(adder)에게만 가고 다른 참가자는 제외된다`() {
        val itemId = 2002L
        val tournamentId = 1005L
        val adder = UUID.randomUUID()
        val otherParticipant = UUID.randomUUID()
        // otherParticipant 는 추가 시점에 TOURNAMENT_ITEM_ADDED 로 갱신하므로 파싱완료는 안 받는다 — 참가자로 깔아두고 제외를 확인.
        listOf(adder, otherParticipant).forEach { tournamentUserRepository.save(TournamentUser(tournamentId, it)) }
        tournamentItemRepository.saveAll(listOf(TournamentItem(tournamentId, itemId, adder)))

        val recipients = parsingCompletedHandler.resolveRecipients(ItemParsingCompleted(itemId))

        assertEquals(setOf(adder), recipients)
    }

    @Test
    fun `파싱 완료 수신자 - 위시 주인과 토너먼트 adder 의 합집합이다 (그냥 참가자는 제외)`() {
        val itemId = 2003L
        val tournamentId = 1006L
        val wishOwner = UUID.randomUUID()
        val adder = UUID.randomUUID()
        val otherParticipant = UUID.randomUUID()
        wishRepository.save(Wish(wishOwner, itemId))
        listOf(adder, otherParticipant).forEach { tournamentUserRepository.save(TournamentUser(tournamentId, it)) }
        tournamentItemRepository.saveAll(listOf(TournamentItem(tournamentId, itemId, adder)))

        val recipients = parsingCompletedHandler.resolveRecipients(ItemParsingCompleted(itemId))

        assertEquals(setOf(wishOwner, adder), recipients)
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

        assertEquals(setOf(owner), recipients)
    }
}
