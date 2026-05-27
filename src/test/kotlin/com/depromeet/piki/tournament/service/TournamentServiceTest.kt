package com.depromeet.piki.tournament.service

import com.depromeet.piki.common.domain.LongBaseEntity
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentHistory
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.domain.TournamentUser
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import com.depromeet.piki.tournament.service.dto.AddTournamentItemsFromWish
import com.depromeet.piki.tournament.service.dto.CreateTournament
import com.depromeet.piki.tournament.service.dto.RecordMatch
import com.depromeet.piki.tournament.service.dto.TournamentDetail
import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.repository.UserRepository
import com.depromeet.piki.wishlist.domain.Wish
import com.depromeet.piki.wishlist.domain.WishCursor
import com.depromeet.piki.wishlist.repository.WishRepository
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

        override fun findByTournamentIdAndUserId(
            tournamentId: Long,
            userId: UUID,
        ): TournamentUser? = users.find { it.tournamentId == tournamentId && it.userId == userId }

        override fun findTournamentIdsByUserId(userId: UUID): List<Long> =
            users.filter { it.userId == userId }.map { it.tournamentId }

        override fun findByTournamentId(tournamentId: Long): List<TournamentUser> =
            users.filter { it.tournamentId == tournamentId }

        override fun findByTournamentIds(tournamentIds: List<Long>): List<TournamentUser> =
            users.filter { it.tournamentId in tournamentIds }

        private fun setEntityId(
            entity: LongBaseEntity,
            id: Long,
        ) {
            val field = LongBaseEntity::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(entity, id)
        }
    }

    private class TestItemRepository : ItemRepository {
        var validIds: Set<Long>? = null // null = 모든 ID 유효
        val statusOverrides: MutableMap<Long, ItemStatus> = mutableMapOf()
        // null 가격 시나리오를 명시적으로 테스트할 때 키를 추가한다. 키 없으면 DEFAULT_PRICE 사용.
        val priceOverrides: MutableMap<Long, Int?> = mutableMapOf()

        override fun save(item: Item): Item = item

        override fun saveAll(items: List<Item>): List<Item> = items

        override fun findById(id: Long): Item? = null

        override fun findByIds(ids: List<Long>): List<Item> {
            val effective = validIds?.let { valid -> ids.filter { it in valid } } ?: ids
            return effective.map { id ->
                Item(
                    status = statusOverrides[id] ?: ItemStatus.READY,
                    currentPrice = if (id in priceOverrides) priceOverrides[id] else DEFAULT_PRICE,
                ).also { item ->
                    val field = LongBaseEntity::class.java.getDeclaredField("id")
                    field.isAccessible = true
                    field.set(item, id)
                }
            }
        }

        override fun findStaleProcessingIds(cutoff: java.time.LocalDateTime): List<Long> = emptyList()

        companion object {
            const val DEFAULT_PRICE = 10_000
        }
    }

    private class TestWishRepository : WishRepository {
        // userId → itemIds 매핑. 위시에 등록된 것만 카운트된다.
        val wishItemIdsByUser: MutableMap<UUID, MutableSet<Long>> = mutableMapOf()

        fun addWish(
            userId: UUID,
            vararg itemIds: Long,
        ) {
            wishItemIdsByUser.getOrPut(userId) { mutableSetOf() }.addAll(itemIds.toList())
        }

        override fun save(wish: Wish): Wish = wish

        override fun countByIdsAndUserId(
            ids: List<Long>,
            userId: UUID,
        ): Long = 0L

        override fun countByItemIdsAndUserId(
            itemIds: List<Long>,
            userId: UUID,
        ): Long {
            val owned = wishItemIdsByUser[userId] ?: emptySet()
            return itemIds.count { it in owned }.toLong()
        }

        override fun findPage(
            userId: UUID,
            cursor: WishCursor?,
            limit: Int,
        ): List<Wish> = emptyList()

        override fun findById(id: Long): Wish? = null
    }

    private class TestUserRepository : UserRepository {
        private val users = mutableMapOf<UUID, User>()

        fun add(user: User) {
            users[user.id] = user
        }

        override fun save(user: User): User = user.also { users[it.id] = it }

        override fun findById(id: UUID): User? = users[id]

        override fun findByIds(ids: Collection<UUID>): List<User> = ids.mapNotNull { users[it] }

        override fun existsByNickname(nickname: String): Boolean = users.values.any { it.nickname == nickname }

        override fun existsByNicknameAndIdNot(
            nickname: String,
            excludeUserId: UUID,
        ): Boolean = users.values.any { it.nickname == nickname && it.id != excludeUserId }
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

        override fun countByTournamentId(tournamentId: Long): Int = items.count { it.tournamentId == tournamentId }

        override fun findAllByTournamentId(tournamentId: Long): List<TournamentItem> =
            items.filter { it.tournamentId == tournamentId }

        override fun findById(id: Long): TournamentItem? = items.find { it.getId() == id }

        override fun delete(tournamentItem: TournamentItem) {
            items.remove(tournamentItem)
        }

        override fun deleteIfPending(
            id: Long,
            tournamentId: Long,
        ): Int {
            val item = items.find { it.getId() == id && it.tournamentId == tournamentId } ?: return 0
            items.remove(item)
            return 1
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

    private class TestTournamentRepository : TournamentRepository {
        private var tournamentIdSeq = 1L
        private var historyIdSeq = 1L
        val tournaments = mutableMapOf<Long, Tournament>()
        val histories = mutableListOf<TournamentHistory>()

        override fun saveTournament(tournament: Tournament): Tournament {
            val id = tournamentIdSeq++
            setEntityId(tournament, id)
            tournaments[id] = tournament
            return tournament
        }

        override fun saveHistory(history: TournamentHistory) {
            setEntityId(history, historyIdSeq++)
            histories.add(history)
        }

        override fun findTournamentById(tournamentId: Long): Tournament? = tournaments[tournamentId]

        override fun findTournamentByIdForUpdate(tournamentId: Long): Tournament? = tournaments[tournamentId]

        override fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory> =
            histories.filter { it.tournamentId == tournamentId }

        override fun findByIdsAndStatuses(
            ids: List<Long>,
            statuses: List<TournamentStatus>?,
        ): List<Tournament> =
            tournaments.values
                .filter { it.getId() in ids }
                .filter { statuses.isNullOrEmpty() || it.status in statuses }
                .sortedByDescending { it.createdAt }

        private fun setEntityId(
            entity: LongBaseEntity,
            id: Long,
        ) {
            val field = LongBaseEntity::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(entity, id)
        }
    }

    private val tournamentItemRepository = TestTournamentItemRepository()
    private val tournamentUserRepository = TestTournamentUserRepository()
    private val repository = TestTournamentRepository()
    private val testUserRepository = TestUserRepository()
    private val testItemRepository = TestItemRepository()
    private val testWishRepository = TestWishRepository()
    private val service =
        TournamentService(
            tournamentUserRepository,
            repository,
            tournamentItemRepository,
            testUserRepository,
            testItemRepository,
            testWishRepository,
        )
    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    private fun createAndStart(
        itemIds: List<Long>,
        name: String = "토너먼트",
    ): Long {
        val tournamentId = service.create(userId, CreateTournament(name))
        testWishRepository.addWish(userId, *itemIds.toLongArray())
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, itemIds))
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
        testWishRepository.addWish(userId, *(1L..8L).toList().toLongArray())

        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, (1L..8L).toList()))

        assertEquals(8, tournamentItemRepository.findAllByTournamentId(tournamentId).size)
    }

    @Test
    fun `addItems 에서 토너먼트 참여자가 아니면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))

        val ex =
            assertFailsWith<TournamentException> {
                service.addItemsFromWish(otherUserId, AddTournamentItemsFromWish(tournamentId, (1L..4L).toList()))
            }
        assertEquals(HttpStatus.FORBIDDEN, ex.httpStatus)
    }

    @Test
    fun `addItems 에서 토너먼트 참여자가 아니면 중복 아이템이어도 권한 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, 1L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L)))

        val ex =
            assertFailsWith<TournamentException> {
                service.addItemsFromWish(otherUserId, AddTournamentItemsFromWish(tournamentId, listOf(1L)))
            }
        assertEquals(HttpStatus.FORBIDDEN, ex.httpStatus)
    }

    @Test
    fun `addItems 에서 기존 아이템과 합산해 32개를 초과하면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, *(1L..33L).toList().toLongArray())
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, (1L..32L).toList()))

        val ex =
            assertFailsWith<TournamentException> {
                service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(33L)))
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    @Test
    fun `addItems 에서 PENDING 이 아닌 토너먼트에 추가하면 예외가 발생한다`() {
        val tournamentId = createAndStart(listOf(1L, 2L))

        assertFailsWith<TournamentException> {
            service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(3L, 4L)))
        }
    }

    @Test
    fun `addItems 에서 요청 내 동일한 itemId 가 중복되면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))

        assertFailsWith<TournamentException> {
            service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 1L, 2L)))
        }
    }

    @Test
    fun `addItems 에서 이미 등록된 itemId 를 다시 추가하면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))

        assertFailsWith<TournamentException> {
            service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L)))
        }
    }

    @Test
    fun `addItems 에서 존재하지 않는 itemId 이면 예외가 발생한다`() {
        testItemRepository.validIds = setOf(1L, 2L)
        testWishRepository.addWish(userId, 1L, 999L)
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))

        val ex =
            assertFailsWith<TournamentException> {
                service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 999L)))
            }
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `start 는 PENDING 토너먼트를 IN_PROGRESS 로 전환한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))

        service.start(userId, tournamentId)

        assertEquals(TournamentStatus.IN_PROGRESS, repository.tournaments[tournamentId]!!.status)
    }

    @Test
    fun `start 에서 소유자가 아니면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))

        assertFailsWith<TournamentException> {
            service.start(otherUserId, tournamentId)
        }
    }

    @Test
    fun `start 에서 PENDING 이 아닌 토너먼트면 예외가 발생한다`() {
        val tournamentId = createAndStart(listOf(1L, 2L))

        assertFailsWith<TournamentException> {
            service.start(userId, tournamentId)
        }
    }

    @Test
    fun `start 에서 PROCESSING 아이템이 있으면 409 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))
        testItemRepository.statusOverrides[1L] = ItemStatus.PROCESSING

        val ex = assertFailsWith<TournamentException> { service.start(userId, tournamentId) }
        assertEquals(HttpStatus.CONFLICT, ex.httpStatus)
    }

    @Test
    fun `start 에서 FAILED 아이템이 있으면 409 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))
        testItemRepository.statusOverrides[1L] = ItemStatus.FAILED

        val ex = assertFailsWith<TournamentException> { service.start(userId, tournamentId) }
        assertEquals(HttpStatus.CONFLICT, ex.httpStatus)
    }

    @Test
    fun `start 에서 가격이 없는 아이템이 있으면 409 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))
        testItemRepository.priceOverrides[1L] = null

        val ex = assertFailsWith<TournamentException> { service.start(userId, tournamentId) }
        assertEquals(HttpStatus.CONFLICT, ex.httpStatus)
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
        testWishRepository.addWish(userId, 1L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L)))

        assertFailsWith<TournamentException> {
            service.start(userId, tournamentId)
        }
    }

    @Test
    fun `start 에서 아이템이 32개면 정상적으로 시작된다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, *(1L..32L).toList().toLongArray())
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, (1L..32L).toList()))

        service.start(userId, tournamentId)

        assertEquals(TournamentStatus.IN_PROGRESS, repository.tournaments[tournamentId]!!.status)
    }

    @Test
    fun `recordMatch 는 IN_PROGRESS 토너먼트에 히스토리를 저장한다`() {
        val tournamentId = createAndStart((1L..4L).toList())
        val items = tournamentItemRepository.findAllByTournamentId(tournamentId)
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
        val items = tournamentItemRepository.findAllByTournamentId(tournamentId)
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
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))

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
    fun `recordMatch 에서 참가자가 아니면 예외가 발생한다`() {
        val tournamentId = createAndStart((1L..4L).toList())
        val items = tournamentItemRepository.findAllByTournamentId(tournamentId)
        val firstItem = items.find { it.itemId == 1L }!!
        val secondItem = items.find { it.itemId == 2L }!!

        assertFailsWith<TournamentException> {
            service.recordMatch(
                otherUserId,
                RecordMatch(
                    tournamentId = tournamentId,
                    currentRound = 4,
                    firstTournamentItemId = firstItem.getId(),
                    secondTournamentItemId = secondItem.getId(),
                    selectedTournamentItemId = firstItem.getId(),
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
        val items = tournamentItemRepository.findAllByTournamentId(tournamentId)
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
    fun `recordMatch 에서 currentRound 가 예상 라운드와 다르면 예외가 발생한다`() {
        // 4개 아이템 토너먼트: 첫 라운드는 currentRound=4 여야 한다
        val tournamentId = createAndStart((1L..4L).toList())
        val items = tournamentItemRepository.findAllByTournamentId(tournamentId)

        val ex =
            assertFailsWith<TournamentException> {
                service.recordMatch(
                    userId,
                    RecordMatch(
                        tournamentId = tournamentId,
                        currentRound = 2,
                        firstTournamentItemId = items[0].getId(),
                        secondTournamentItemId = items[1].getId(),
                        selectedTournamentItemId = items[0].getId(),
                    ),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    @Test
    fun `recordMatch 에서 이미 완료된 토너먼트면 예외가 발생한다`() {
        val tournamentId = createAndStart(listOf(10L, 20L))
        val items = tournamentItemRepository.findAllByTournamentId(tournamentId)
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
    fun `getTournamentById 는 IN_PROGRESS 토너먼트에서 마지막 히스토리와 미등장 아이템을 가격 오름차순으로 반환한다`() {
        val tournamentId = createAndStart((1L..4L).toList())
        val items = tournamentItemRepository.findAllByTournamentId(tournamentId)
        // ti[0](price=10000) vs ti[1](price=10000) — 기본가 동일하므로 tournamentItemId 정렬이 tie-break
        service.recordMatch(userId, RecordMatch(tournamentId, 4, items[0].getId(), items[1].getId(), items[0].getId()))

        val detail = assertIs<TournamentDetail.InProgress>(service.getTournamentById(tournamentId, userId))

        assertEquals(tournamentId, detail.tournamentId)
        assertEquals(4, detail.currentRound)
        assertEquals(items[0].getId(), detail.lastHistory?.firstTournamentItemId)
        assertEquals(items[1].getId(), detail.lastHistory?.secondTournamentItemId)
        assertEquals(items[0].getId(), detail.lastHistory?.selectedTournamentItemId)
        // ti[0] 은 이미 round-4 대결 → remaining = ti[2], ti[3]
        assertEquals(2, detail.remainingItems.size)
        assertEquals(items[2].getId(), detail.remainingItems[0].tournamentItemId)
        assertEquals(items[3].getId(), detail.remainingItems[1].tournamentItemId)
    }

    @Test
    fun `getTournamentById 는 IN_PROGRESS 토너먼트에서 아직 매치가 없으면 lastHistory 가 null 이고 전체 아이템을 반환한다`() {
        val tournamentId = createAndStart((1L..4L).toList())

        val detail = assertIs<TournamentDetail.InProgress>(service.getTournamentById(tournamentId, userId))

        assertEquals(4, detail.currentRound)
        assertEquals(null, detail.lastHistory)
        assertEquals(4, detail.remainingItems.size)
    }

    @Test
    fun `getTournamentById 는 IN_PROGRESS 토너먼트에서 다중 라운드 진행 후 마지막 히스토리가 가장 최근 매치를 가리킨다`() {
        // 4개 아이템: round-4 매치 2개 후 round-2(결승) 직전 상태
        val tournamentId = createAndStart((1L..4L).toList())
        val items = tournamentItemRepository.findAllByTournamentId(tournamentId)
        service.recordMatch(userId, RecordMatch(tournamentId, 4, items[0].getId(), items[1].getId(), items[0].getId()))
        service.recordMatch(userId, RecordMatch(tournamentId, 4, items[2].getId(), items[3].getId(), items[2].getId()))
        // 여기까지 round-4 2경기 완료, 아직 round-2 결승 미진행 → IN_PROGRESS

        val detail = assertIs<TournamentDetail.InProgress>(service.getTournamentById(tournamentId, userId))

        // currentRound 는 round-2(결승)
        assertEquals(2, detail.currentRound)
        // lastHistory 는 두 번째로 기록된 round-4 매치 (가장 최근)
        assertEquals(4, detail.lastHistory?.currentRound)
        assertEquals(items[2].getId(), detail.lastHistory?.firstTournamentItemId)
        assertEquals(items[3].getId(), detail.lastHistory?.secondTournamentItemId)
        // round-4 승자 2명이 round-2 대결 대기 중
        assertEquals(2, detail.remainingItems.size)
        assertEquals(items[0].getId(), detail.remainingItems[0].tournamentItemId)
        assertEquals(items[2].getId(), detail.remainingItems[1].tournamentItemId)
    }

    @Test
    fun `getTournamentById 는 PENDING 토너먼트의 아이템 목록을 반환한다`() {
        val tournamentId = service.create(userId, CreateTournament("빈 토너먼트"))

        val detail = assertIs<TournamentDetail.Pending>(service.getTournamentById(tournamentId, userId))

        assertEquals(tournamentId, detail.tournamentId)
        assertEquals(0, detail.items.size)
    }

    @Test
    fun `getTournamentById 는 PENDING 토너먼트의 참여자 정보를 반환한다`() {
        testUserRepository.add(
            User(
                id = userId,
                nickname = "테스터",
                profileImage = "https://cdn.example.com/test.jpg",
                identityType = IdentityType.MEMBER,
            ),
        )
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))

        val detail = assertIs<TournamentDetail.Pending>(service.getTournamentById(tournamentId, userId))

        assertEquals(1, detail.participants.size)
        assertEquals(userId, detail.participants[0].userId)
        assertEquals("테스터", detail.participants[0].nickname)
        assertEquals("https://cdn.example.com/test.jpg", detail.participants[0].profileImage)
    }

    // TODO: COMPLETED 조회 구현 후 테스트 추가

    @Test
    fun `getTournamentById 에서 참가자가 아니면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))

        assertFailsWith<TournamentException> {
            service.getTournamentById(tournamentId, otherUserId)
        }
    }

    @Test
    fun `getTournamentById 에서 존재하지 않는 tournamentId 면 예외가 발생한다`() {
        assertFailsWith<TournamentException> {
            service.getTournamentById(999L, userId)
        }
    }

    @Test
    fun `3개 아이템 토너먼트에서 모든 라운드를 진행하면 COMPLETED 로 완료된다`() {
        val tournamentId = createAndStart((1L..3L).toList())
        val allItems = tournamentItemRepository.findAllByTournamentId(tournamentId)
        // generate() 는 입력 순서대로 페어링: allItems[0] vs allItems[1], 부전승: allItems[2]
        val paired0 = allItems[0]
        val paired1 = allItems[1]
        val leftover = allItems[2]

        service.recordMatch(userId, RecordMatch(tournamentId, 3, paired0.getId(), paired1.getId(), paired0.getId()))
        service.recordMatch(userId, RecordMatch(tournamentId, 2, paired0.getId(), leftover.getId(), paired0.getId()))

        assertEquals(TournamentStatus.COMPLETED, repository.tournaments[tournamentId]!!.status)
    }

    @Test
    fun `6개 아이템 토너먼트에서 모든 라운드를 진행하면 COMPLETED 로 완료된다`() {
        val tournamentId = createAndStart((1L..6L).toList())
        val allItems = tournamentItemRepository.findAllByTournamentId(tournamentId)
        // generate() 는 입력 순서대로 페어링: (0,1), (2,3), (4,5) — 3경기
        val pairs = allItems.chunked(2)
        assertEquals(3, pairs.size)

        pairs.forEach { (first, second) ->
            service.recordMatch(userId, RecordMatch(tournamentId, 6, first.getId(), second.getId(), first.getId()))
        }
        service.recordMatch(userId, RecordMatch(tournamentId, 3, pairs[0][0].getId(), pairs[1][0].getId(), pairs[0][0].getId()))
        service.recordMatch(userId, RecordMatch(tournamentId, 2, pairs[0][0].getId(), pairs[2][0].getId(), pairs[0][0].getId()))

        assertEquals(TournamentStatus.COMPLETED, repository.tournaments[tournamentId]!!.status)
    }

    @Test
    fun `getTournaments 는 자신이 참여한 토너먼트만 반환한다`() {
        service.create(userId, CreateTournament("내 토너먼트1"))
        service.create(userId, CreateTournament("내 토너먼트2"))
        service.create(otherUserId, CreateTournament("남의 토너먼트"))

        val result = service.getTournaments(userId, null)

        assertEquals(2, result.size)
        assertTrue(result.all { r -> repository.tournaments.containsKey(r.tournamentId) })
    }

    @Test
    fun `getTournaments 에서 참여한 토너먼트가 없으면 빈 리스트를 반환한다`() {
        val result = service.getTournaments(userId, null)

        assertEquals(0, result.size)
    }

    @Test
    fun `getTournaments 에서 status 필터가 주어지면 해당 상태만 반환한다`() {
        service.create(userId, CreateTournament("대기중"))
        val startedId = service.create(userId, CreateTournament("진행중"))
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(startedId, listOf(1L, 2L)))
        service.start(userId, startedId)

        val result = service.getTournaments(userId, listOf(TournamentStatus.PENDING))

        assertEquals(1, result.size)
        assertEquals("대기중", result[0].name)
    }

    @Test
    fun `getTournaments 에서 복수 status 필터가 주어지면 해당 상태들만 반환한다`() {
        service.create(userId, CreateTournament("대기중"))
        val startedId = service.create(userId, CreateTournament("진행중"))
        testWishRepository.addWish(userId, 1L, 2L, 3L, 4L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(startedId, listOf(1L, 2L)))
        service.start(userId, startedId)
        val completedId = service.create(userId, CreateTournament("완료됨"))
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(completedId, listOf(3L, 4L)))
        service.start(userId, completedId)
        val completedItems = tournamentItemRepository.findAllByTournamentId(completedId)
        service.recordMatch(
            userId,
            RecordMatch(
                completedId,
                2,
                completedItems[0].getId(),
                completedItems[1].getId(),
                completedItems[0].getId(),
            ),
        )

        val result = service.getTournaments(userId, listOf(TournamentStatus.PENDING, TournamentStatus.COMPLETED))

        assertEquals(2, result.size)
        assertTrue(result.none { it.status == TournamentStatus.IN_PROGRESS })
    }

    @Test
    fun `getTournaments 에서 참여자 프로필 이미지를 반환한다`() {
        testUserRepository.add(
            User(
                id = userId,
                nickname = "테스터",
                profileImage = "https://cdn.example.com/test.jpg",
                identityType = IdentityType.MEMBER,
            ),
        )
        service.create(userId, CreateTournament("이미지 토너먼트"))

        val result = service.getTournaments(userId, null)

        assertEquals(1, result.size)
        assertEquals(listOf("https://cdn.example.com/test.jpg"), result[0].participantProfileImages)
    }

    @Test
    fun `deleteItem 은 PENDING 토너먼트에서 아이템을 삭제한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))
        val item = tournamentItemRepository.findAllByTournamentId(tournamentId).first()

        service.deleteItem(userId, tournamentId, item.getId())

        assertEquals(1, tournamentItemRepository.findAllByTournamentId(tournamentId).size)
    }

    @Test
    fun `deleteItem 에서 토너먼트 소유자도 다른 사람이 추가한 아이템을 삭제할 수 있다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        tournamentUserRepository.save(TournamentUser(tournamentId = tournamentId, userId = otherUserId))
        testWishRepository.addWish(otherUserId, 1L)
        service.addItemsFromWish(otherUserId, AddTournamentItemsFromWish(tournamentId, listOf(1L)))
        val item = tournamentItemRepository.findAllByTournamentId(tournamentId).first()

        service.deleteItem(userId, tournamentId, item.getId())

        assertEquals(0, tournamentItemRepository.findAllByTournamentId(tournamentId).size)
    }

    @Test
    fun `deleteItem 에서 PENDING 이 아닌 토너먼트면 예외가 발생한다`() {
        val tournamentId = createAndStart(listOf(1L, 2L))
        val item = tournamentItemRepository.findAllByTournamentId(tournamentId).first()

        assertFailsWith<TournamentException> {
            service.deleteItem(userId, tournamentId, item.getId())
        }
    }

    @Test
    fun `deleteItem 에서 존재하지 않는 tournamentItemId 면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))

        assertFailsWith<TournamentException> {
            service.deleteItem(userId, tournamentId, 999L)
        }
    }

    @Test
    fun `deleteItem 에서 다른 토너먼트 아이템이면 예외가 발생한다`() {
        val tournamentId1 = service.create(userId, CreateTournament("토너먼트1"))
        val tournamentId2 = service.create(userId, CreateTournament("토너먼트2"))
        testWishRepository.addWish(userId, 1L, 2L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId1, listOf(1L)))
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId2, listOf(2L)))
        val itemOfTournament2 = tournamentItemRepository.findAllByTournamentId(tournamentId2).first()

        assertFailsWith<TournamentException> {
            service.deleteItem(userId, tournamentId1, itemOfTournament2.getId())
        }
    }

    @Test
    fun `deleteItem 에서 아이템을 추가하지 않은 타인이면 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        testWishRepository.addWish(userId, 1L)
        service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L)))
        val item = tournamentItemRepository.findAllByTournamentId(tournamentId).first()

        assertFailsWith<TournamentException> {
            service.deleteItem(otherUserId, tournamentId, item.getId())
        }
    }

    @Test
    fun `addItems 에서 위시에 없는 아이템이면 403 예외가 발생한다`() {
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))
        // 위시에 1L만 등록하고 2L은 없는 상태

        val ex =
            assertFailsWith<TournamentException> {
                service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))
            }
        assertEquals(HttpStatus.FORBIDDEN, ex.httpStatus)
    }

    @Test
    fun `addItems 에서 일부 아이템이 위시에 없으면 403 예외가 발생한다`() {
        testWishRepository.addWish(userId, 1L) // 1L만 위시에 있음
        val tournamentId = service.create(userId, CreateTournament("토너먼트"))

        val ex =
            assertFailsWith<TournamentException> {
                service.addItemsFromWish(userId, AddTournamentItemsFromWish(tournamentId, listOf(1L, 2L)))
            }
        assertEquals(HttpStatus.FORBIDDEN, ex.httpStatus)
    }
}
