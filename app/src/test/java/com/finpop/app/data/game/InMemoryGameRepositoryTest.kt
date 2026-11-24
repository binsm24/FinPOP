package com.finpop.app.data.game

import com.finpop.app.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.coroutines.*

class InMemoryGameRepositoryTest {
    private fun id() = UUID.randomUUID().toString()
    private fun repo(vararg values: Int): InMemoryGameRepository {
        val queue = values.toList().iterator()
        return InMemoryGameRepository(dice = { queue.next() }, now = { "2026-11-17T00:00:00Z" })
    }
    private fun <T> run(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return checkNotNull(outcome) { "로컬 저장소에서 예상하지 못한 대기가 발생했습니다." }.getOrThrow()
    }
    private fun failure(code: String, block: suspend () -> Unit) {
        try { run(block); fail("Expected " + code) }
        catch (error: GameException) { assertEquals(code, error.code) }
    }
    @Test fun initialStateMatchesRulesAndSecondGameIsRejected() {
        val repo = repo()
        val game = run { repo.createGame(CreateGameRequest(characterId = "CHEESE"), id()) }
        assertEquals(14, game.tiles.size)
        assertTrue(game.tiles.all { it.ownerPlayerId == null })
        assertTrue(game.players.all { it.position == 0 && it.gameGold == 700 && it.remainingTurns == 7 })
        assertEquals(GamePhase.INITIATIVE_PENDING, game.phase)
        assertNull(game.currentPlayerId)
        failure("ACTIVE_GAME_EXISTS") { repo.createGame(CreateGameRequest(characterId = "CHEESE"), id()) }
    }
    @Test fun tiesAreRerolledWithoutSpendingTurns() {
        val repo = repo(4,4,6,2)
        val game = run { repo.createGame(CreateGameRequest(characterId = "CHEESE"), id()) }
        val result = run { repo.determineInitiative(game.gameId, GameActionRequest(1), id()) }
        assertEquals(listOf(InitiativeRoll(4,4), InitiativeRoll(6,2)), result.rolls)
        assertEquals(PlayerId.P1, result.firstPlayerId)
        assertEquals(2, result.game.stateVersion)
        assertTrue(result.game.players.all { it.remainingTurns == 7 && it.position == 0 })
    }
    @Test fun movementIssuesQuizAndCannotBeRepeated() {
        val repo = repo(6,1,5)
        val game = run { repo.createGame(CreateGameRequest(characterId = "CHEESE"), id()) }
        run { repo.determineInitiative(game.gameId, GameActionRequest(1), id()) }
        val key = id()
        val moved = run { repo.rollDice(game.gameId, GameActionRequest(2), key) }
        assertEquals(listOf(1,2,3,4,5), moved.movementPath)
        assertEquals(GamePhase.QUIZ_OPEN, moved.game.phase)
        assertEquals(7, moved.game.players.first().remainingTurns)
        assertEquals(moved, run { repo.rollDice(game.gameId, GameActionRequest(2), key) })
        failure("INVALID_PHASE") { repo.rollDice(game.gameId, GameActionRequest(3), id()) }
    }
    @Test fun aiFirstPreventsHumanMovement() {
        val repo = repo(1,6)
        val game = run { repo.createGame(CreateGameRequest(characterId = "CHEESE"), id()) }
        run { repo.determineInitiative(game.gameId, GameActionRequest(1), id()) }
        failure("NOT_YOUR_TURN") { repo.rollDice(game.gameId, GameActionRequest(2), id()) }
    }
    @Test fun retryPrecedesVersionCheckAndDifferentPayloadConflicts() {
        val repo = repo(6,1)
        val game = run { repo.createGame(CreateGameRequest(characterId = "CHEESE"), id()) }
        val key = id()
        val result = run { repo.determineInitiative(game.gameId, GameActionRequest(1), key) }
        assertEquals(result, run { repo.determineInitiative(game.gameId, GameActionRequest(1), key) })
        failure("IDEMPOTENCY_CONFLICT") { repo.determineInitiative(game.gameId, GameActionRequest(2), key) }
        failure("STATE_CONFLICT") { repo.determineInitiative(game.gameId, GameActionRequest(1), id()) }
    }
    @Test fun wraparoundIncludesStartAndExcludesOrigin() {
        assertEquals(listOf(13,0,1,2), InMemoryGameRepository.movementPath(12,4))
        assertEquals(listOf(0), InMemoryGameRepository.movementPath(13,1))
        assertEquals(listOf(1,2), InMemoryGameRepository.movementPath(0,2))
    }
    @Test fun invalidRandomDoesNotCommitState() {
        val repo = repo(6,1,7)
        val game = run { repo.createGame(CreateGameRequest(characterId = "CHEESE"), id()) }
        run { repo.determineInitiative(game.gameId, GameActionRequest(1), id()) }
        failure("DICE_UNAVAILABLE") { repo.rollDice(game.gameId, GameActionRequest(2), id()) }
        assertEquals(2, run { repo.getGame(game.gameId) }.stateVersion)
    }
    @Test fun fullTurnRetriesDoNotDuplicatePurchaseOrTurnDeduction() {
        val repo = repo(6,1,1)
        val created = run { repo.createGame(CreateGameRequest(characterId="CHEESE"),id()) }
        val first = run { repo.determineInitiative(created.gameId,GameActionRequest(1),id()) }.game
        val moved = run { repo.rollDice(first.gameId,GameActionRequest(2),id()) }.game
        val quiz = requireNotNull(moved.pendingQuiz)
        val answerKey = id()
        val answered = run { repo.answerQuiz(moved.gameId,quiz.attemptId,1,GameActionRequest(3),answerKey) }
        assertEquals(answered,run { repo.answerQuiz(moved.gameId,quiz.attemptId,1,GameActionRequest(3),answerKey) })
        failure("ALREADY_RESOLVED") { repo.answerQuiz(moved.gameId,quiz.attemptId,1,GameActionRequest(4),id()) }
        val buyKey = id()
        val purchased = run { repo.decidePurchase(moved.gameId,1,PurchaseAction.BUY,GameActionRequest(4),buyKey) }
        assertEquals(purchased,run { repo.decidePurchase(moved.gameId,1,PurchaseAction.BUY,GameActionRequest(4),buyKey) })
        val finishKey = id()
        val done = run { repo.completeTurn(moved.gameId,requireNotNull(first.currentTurnId),GameActionRequest(5),finishKey) }
        assertEquals(done,run { repo.completeTurn(moved.gameId,requireNotNull(first.currentTurnId),GameActionRequest(5),finishKey) })
        assertEquals(510,done.players.first().gameGold)
        assertEquals(6,done.players.first().remainingTurns)
        assertEquals(PlayerId.P2,done.currentPlayerId)
    }

    @Test fun quizProviderFailureRollsBackMovementAndVersion() {
        val repo = InMemoryGameRepository(quizProvider=LocalQuizProvider { _,_,_ -> null },dice={ 1 })
        // 동률만 나오는 난수 대신 선공 단계까지 별도 큐를 사용합니다.
        val queue = listOf(6,1,1).iterator()
        val tested = InMemoryGameRepository(quizProvider=LocalQuizProvider { _,_,_ -> null },dice={queue.next()})
        val game = run { tested.createGame(CreateGameRequest(characterId="CHEESE"),id()) }
        run { tested.determineInitiative(game.gameId,GameActionRequest(1),id()) }
        failure("QUIZ_POOL_EXHAUSTED") { tested.rollDice(game.gameId,GameActionRequest(2),id()) }
        val unchanged = run { tested.getGame(game.gameId) }
        assertEquals(2,unchanged.stateVersion)
        assertEquals(0,unchanged.players.first().position)
        assertTrue(unchanged.usedQuestionIds.isEmpty())
    }

    @Test fun creationRetryIsStableAndProfileIsValidated() {
        val repo = repo()
        val key = id()
        val request = CreateGameRequest(characterId = "CHEESE")
        val game = run { repo.createGame(request, key) }
        assertEquals(game, run { repo.createGame(request, key) })
        failure("IDEMPOTENCY_CONFLICT") { repo.createGame(request.copy(characterId = "OTHER"), key) }
        failure("CHARACTER_LOCKED") { repo().createGame(request.copy(characterId = "OTHER"), id()) }
    }
}


