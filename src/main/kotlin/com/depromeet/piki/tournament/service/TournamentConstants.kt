package com.depromeet.piki.tournament.service

internal const val TOURNAMENT_MAX_ITEM_COUNT = 32
internal const val TOURNAMENT_MIN_ITEM_COUNT = 2

internal const val TOURNAMENT_INVITE_DEFAULT_DURATION_MINUTES = 30L
internal const val TOURNAMENT_INVITE_MAX_DURATION_MINUTES = 24 * 60L

internal const val PLAY_LINK_DURATION_DAYS = 14L

// 주최자 포함 최대 8명
internal const val TOURNAMENT_MAX_PARTICIPANT_COUNT = 8

// invite_code 충돌 시 재시도 최대 횟수 (17,576,000 가지 조합 → 충돌 극히 드물어 5회면 충분)
internal const val INVITE_CODE_MAX_ATTEMPTS = 5
