package com.depromeet.team3.tournament.service

import com.depromeet.team3.common.domain.LongBaseEntity
import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentItem
import com.depromeet.team3.tournament.domain.TournamentStatus
import com.depromeet.team3.tournament.domain.TournamentUser
import com.depromeet.team3.tournament.repository.TournamentItemRepository
import com.depromeet.team3.tournament.repository.TournamentRepository
import com.depromeet.team3.tournament.repository.TournamentUserRepository
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
    private class TestTournamentUserRepository : TournamentUserRepository {
        val users = mutableListOf<TournamentUser>()
        private var idSeq = 1L

        override fun save(tournamentUser: TournamentUser): TournamentUser {
            val id = idSeq++
            setEntityId(tournamentUser, id)
            users.add(tournamentUser)
            return tournamentUser
        }

        private fun setEntityId(
            entity: LongBaseEntity,
            id: Long,
        ) {
            val field = LongBaseEntity::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(entity, id)
        }
    }

    private class TestWishRepository(
        private val allOwned: Boolean = true,
    ) : WishRepository {
        override fun save(wish: Wish): Wish = wish

        override fun countByIdsAndUserId(
            ids: List<Long>,
            userId: UUID,
        ): Long = if (allOwned) ids.size.toLong() else 0L
    }

    private class TestTournamentItemRepository : TournamentItemRepository {
        val items = mutableListOf<TournamentItem>()
        private var idSeq = 1L

        override fun saveAll(items: List<TournamentItem>): List<TournamentItem> =
            items.map { item ->
                val id = idSeq++
                setEntityId(item, id)
                this.items.add(item)
                item
            }

        override fun findAllByTournamentId(tournamentId: Long): List<TournamentItem> =
            items.filter { it.tournamentId == tournamentId }

        private fun setEntityId(
            entity: LongBaseEntity,
            id: Long,
        ) {
            val field = LongBaseEntity::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(entity, id)
        }
    }

    private class TestTournamentRepository : TournamentRepository {
        private var tournamentIdSeq = 1L
        val tournaments = mutableMapOf<Long, Tournament>()
        val histories = mutableListOf<TournamentHistory>()

        override fun saveTournament(tournament: Tournament): Tournament {
            val id = tournamentIdSeq++
            setEntityId(tournament, id)
            tournaments[id] = tournament
            return tournament
        }

        override fun saveHistory(history: TournamentHistory) {
            histories.add(history)
        }

        override fun findTournamentById(tournamentId: Long): Tournament? = tournaments[tournamentId]

        override fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory> =
            histories.filter { it.tournamentId == tournamentId }

        private fun setEntityId(
            entity: LongBaseEntity,
            id: Long,
        ) {
            val field = LongBaseEntity::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(entity, id)
        }
    }

    private val itemRepository = TestTournamentItemRepository()
    private val userRepository = TestTournamentUserRepository()
    private val repository = TestTournamentRepository()
    private val wishRepository = TestWishRepository()
    private val service = TournamentService(userRepository, repository, itemRepository, wishRepository)
    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    private fun createAndStart(
        itemIds: List<Long>,
        name: String = "토너먼트",
    ): Long {
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

        assertEquals(8, itemRepository.findAllByTournamentId(tournamentId).size)
    }

    @Test
    fun `addItems 에서 위시 아이템이 요청자의 것이 아니면 예외가 발생한다`() {
        val serviceWithNoOwnership =
            TournamentService(userRepository, repository, itemRepository, TestWishRepository(allOwned = false))
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
    fun `start 에서 아이템이 없으면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))

        assertFailsWith<TournamentException> {
            service.start(userId, tournamentId)
        }
    }

    @Test
    fun `start 에서 아이템이 1개면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        service.addItems(userId, AddTournamentItems(tournamentId, listOf(1L)))

        assertFailsWith<TournamentException> {
            service.start(userId, tournamentId)
        }
    }

    @Test
    fun `start 에서 아이템이 33개면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        service.addItems(userId, AddTournamentItems(tournamentId, (1L..33L).toList()))

        assertFailsWith<TournamentException> {
            service.start(userId, tournamentId)
        }
    }

    @Test
    fun `start 에서 아이템이 32개면 정상적으로 시작된다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        service.addItems(userId, AddTournamentItems(tournamentId, (1L..32L).toList()))

        service.start(userId, tournamentId)

        assertEquals(TournamentStatus.IN_PROGRESS, repository.tournaments[tournamentId]!!.status)
    }

    @Test
    fun `recordMatch 는 IN_PROGRESS 토너먼트에 히스토리를 저장한다`() {
        val tournamentId = createAndStart((1L..8L).toList())
        val items = itemRepository.findAllByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 1L }!!
        val secondItem = items.find { it.itemId == 2L }!!

        service.recordMatch(
            userId,
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 4,
                firstTournamentItemId = firstItem.getId(),
                secondTournamentItemId = secondItem.getId(),
                selectedTournamentItemId = firstItem.getId(),
            ),
        )

        assertEquals(1, repository.histories.size)
        assertEquals(firstItem.getId(), repository.histories[0].selectedTournamentItemId)
    }

    @Test
    fun `recordMatch 에서 currentRound 가 2 면 토너먼트가 COMPLETED 로 완료된다`() {
        val tournamentId = createAndStart(listOf(10L, 20L))
        val items = itemRepository.findAllByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 10L }!!
        val secondItem = items.find { it.itemId == 20L }!!

        service.recordMatch(
            userId,
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 2,
                firstTournamentItemId = firstItem.getId(),
                secondTournamentItemId = secondItem.getId(),
                selectedTournamentItemId = firstItem.getId(),
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
                RecordMatch(
                    tournamentId = tournamentId,
                    currentRound = 2,
                    firstTournamentItemId = 1L,
                    secondTournamentItemId = 2L,
                    selectedTournamentItemId = 1L,
                ),
            )
        }
    }

    @Test
    fun `recordMatch 에서 존재하지 않는 tournamentId 면 예외가 발생한다`() {
        assertFailsWith<TournamentException> {
            service.recordMatch(
                userId,
                RecordMatch(
                    tournamentId = 999L,
                    currentRound = 2,
                    firstTournamentItemId = 1L,
                    secondTournamentItemId = 2L,
                    selectedTournamentItemId = 1L,
                ),
            )
        }
    }

    @Test
    fun `recordMatch 에서 승자가 대결 아이템이 아니면 예외가 발생한다`() {
        val tournamentId = createAndStart((1L..4L).toList())
        val items = itemRepository.findAllByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 1L }!!
        val secondItem = items.find { it.itemId == 2L }!!

        assertFailsWith<TournamentException> {
            service.recordMatch(
                userId,
                RecordMatch(
                    tournamentId = tournamentId,
                    currentRound = 4,
                    firstTournamentItemId = firstItem.getId(),
                    secondTournamentItemId = secondItem.getId(),
                    selectedTournamentItemId = 99L,
                ),
            )
        }
    }

    @Test
    fun `recordMatch 에서 이미 완료된 토너먼트면 예외가 발생한다`() {
        val tournamentId = createAndStart(listOf(10L, 20L))
        val items = itemRepository.findAllByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 10L }!!
        val secondItem = items.find { it.itemId == 20L }!!

        val finalMatch =
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 2,
                firstTournamentItemId = firstItem.getId(),
                secondTournamentItemId = secondItem.getId(),
                selectedTournamentItemId = firstItem.getId(),
            )
        service.recordMatch(userId, finalMatch)

        assertFailsWith<TournamentException> {
            service.recordMatch(userId, finalMatch)
        }
    }

    @Test
    fun `getTournamentById 는 토너먼트 정보와 히스토리를 반환한다`() {
        val tournamentId = createAndStart((1L..4L).toList(), "조회 토너먼트")
        val items = itemRepository.findAllByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 1L }!!
        val secondItem = items.find { it.itemId == 2L }!!

        service.recordMatch(
            userId,
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 4,
                firstTournamentItemId = firstItem.getId(),
                secondTournamentItemId = secondItem.getId(),
                selectedTournamentItemId = secondItem.getId(),
            ),
        )

        val info = service.getTournamentById(tournamentId, userId)

        assertEquals(tournamentId, info.tournamentId)
        assertEquals(4, info.items.size)
        assertEquals(1, info.history.size)
        assertEquals(secondItem.getId(), info.history[0].selectedTournamentItemId)
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
}
