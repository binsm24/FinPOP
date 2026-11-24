package com.finpop.app.data.game

import com.finpop.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class GameTurnRulesTest {
    private val time = "2026-11-17T00:00:00Z"
    private val provider = SampleQuizProvider()
    private fun state(position: Int = 1, owner: PlayerId? = null, gold: Int = 700) = GameState(
        gameId = "game", quizStage = 1, currentPlayerId = PlayerId.P1, currentTurnId = "turn",
        phase = GamePhase.LANDING_PENDING,
        players = listOf(GamePlayer(PlayerId.P1,false,"CHEESE",1,position=position,gameGold=gold),
            GamePlayer(PlayerId.P2,true,"CHEESE",1)),
        tiles = GameRules.tiles.map { TileOwnership(it.tileId, if(it.tileId == position) owner else null) },
        updatedAt = time,
    )
    private fun failure(code: String, action: () -> Unit) {
        try { action(); fail("오류가 발생해야 합니다: $code") }
        catch(error: GameException) { assertEquals(code,error.code) }
    }
    @Test fun correctAnswerAllowsPurchaseAndCompletionOnce() {
        val (open,question) = GameTurnRules.land(state(),provider,time) { "attempt" }
        assertEquals(GamePhase.QUIZ_OPEN,open.phase)
        val answered = GameTurnRules.answer(open,question!!,1,time)
        assertEquals(GamePhase.PURCHASE_PENDING,answered.phase)
        assertEquals(700,answered.players.first().gameGold)
        failure("TURN_NOT_READY_TO_COMPLETE") { GameTurnRules.complete(answered,"turn") { "next" } }
        val purchased = GameTurnRules.purchase(answered,1,PurchaseAction.BUY)
        assertEquals(PlayerId.P1,purchased.tiles[1].ownerPlayerId)
        assertEquals(510,purchased.players.first().gameGold)
        val complete = GameTurnRules.complete(purchased,"turn") { "next" }
        assertEquals(6,complete.players.first().remainingTurns)
        assertEquals(PlayerId.P2,complete.currentPlayerId)
        failure("TURN_ALREADY_COMPLETED") { GameTurnRules.complete(complete,"turn") { "other" } }
    }
    @Test fun passDoesNotChangeGoldOrOwnership() {
        val (open,q) = GameTurnRules.land(state(),provider,time) { "attempt" }
        val answered = GameTurnRules.answer(open,q!!,1,time)
        val passed = GameTurnRules.purchase(answered,1,PurchaseAction.PASS)
        assertNull(passed.tiles[1].ownerPlayerId)
        assertEquals(700,passed.players.first().gameGold)
        assertEquals(GamePhase.TURN_REVIEW,passed.phase)
    }
    @Test fun insufficientBalanceCannotBuyButCanPass() {
        val (open,q) = GameTurnRules.land(state(gold=50),provider,time) { "attempt" }
        val answered = GameTurnRules.answer(open,q!!,1,time)
        assertFalse(answered.lastQuizResult!!.canPurchase)
        failure("INSUFFICIENT_GOLD") { GameTurnRules.purchase(answered,1,PurchaseAction.BUY) }
        assertEquals(50,GameTurnRules.purchase(answered,1,PurchaseAction.PASS).players.first().gameGold)
    }
    @Test fun wrongAnswerDoesNotAllowPurchase() {
        val (open,q) = GameTurnRules.land(state(),provider,time) { "attempt" }
        val wrong = GameTurnRules.answer(open,q!!,2,time)
        assertEquals(GamePhase.TURN_REVIEW,wrong.phase)
        assertNull(wrong.pendingPurchase)
        failure("PURCHASE_NOT_AVAILABLE") { GameTurnRules.purchase(wrong,1,PurchaseAction.BUY) }
    }
    @Test fun visitCorrectPaysHalfAndPreservesTotalGold() {
        val (open,q) = GameTurnRules.land(state(owner=PlayerId.P2),provider,time) { "attempt" }
        val result = GameTurnRules.answer(open,q!!,1,time)
        assertEquals(95,result.lastQuizResult!!.payment!!.amount)
        assertEquals(listOf(605,795),result.players.map { it.gameGold })
        assertEquals(1400,result.players.sumOf { it.gameGold })
        assertNull(result.pendingPurchase)
    }
    @Test fun wrongVisitPaysFullEvenWithNegativeBalance() {
        val (open,q) = GameTurnRules.land(state(owner=PlayerId.P2,gold=50),provider,time) { "attempt" }
        val result = GameTurnRules.answer(open,q!!,2,time)
        assertEquals(listOf(-140,890),result.players.map { it.gameGold })
        assertEquals(GameStatus.ACTIVE,result.status)
    }
    @Test fun deadlineIsTimeoutAndPaysFull() {
        val (open,q) = GameTurnRules.land(state(owner=PlayerId.P2,gold=50),provider,time) { "attempt" }
        failure("QUIZ_NOT_EXPIRED") { GameTurnRules.answer(open,q!!,null,time) }
        val result = GameTurnRules.answer(open,q!!,1,"2026-11-17T00:00:20Z")
        assertEquals(QuizResolution.TIMEOUT,result.lastQuizResult!!.resolution)
        assertNull(result.lastQuizResult!!.selectedChoiceId)
        assertEquals(-140,result.players.first().gameGold)
        failure("ALREADY_RESOLVED") { GameTurnRules.answer(result,q,1,time) }
    }
    @Test fun ownTileAndStartSkipQuiz() {
        for(game in listOf(state(owner=PlayerId.P1),state(position=0))) {
            val (landed,q) = GameTurnRules.land(game,provider,time) { "attempt" }
            assertEquals(GamePhase.TURN_REVIEW,landed.phase)
            assertNull(q)
            assertEquals(game.players,landed.players)
        }
    }
    @Test fun specialTileBlocksCompletionUntilEffectsExist() {
        for(position in listOf(3,7,10)) {
            val (landed,q) = GameTurnRules.land(state(position),provider,time) { "attempt" }
            assertEquals(GamePhase.SPECIAL_TILE_PENDING,landed.phase)
            assertNull(q)
            failure("TURN_NOT_READY_TO_COMPLETE") { GameTurnRules.complete(landed,"turn") { "next" } }
        }
    }
    @Test fun sameQuestionCannotBeIssuedTwice() {
        val (open,_) = GameTurnRules.land(state(),provider,time) { "attempt" }
        failure("QUIZ_POOL_EXHAUSTED") { GameTurnRules.land(open.copy(pendingQuiz=null),provider,time) { "again" } }
    }
    @Test fun invalidChoiceAndWrongTileAreRejected() {
        val (open,q) = GameTurnRules.land(state(),provider,time) { "attempt" }
        failure("INVALID_CHOICE") { GameTurnRules.answer(open,q!!,5,time) }
        val answered = GameTurnRules.answer(open,q!!,1,time)
        failure("PURCHASE_NOT_AVAILABLE") { GameTurnRules.purchase(answered,2,PurchaseAction.BUY) }
    }
    @Test fun bothPlayersZeroMarksFinishedWithoutIssuingRewards() {
        val game = state(owner=PlayerId.P1).copy(
            phase=GamePhase.TURN_REVIEW,
            players=state().players.map { it.copy(remainingTurns=if(it.playerId==PlayerId.P1) 1 else 0) })
        val result = GameTurnRules.complete(game,"turn") { "unused" }
        assertEquals(GameStatus.FINISHED,result.status)
        assertNull(result.currentTurnId)
    }
}

