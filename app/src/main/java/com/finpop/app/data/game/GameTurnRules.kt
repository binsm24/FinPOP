package com.finpop.app.data.game

import com.finpop.app.core.model.*
import java.time.Instant

/** DB와 화면에 의존하지 않는 도착·판정·정산 규칙입니다. 입력 상태를 변경하지 않습니다. */
object GameTurnRules {
    private fun requireGame(condition: Boolean, code: String, message: String) {
        if (!condition) throw GameException(code, message)
    }

    fun land(game: GameState, provider: LocalQuizProvider, now: String, newId: () -> String): Pair<GameState, LocalQuestion?> {
        val player = game.players.first { it.playerId == game.currentPlayerId }
        val tile = GameRules.tiles[player.position]
        if (tile.topic == TileTopic.GOLDEN_KEY || tile.topic == TileTopic.ISLAND)
            return game.copy(phase = GamePhase.SPECIAL_TILE_PENDING) to null
        val owner = game.tiles.first { it.tileId == tile.tileId }.ownerPlayerId
        if (tile.topic == TileTopic.START || owner == player.playerId)
            return game.copy(phase = GamePhase.TURN_REVIEW) to null
        val question = provider.find(tile.topic, game.quizStage, game.usedQuestionIds.toSet())
            ?: throw GameException("QUIZ_POOL_EXHAUSTED", "해당 주제의 미출제 문항이 부족합니다.")
        requireGame(question.topic == tile.topic && question.id !in game.usedQuestionIds &&
            question.choices.size == 4 && question.choices.map { it.id }.toSet() == setOf(1,2,3,4) &&
            question.correctChoiceId in 1..4, "INVALID_QUIZ", "문항 형식을 확인해 주세요.")
        val quiz = PendingQuiz(newId(), question.id, tile.tileId, tile.topic, question.question,
            question.choices.toList(), now,
            Instant.parse(now).plusSeconds(GameRules.QUIZ_TIME_LIMIT_SECONDS.toLong()).toString())
        return game.copy(phase = GamePhase.QUIZ_OPEN, pendingQuiz = quiz,
            lastQuizResult = null, usedQuestionIds = game.usedQuestionIds + question.id) to question
    }

    fun answer(game: GameState, question: LocalQuestion, choiceId: Int?, now: String): GameState {
        val quiz = game.pendingQuiz ?: throw GameException("ALREADY_RESOLVED", "현재 판정할 문제가 없습니다.")
        requireGame(game.phase == GamePhase.QUIZ_OPEN, "ALREADY_RESOLVED", "이미 판정이 끝난 문제입니다.")
        requireGame(quiz.questionVersionId == question.id, "INVALID_QUIZ", "발급된 문항과 판정 자료가 다릅니다.")
        val expired = !Instant.parse(now).isBefore(Instant.parse(quiz.deadlineAt))
        if (choiceId == null) requireGame(expired, "QUIZ_NOT_EXPIRED", "아직 제한 시간이 남아 있습니다.")
        else requireGame(choiceId in 1..4, "INVALID_CHOICE", "선택지는 1~4 중 하나여야 합니다.")
        val resolution = when {
            expired -> QuizResolution.TIMEOUT
            choiceId == question.correctChoiceId -> QuizResolution.CORRECT
            else -> QuizResolution.INCORRECT
        }
        val playerId = requireNotNull(game.currentPlayerId)
        val player = game.players.first { it.playerId == playerId }
        val owner = game.tiles.first { it.tileId == quiz.tileId }.ownerPlayerId
        val price = GameRules.tiles[quiz.tileId].price
        val payment = if (owner != null && owner != playerId)
            VisitPayment(playerId, owner, if (resolution == QuizResolution.CORRECT) price / 2 else price)
            else null
        val purchase = if (owner == null && resolution == QuizResolution.CORRECT) PendingPurchase(quiz.tileId, price) else null
        val result = QuizResult(quiz.attemptId, resolution, if (expired) null else choiceId,
            question.correctChoiceId, question.explanation,
            purchase != null && player.gameGold >= price, payment)
        return game.copy(
            phase = if (purchase != null) GamePhase.PURCHASE_PENDING else GamePhase.TURN_REVIEW,
            pendingQuiz = null, pendingPurchase = purchase, lastQuizResult = result,
            players = game.players.map {
                when (it.playerId) {
                    payment?.fromPlayerId -> it.copy(gameGold = it.gameGold - requireNotNull(payment).amount)
                    payment?.toPlayerId -> it.copy(gameGold = it.gameGold + requireNotNull(payment).amount)
                    else -> it
                }
            },
        )
    }

    fun purchase(game: GameState, tileId: Int, action: PurchaseAction): GameState {
        val pending = game.pendingPurchase
        requireGame(game.phase == GamePhase.PURCHASE_PENDING && pending?.tileId == tileId,
            "PURCHASE_NOT_AVAILABLE", "현재 구매할 수 있는 칸이 아닙니다.")
        val player = game.players.first { it.playerId == game.currentPlayerId }
        requireGame(game.tiles.firstOrNull { it.tileId == tileId }?.ownerPlayerId == null,
            "PURCHASE_NOT_AVAILABLE", "이미 소유자가 있는 칸입니다.")
        if (action == PurchaseAction.BUY) requireGame(player.gameGold >= pending!!.price,
            "INSUFFICIENT_GOLD", "칸을 구매할 G가 부족합니다.")
        return game.copy(phase = GamePhase.TURN_REVIEW, pendingPurchase = null,
            players = game.players.map {
                if (it.playerId == player.playerId && action == PurchaseAction.BUY)
                    it.copy(gameGold = it.gameGold - pending!!.price) else it
            },
            tiles = game.tiles.map {
                if (it.tileId == tileId && action == PurchaseAction.BUY) it.copy(ownerPlayerId = player.playerId) else it
            })
    }

    fun complete(game: GameState, turnId: String, newId: () -> String): GameState {
        requireGame(game.currentTurnId == turnId, "TURN_ALREADY_COMPLETED", "현재 차례 ID와 다릅니다.")
        requireGame(game.phase == GamePhase.TURN_REVIEW && game.pendingQuiz == null && game.pendingPurchase == null,
            "TURN_NOT_READY_TO_COMPLETE", "퀴즈·구매·이벤트 처리를 먼저 완료해 주세요.")
        val current = game.players.first { it.playerId == game.currentPlayerId }
        requireGame(current.remainingTurns > 0, "TURN_ALREADY_COMPLETED", "이미 완료한 차례입니다.")
        val players = game.players.map {
            if (it.playerId == current.playerId) it.copy(remainingTurns = it.remainingTurns - 1) else it
        }
        val finished = players.all { it.remainingTurns == 0 }
        val other = players.first { it.playerId != current.playerId }
        val nextPlayer = if (other.remainingTurns > 0) other.playerId else current.playerId
        return game.copy(players = players,
            status = if (finished) GameStatus.FINISHED else GameStatus.ACTIVE,
            phase = if (finished) GamePhase.FINISHED else GamePhase.TURN_READY,
            currentPlayerId = if (finished) null else nextPlayer,
            currentTurnId = if (finished) null else newId())
    }
}

