package com.depromeet.piki.tournament.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class TournamentBracketTest {
    private fun generate(vararg items: Pair<Long, Int?>) =
        TournamentBracket.generate(items.toList())

    private fun TournamentBracket.allItemIds() =
        matches.flatMap { listOf(it.firstTournamentItemId, it.secondTournamentItemId) }

    @Test
    fun `아이템 4개는 가격 오름차순 정렬 후 인접 쌍으로 2개의 매치가 생성된다`() {
        val bracket = generate(1L to 15_000, 2L to 17_000, 3L to 18_000, 4L to 12_000)

        assertEquals(2, bracket.matches.size)
        assertEquals(setOf(1L, 2L, 3L, 4L), bracket.allItemIds().toSet())
    }

    @Test
    fun `아이템 2개는 1개의 매치가 된다`() {
        val bracket = generate(1L to 10_000, 2L to 19_000)

        assertEquals(1, bracket.matches.size)
        assertEquals(setOf(1L, 2L), bracket.allItemIds().toSet())
    }

    @Test
    fun `가격 인접한 아이템끼리 쌍을 이루어 4L(21k)은 가장 가까운 3L(18k)과 매칭된다`() {
        // sorted: 1L(15k), 2L(17k), 3L(18k), 4L(21k) → (1L,2L) 매칭, (3L,4L) 매칭
        val bracket = generate(1L to 15_000, 2L to 17_000, 3L to 18_000, 4L to 21_000)

        assertEquals(2, bracket.matches.size)
        assertEquals(setOf(1L, 2L, 3L, 4L), bracket.allItemIds().toSet())

        val matchWith4 = bracket.matches.first { 4L in listOf(it.firstTournamentItemId, it.secondTournamentItemId) }
        val partner = if (matchWith4.firstTournamentItemId == 4L) matchWith4.secondTournamentItemId else matchWith4.firstTournamentItemId
        assertEquals(3L, partner)
    }

    @Test
    fun `가격 없는 아이템은 마지막에 별도 그룹으로 처리된다`() {
        val bracket = generate(1L to null, 2L to null, 3L to null, 4L to null)

        assertEquals(2, bracket.matches.size)
        assertEquals(setOf(1L, 2L, 3L, 4L), bracket.allItemIds().toSet())
    }

    @Test
    fun `가격 있는 아이템과 없는 아이템이 섞이면 가격 있는 쪽이 먼저 처리된다`() {
        // sorted price: [1L(15k), 2L(17k)] → 1개 매치
        // no-price: [3L, 4L] → 1개 매치
        val bracket = generate(1L to 15_000, 2L to 17_000, 3L to null, 4L to null)

        assertEquals(2, bracket.matches.size)
        assertEquals(setOf(1L, 2L, 3L, 4L), bracket.allItemIds().toSet())
    }

    @Test
    fun `가격 있는 그룹에서 홀수로 남은 아이템이 가격 없는 그룹과 매칭된다`() {
        // sorted price: [1L(15k), 2L(17k), 3L(18k)] → (1,2) 매칭, 3L 잔여
        // no-price: [3L(잔여), 4L] → (3,4) 매칭
        val bracket = generate(1L to 15_000, 2L to 17_000, 3L to 18_000, 4L to null)

        assertEquals(2, bracket.matches.size)
        assertEquals(setOf(1L, 2L, 3L, 4L), bracket.allItemIds().toSet())
    }

    @Test
    fun `모든 그룹 처리 후 홀수로 남은 아이템은 매치에 포함되지 않는다`() {
        // sorted price: [1L(15k), 2L(17k), 3L(21k)] → (1,2) 매칭, 3L 잔여 → 이후 그룹 없음 → 미매칭
        val bracket = generate(1L to 15_000, 2L to 17_000, 3L to 21_000)

        assertEquals(1, bracket.matches.size)
        assertTrue(3L !in bracket.allItemIds())
    }

    @Test
    fun `32개 아이템은 16개의 매치가 생성된다`() {
        val items = (1L..32L).map { it to (it * 1_000).toInt() }
        val bracket = TournamentBracket.generate(items)

        assertEquals(16, bracket.matches.size)
        assertEquals((1L..32L).toSet(), bracket.allItemIds().toSet())
    }

    @Test
    fun `각 아이템은 정확히 하나의 매치에만 등장한다`() {
        val items = (1L..10L).map { it to (it * 3_000).toInt() }
        val bracket = TournamentBracket.generate(items)

        val allIds = bracket.allItemIds()
        assertEquals(allIds.size, allIds.toSet().size)
    }

    @Test
    fun `동일 입력으로 두 번 생성하면 항상 같은 대진표가 반환된다`() {
        val items = (1L..8L).map { it to (it * 10_000).toInt() }

        assertEquals(TournamentBracket.generate(items), TournamentBracket.generate(items))
    }
}
