package com.finpop.app.core.model

enum class TileTopic { START, STOCK, BOND, GOLDEN_KEY, MATURITY, COMPOUND, DIVIDEND, ISLAND, DEPOSIT, SAVINGS, LOAN, ASSET, FUND }
enum class GamePhase { INITIATIVE_PENDING, TURN_READY, LANDING_PENDING, QUIZ_OPEN, PURCHASE_PENDING, TURN_REVIEW, SPECIAL_TILE_PENDING, FINISHED }
enum class GameMode { AI }
enum class GameStatus { ACTIVE, FINISHED }
enum class PlayerId { P1, P2 }

data class BoardTile(val tileId: Int, val topic: TileTopic, val price: Int)
data class TileOwnership(val tileId: Int, val ownerPlayerId: PlayerId? = null)
data class AbilityState(val triggerCount: Int = 0)
data class GamePlayer(
    val playerId: PlayerId,
    val isAi: Boolean,
    val characterId: String,
    val characterLevel: Int,
    val position: Int = 0,
    val gameGold: Int = 700,
    val remainingTurns: Int = 7,
    val islandWaitTurns: Int = 0,
    val abilityState: AbilityState = AbilityState(),
)
data class GameState(
    val gameId: String,
    val mode: GameMode = GameMode.AI,
    val status: GameStatus = GameStatus.ACTIVE,
    val phase: GamePhase = GamePhase.INITIATIVE_PENDING,
    val stateVersion: Int = 1,
    val rulesVersion: String = "1.0",
    val quizStage: Int,
    val currentPlayerId: PlayerId? = null,
    val currentTurnId: String? = null,
    val players: List<GamePlayer>,
    val tiles: List<TileOwnership>,
    val pendingQuiz: PendingQuiz? = null,
    val pendingPurchase: PendingPurchase? = null,
    val lastQuizResult: QuizResult? = null,
    val usedQuestionIds: List<String> = emptyList(),
    val updatedAt: String,
)
data class InitiativeRoll(val p1: Int, val p2: Int)
data class InitiativeResult(val rolls: List<InitiativeRoll>, val firstPlayerId: PlayerId, val game: GameState)
data class MovementEvent(val type: String, val playerId: PlayerId, val amount: Int)
data class MovementResult(
    val diceValue: Int,
    val movementPath: List<Int>,
    val events: List<MovementEvent>,
    val arrivalTile: BoardTile,
    val game: GameState,
)


data class QuizChoice(val id: Int, val text: String)
data class PendingQuiz(
    val attemptId: String, val questionVersionId: String, val tileId: Int, val topic: TileTopic,
    val question: String, val choices: List<QuizChoice>, val issuedAt: String, val deadlineAt: String,
)
enum class QuizResolution { CORRECT, INCORRECT, TIMEOUT }
enum class PurchaseAction { BUY, PASS }
data class PendingPurchase(val tileId: Int, val price: Int)
data class VisitPayment(val fromPlayerId: PlayerId, val toPlayerId: PlayerId, val amount: Int)
data class QuizResult(
    val attemptId: String, val resolution: QuizResolution, val selectedChoiceId: Int?,
    val correctChoiceId: Int, val explanation: String, val canPurchase: Boolean,
    val payment: VisitPayment?,
)
