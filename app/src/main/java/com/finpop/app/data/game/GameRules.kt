package com.finpop.app.data.game

import com.finpop.app.core.model.BoardTile
import com.finpop.app.core.model.TileTopic

/** 노션 API 명세서의 규칙 버전을 기준으로 정의한 게임 규칙입니다. */
object GameRules {
    const val VERSION = "1.0"
    const val INITIAL_GOLD = 700
    const val TURNS_PER_PLAYER = 7
    const val START_REWARD_GOLD = 200
    const val DICE_MIN = 1
    const val DICE_MAX = 6
    const val QUIZ_TIME_LIMIT_SECONDS = 20
    const val ISLAND_WAIT_TURNS = 1
    // 호출한 쪽에서 공통 규칙을 변경하지 못하도록 매번 새 목록을 반환합니다.
    val tiles: List<BoardTile> get() = listOf(
        BoardTile(0, TileTopic.START, 0),
        BoardTile(1, TileTopic.STOCK, 190),
        BoardTile(2, TileTopic.BOND, 160),
        BoardTile(3, TileTopic.GOLDEN_KEY, 0),
        BoardTile(4, TileTopic.MATURITY, 200),
        BoardTile(5, TileTopic.COMPOUND, 220),
        BoardTile(6, TileTopic.DIVIDEND, 240),
        BoardTile(7, TileTopic.ISLAND, 0),
        BoardTile(8, TileTopic.DEPOSIT, 120),
        BoardTile(9, TileTopic.SAVINGS, 150),
        BoardTile(10, TileTopic.GOLDEN_KEY, 0),
        BoardTile(11, TileTopic.LOAN, 180),
        BoardTile(12, TileTopic.ASSET, 210),
        BoardTile(13, TileTopic.FUND, 170),
    )
}

