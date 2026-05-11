package com.depromeet.team3.tournament.service

import com.depromeet.team3.common.domain.LongBaseEntity
import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentItem
import com.depromeet.team3.tournament.domain.TournamentStatus
import com.depromeet.team3.tournament.repository.TournamentRepository
import com.depromeet.team3.tournament.service.dto.AddTournamentItems
import com.depromeet.team3.tournament.service.dto.CreateTournament
import com.depromeet.team3.tournament.service.dto.RecordMatch
import com.depromeet.team3.wishlist.domain.Wish
import com.depromeet.team3.wishlist.repository.WishRepository
import com.depromeet.team3.wishlist.service.WishException
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TournamentServiceTest {

    private class TestWishRepository(
        private val allOwned: Boolean = true,
    ) : WishRepository {
        override fun save(wish: Wish): Wish = wish
        override fun existsByGuestIdAndProductLink(guestId: UUID, link: ProductLink): Boolean = false
        override fun countByIdsAndGuestId(ids: List<Long>, guestId: UUID): Long =
            if (allOwned) ids.size.toLong() else 0L
    }

    private class TestTournamentRepository : TournamentRepository {
        private var tournamentIdSeq = 1L
        private var itemIdSeq = 1L
        val tournaments = mutableMapOf<Long, Tournament>()
        val tournamentItems = mutableListOf<TournamentItem>()
        val histories = mutableListOf<TournamentHistory>()

        override fun saveTournament(tournament: Tournament): Long {
            val id = tournamentIdSeq++
            tournaments[id] = tournament
            setEntityId(tournament, id)
            return id
        }

        override fun saveTournamentItems(items: List<TournamentItem>): List<TournamentItem> =
            items.map { item ->
                val id = itemIdSeq++
                setEntityId(item, id)
                tournamentItems.add(item)
                item
            }

        override fun saveHistory(history: TournamentHistory) {
            histories.add(history)
        }

        override fun findTournamentById(tournamentId: Long): Tournament? = tournaments[tournamentId]

        override fun findTournamentItemsByTournamentId(tournamentId: Long): List<TournamentItem> =
            tournamentItems.filter { it.tournamentId == tournamentId }

        override fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory> =
            histories.filter { it.tournamentId == tournamentId }

        private fun setEntityId(entity: LongBaseEntity, id: Long) {
            val field = LongBaseEntity::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(entity, id)
        }
    }

    private val repository = TestTournamentRepository()
    private val wishRepository = TestWishRepository()
    private val service = TournamentService(repository, wishRepository)
    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    private fun createAndStart(itemIds: List<Long>, name: String = "토너먼트"): Long {
        val tournamentId = service.create(userId, CreateTournament(name))
        service.addItems(userId, AddTournamentItems(tournamentId, itemIds))
        service.start(userId, tournamentId)
        return tournamentId
    }

    @Test
    fun `create 는 PENDING 상태로 토너먼트를 생성하고 ID 를 반환한다`() {
        val id = service.create(userId, CreateTournament("내 토너먼트"))

        assertEquals(1L, id)
        assertEquals(TournamentStatus.PENDING, repository.tournaments[id]!!.status)
    }

    @Test
    fun `addItems 는 PENDING 토너먼트에 아이템을 추가한다`() {
        val tournamentId = service.create(userId, CreateTournament("내 토너먼트"))

        service.addItems(userId, AddTournamentItems(tournamentId, (1L..8L).toList()))

        assertEquals(8, repository.findTournamentItemsByTournamentId(tournamentId).size)
    }

    @Test
    fun `addItems 에서 위시 아이템이 요청자의 것이 아니면 예외가 발생한다`() {
        val serviceWithNoOwnership = TournamentService(repository, TestWishRepository(allOwned = false))
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))

        assertFailsWith<WishException> {
            serviceWithNoOwnership.addItems(userId, AddTournamentItems(tournamentId, (1L..4L).toList()))
        }
    }

    @Test
    fun `addItems 에서 PENDING 이 아닌 토너먼트에 추가하면 예외가 발생한다`() {
        val tournamentId = createAndStart(listOf(1L, 2L))

        assertFailsWith<TournamentException> {
            service.addItems(userId, AddTournamentItems(tournamentId, listOf(3L, 4L)))
        }
    }

    @Test
    fun `start 는 PENDING 토너먼트를 IN_PROGRESS 로 전환한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        service.addItems(userId, AddTournamentItems(tournamentId, listOf(1L, 2L)))

        service.start(userId, tournamentId)

        assertEquals(TournamentStatus.IN_PROGRESS, repository.tournaments[tournamentId]!!.status)
    }

    @Test
    fun `start 에서 PENDING 이 아닌 토너먼트면 예외가 발생한다`() {
        val tournamentId = createAndStart(listOf(1L, 2L))

        assertFailsWith<TournamentException> {
            service.start(userId, tournamentId)
        }
    }

    @Test
    fun `recordMatch 는 IN_PROGRESS 토너먼트에 히스토리를 저장한다`() {
        val tournamentId = createAndStart((1L..8L).toList())
        val items = repository.findTournamentItemsByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 1L }!!
        val secondItem = items.find { it.itemId == 2L }!!

        service.recordMatch(
            userId,
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 4,
                firstItemId = firstItem.getId(),
                secondItemId = secondItem.getId(),
                winnerItemId = firstItem.getId(),
            ),
        )

        assertEquals(1, repository.histories.size)
        assertEquals(firstItem.getId(), repository.histories[0].winnerTournamentItemId)
    }

    @Test
    fun `recordMatch 에서 currentRound 가 2 면 토너먼트가 COMPLETED 로 완료된다`() {
        val tournamentId = createAndStart(listOf(10L, 20L))
        val items = repository.findTournamentItemsByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 10L }!!
        val secondItem = items.find { it.itemId == 20L }!!

        service.recordMatch(
            userId,
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 2,
                firstItemId = firstItem.getId(),
                secondItemId = secondItem.getId(),
                winnerItemId = firstItem.getId(),
            ),
        )

        assertEquals(TournamentStatus.COMPLETED, repository.tournaments[tournamentId]!!.status)
    }

    @Test
    fun `recordMatch 에서 PENDING 토너먼트면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        service.addItems(userId, AddTournamentItems(tournamentId, listOf(1L, 2L)))

        assertFailsWith<TournamentException> {
            service.recordMatch(
                userId,
                RecordMatch(tournamentId = tournamentId, currentRound = 2, firstItemId = 1L, secondItemId = 2L, winnerItemId = 1L),
            )
        }
    }

    @Test
    fun `recordMatch 에서 존재하지 않는 tournamentId 면 예외가 발생한다`() {
        assertFailsWith<TournamentException> {
            service.recordMatch(
                userId,
                RecordMatch(tournamentId = 999L, currentRound = 2, firstItemId = 1L, secondItemId = 2L, winnerItemId = 1L),
            )
        }
    }

    @Test
    fun `recordMatch 에서 다른 사용자의 토너먼트면 예외가 발생한다`() {
        val tournamentId = createAndStart((1L..4L).toList())
        val items = repository.findTournamentItemsByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 1L }!!
        val secondItem = items.find { it.itemId == 2L }!!

        assertFailsWith<TournamentException> {
            service.recordMatch(
                otherUserId,
                RecordMatch(
                    tournamentId = tournamentId,
                    currentRound = 4,
                    firstItemId = firstItem.getId(),
                    secondItemId = secondItem.getId(),
                    winnerItemId = firstItem.getId(),
                ),
            )
        }
    }

    @Test
    fun `recordMatch 에서 승자가 대결 아이템이 아니면 예외가 발생한다`() {
        val tournamentId = createAndStart((1L..4L).toList())
        val items = repository.findTournamentItemsByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 1L }!!
        val secondItem = items.find { it.itemId == 2L }!!

        assertFailsWith<TournamentException> {
            service.recordMatch(
                userId,
                RecordMatch(
                    tournamentId = tournamentId,
                    currentRound = 4,
                    firstItemId = firstItem.getId(),
                    secondItemId = secondItem.getId(),
                    winnerItemId = 99L,
                ),
            )
        }
    }

    @Test
    fun `recordMatch 에서 이미 완료된 토너먼트면 예외가 발생한다`() {
        val tournamentId = createAndStart(listOf(10L, 20L))
        val items = repository.findTournamentItemsByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 10L }!!
        val secondItem = items.find { it.itemId == 20L }!!

        val finalMatch = RecordMatch(
            tournamentId = tournamentId,
            currentRound = 2,
            firstItemId = firstItem.getId(),
            secondItemId = secondItem.getId(),
            winnerItemId = firstItem.getId(),
        )
        service.recordMatch(userId, finalMatch)

        assertFailsWith<TournamentException> {
            service.recordMatch(userId, finalMatch)
        }
    }

    @Test
    fun `getTournamentById 는 토너먼트 정보와 히스토리를 반환한다`() {
        val tournamentId = createAndStart((1L..4L).toList(), "조회 토너먼트")
        val items = repository.findTournamentItemsByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 1L }!!
        val secondItem = items.find { it.itemId == 2L }!!

        service.recordMatch(
            userId,
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 4,
                firstItemId = firstItem.getId(),
                secondItemId = secondItem.getId(),
                winnerItemId = secondItem.getId(),
            ),
        )

        val info = service.getTournamentById(tournamentId, userId)

        assertEquals(tournamentId, info.tournamentId)
        assertEquals(4, info.items.size)
        assertEquals(1, info.history.size)
        assertEquals(secondItem.getId(), info.history[0].winnerTournamentItemId)
    }

    @Test
    fun `getTournamentById 는 히스토리가 없어도 빈 리스트로 반환한다`() {
        val tournamentId = service.create(userId, CreateTournament("빈 토너먼트"))

        val info = service.getTournamentById(tournamentId, userId)

        assertEquals(tournamentId, info.tournamentId)
        assertEquals(0, info.items.size)
        assertEquals(0, info.history.size)
    }

    @Test
    fun `getTournamentById 에서 존재하지 않는 tournamentId 면 예외가 발생한다`() {
        assertFailsWith<TournamentException> {
            service.getTournamentById(999L, userId)
        }
    }

    @Test
    fun `getTournamentById 에서 다른 사용자의 토너먼트면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("내 토너먼트"))

        assertFailsWith<TournamentException> {
            service.getTournamentById(tournamentId, otherUserId)
        }
    }
}
