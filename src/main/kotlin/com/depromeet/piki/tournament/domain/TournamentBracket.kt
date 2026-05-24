package com.depromeet.piki.tournament.domain

data class TournamentBracket(val matches: List<Match>) {
    data class Match(
        val firstTournamentItemId: Long,
        val secondTournamentItemId: Long,
    )

    companion object {
        /**
         * 가격 오름차순으로 정렬 후 인접 아이템끼리 1라운드 대진표를 생성한다.
         * - 가장 비슷한 가격의 두 아이템이 맞붙는다.
         * - 홀수 아이템이 남으면 다음 그룹이 있을 경우 합류시키고, 없으면 해당 아이템은 매치에서 제외된다.
         * - 가격 정보가 없는 아이템은 마지막에 별도 그룹으로 처리한다.
         * - itemsWithPrice: List<Pair<tournamentItemId, currentPrice>>
         */
        fun generate(itemsWithPrice: List<Pair<Long, Int?>>): TournamentBracket {
            val (withPrice, noPrice) = itemsWithPrice.partition { it.second is Int }

            val sortedIds = withPrice
                .sortedBy { it.second }
                .map { it.first }

            val noPriceIds = noPrice.map { it.first }.shuffled()

            val matches = mutableListOf<Match>()
            var leftover: Long? = null

            for (group in listOf(sortedIds, noPriceIds)) {
                if (group.isEmpty()) continue
                val pool = buildList {
                    leftover?.let { add(it) }
                    addAll(group)
                }.toMutableList()
                leftover = null

                while (pool.size >= 2) {
                    matches.add(Match(pool.removeFirst(), pool.removeFirst()))
                }
                if (pool.isNotEmpty()) leftover = pool.first()
            }

            return TournamentBracket(matches.shuffled())
        }
    }
}
