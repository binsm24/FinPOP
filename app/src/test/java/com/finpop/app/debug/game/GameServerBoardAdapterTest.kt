package com.finpop.app.debug.game

import org.junit.Assert.*
import org.junit.Test

class GameServerBoardAdapterTest {
    @Test fun allTilesMapToTheirBoardLocations() {
        // 출발·주식·채권·열쇠·만기·복리·배당·무인도·예금·적금·열쇠·대출·자산·펀드 순서입니다.
        val expected = listOf(7, 8, 9, 10, 11, 12, 13, 0, 1, 2, 3, 4, 5, 6)
        expected.forEachIndexed { server, board ->
            assertEquals(board, serverTileToBoard(server))
            assertEquals(server, boardTileToServer(board))
        }
    }

    @Test fun invalidTileIdsAreRejected() {
        listOf(-1, 14).forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { serverTileToBoard(id) }
            assertThrows(IllegalArgumentException::class.java) { boardTileToServer(id) }
        }
    }

    @Test fun onlyReadyUserTurnCanRoll() {
        assertTrue(canRollOnServerBoard("TURN_READY", "P1", 7, 0, false))
        assertFalse(canRollOnServerBoard("TURN_READY", "P2", 7, 0, false))
        assertFalse(canRollOnServerBoard("INITIATIVE_PENDING", null, 7, 0, false))
        listOf("QUIZ_OPEN", "PURCHASE_PENDING", "TURN_REVIEW", "FINISHED").forEach { phase ->
            assertFalse(canRollOnServerBoard(phase, "P1", 7, 0, false))
        }
    }

    @Test fun waitingFinishedOrBusyUserCannotRoll() {
        assertFalse(canRollOnServerBoard("TURN_READY", "P1", 0, 0, false))
        assertFalse(canRollOnServerBoard("TURN_READY", "P1", 7, 1, false))
        assertFalse(canRollOnServerBoard("TURN_READY", "P1", 7, 0, true))
    }
}
