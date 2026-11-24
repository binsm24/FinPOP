package com.finpop.app.data.game

import com.finpop.app.core.model.*
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * 단일 사용자 로컬 검증용입니다. DB·HTTP·인증·계정 보상은 포함하지 않습니다.
 * 일반 칸의 퀴즈·구매·정산·차례 종료를 지원합니다.
 * 특수칸과 캐릭터 능력은 별도 연결 대상이며, 운영 서버 대신 사용하면 안 됩니다.
 */
class InMemoryGameRepository(
    private val profile: GameProfile = GameProfile(),
    private val quizProvider: LocalQuizProvider = SampleQuizProvider(),
    private val dice: () -> Int = { Random.nextInt(1, 7) },
    private val now: () -> String = { Instant.now().toString() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : GameRepository {
    private val lock = Any()
    private val games = mutableMapOf<String, GameState>()
    private val issuedQuestions = mutableMapOf<String, LocalQuestion>()
    private data class Command(val operation: String, val gameId: String?, val payload: Any)
    private data class Cached(val command: Command, val result: Any)
    private val requests = mutableMapOf<String, Cached>()

    override suspend fun createGame(request: CreateGameRequest, requestId: String): GameState = synchronized(lock) {
        val command = Command("create", null, request)
        val old = replay(requestId, command)
        if (old != null) return@synchronized copyState(old as GameState)
        checkGame(request.mode == "AI", "INVALID_REQUEST", "AI 모드만 지원합니다.")
        checkGame(request.characterId == profile.characterId, "CHARACTER_LOCKED", "사용 가능한 캐릭터가 아닙니다.")
        checkGame(profile.characterLevel > 0 && profile.quizStage > 0, "INVALID_PROFILE", "테스트 프로필을 확인해 주세요.")
        checkGame(games.values.none { it.status == GameStatus.ACTIVE }, "ACTIVE_GAME_EXISTS", "진행 중인 게임이 있습니다.")
        val game = GameState(
            gameId = newId(), quizStage = profile.quizStage,
            players = listOf(
                GamePlayer(PlayerId.P1, false, profile.characterId, profile.characterLevel,
                    gameGold = GameRules.INITIAL_GOLD, remainingTurns = GameRules.TURNS_PER_PLAYER),
                GamePlayer(PlayerId.P2, true, "CHEESE", 1,
                    gameGold = GameRules.INITIAL_GOLD, remainingTurns = GameRules.TURNS_PER_PLAYER),
            ),
            tiles = GameRules.tiles.map { TileOwnership(it.tileId) }, updatedAt = now(),
        )
        games[game.gameId] = game
        requests[requestId] = Cached(command, game)
        copyState(game)
    }

    override suspend fun getGame(gameId: String): GameState = synchronized(lock) { copyState(find(gameId)) }

    override suspend fun determineInitiative(
        gameId: String, request: GameActionRequest, requestId: String,
    ): InitiativeResult = synchronized(lock) {
        val command = Command("initiative", gameId, request)
        val old = replay(requestId, command)
        if (old != null) return@synchronized copyInitiative(old as InitiativeResult)
        val game = find(gameId)
        version(game, request)
        checkGame(game.phase == GamePhase.INITIATIVE_PENDING,
            "INITIATIVE_ALREADY_RESOLVED", "선공 결정이 이미 끝났습니다.")
        val rolls = mutableListOf<InitiativeRoll>()
        var winner: PlayerId? = null
        repeat(1000) {
            if (winner == null) {
                val pair = InitiativeRoll(roll(), roll())
                rolls += pair
                winner = when {
                    pair.p1 > pair.p2 -> PlayerId.P1
                    pair.p1 < pair.p2 -> PlayerId.P2
                    else -> null
                }
            }
        }
        val first = winner ?: throw GameException("DICE_UNAVAILABLE", "선공 추첨을 완료하지 못했습니다.")
        val next = game.copy(phase = GamePhase.TURN_READY, currentPlayerId = first,
            currentTurnId = newId(), stateVersion = game.stateVersion + 1, updatedAt = now())
        val result = InitiativeResult(rolls.toList(), first, next)
        games[gameId] = next
        requests[requestId] = Cached(command, result)
        copyInitiative(result)
    }

    override suspend fun rollDice(
        gameId: String, request: GameActionRequest, requestId: String,
    ): MovementResult = synchronized(lock) {
        val command = Command("roll", gameId, request)
        val old = replay(requestId, command)
        if (old != null) return@synchronized copyMovement(old as MovementResult)
        val game = find(gameId)
        version(game, request)
        checkGame(game.currentPlayerId == PlayerId.P1, "NOT_YOUR_TURN", "사용자 차례가 아닙니다.")
        checkGame(game.phase == GamePhase.TURN_READY && game.currentTurnId != null,
            "INVALID_PHASE", "현재 주사위를 굴릴 수 없습니다.")
        val player = game.players.first { it.playerId == PlayerId.P1 }
        checkGame(player.remainingTurns > 0 && player.islandWaitTurns == 0,
            "INVALID_PHASE", "현재 이동할 수 없습니다.")
        val value = roll()
        val path = movementPath(player.position, value)
        val reward = if (0 in path) GameRules.START_REWARD_GOLD else 0
        val events = if (reward > 0) listOf(MovementEvent("START_REWARD", PlayerId.P1, reward)) else emptyList()
        val moved = player.copy(position = path.last(), gameGold = player.gameGold + reward)
        val next = game.copy(
            phase = GamePhase.LANDING_PENDING,
            players = game.players.map { if (it.playerId == player.playerId) moved else it },
            stateVersion = game.stateVersion + 1, updatedAt = now(),
        )
        val (landed, question) = GameTurnRules.land(next, quizProvider, next.updatedAt, newId)
        val result = MovementResult(value, path, events, GameRules.tiles[path.last()], landed)
        games[gameId] = landed
        if (question != null) issuedQuestions[requireNotNull(landed.pendingQuiz).attemptId] = question
        requests[requestId] = Cached(command, result)
        copyMovement(result)
    }

    override suspend fun answerQuiz(gameId: String, attemptId: String, choiceId: Int,
        request: GameActionRequest, requestId: String): GameState =
        resolveAnswer(gameId, attemptId, choiceId, request, requestId)

    override suspend fun resolveQuizTimeout(gameId: String, attemptId: String,
        request: GameActionRequest, requestId: String): GameState =
        resolveAnswer(gameId, attemptId, null, request, requestId)

    private fun resolveAnswer(gameId: String, attemptId: String, choiceId: Int?,
        request: GameActionRequest, requestId: String): GameState = synchronized(lock) {
        mutate(gameId, request, requestId, Command("answer", gameId, listOf(request, attemptId, choiceId))) { game ->
            checkGame(game.pendingQuiz?.attemptId == attemptId, "ALREADY_RESOLVED", "현재 발급된 문제가 아닙니다.")
            val question = issuedQuestions[attemptId]
                ?: throw GameException("RESOURCE_NOT_FOUND", "판정 자료를 찾을 수 없습니다.")
            GameTurnRules.answer(game, question, choiceId, now())
        }
    }

    override suspend fun decidePurchase(gameId: String, tileId: Int, action: PurchaseAction,
        request: GameActionRequest, requestId: String): GameState = synchronized(lock) {
        mutate(gameId, request, requestId, Command("purchase", gameId, listOf(request, tileId, action))) {
            GameTurnRules.purchase(it, tileId, action)
        }
    }

    override suspend fun completeTurn(gameId: String, turnId: String,
        request: GameActionRequest, requestId: String): GameState = synchronized(lock) {
        mutate(gameId, request, requestId, Command("complete", gameId, listOf(request, turnId))) {
            GameTurnRules.complete(it, turnId, newId)
        }
    }

    /** 같은 요청은 현재 버전 검사보다 먼저 재사용하며, 모든 검증이 끝난 뒤 상태를 반영합니다. */
    private fun mutate(gameId: String, request: GameActionRequest, requestId: String,
        command: Command, transform: (GameState) -> GameState): GameState {
        val old = replay(requestId, command)
        if (old != null) return copyState(old as GameState)
        val game = find(gameId)
        version(game, request)
        checkGame(game.currentPlayerId == PlayerId.P1, "NOT_YOUR_TURN", "사용자 차례가 아닙니다.")
        val next = transform(game).copy(stateVersion = game.stateVersion + 1, updatedAt = now())
        games[gameId] = next
        requests[requestId] = Cached(command, next)
        return copyState(next)
    }

    private fun replay(id: String, command: Command): Any? {
        checkGame(runCatching { UUID.fromString(id).toString().equals(id, ignoreCase = true) }.getOrDefault(false),
            "INVALID_REQUEST", "요청 ID는 UUID여야 합니다.")
        val cached = requests[id] ?: return null
        checkGame(cached.command == command, "IDEMPOTENCY_CONFLICT", "같은 요청 ID에 다른 요청을 사용할 수 없습니다.")
        return cached.result
    }
    private fun find(id: String) = games[id] ?: throw GameException("RESOURCE_NOT_FOUND", "게임을 찾을 수 없습니다.")
    private fun version(game: GameState, request: GameActionRequest) {
        checkGame(request.expectedVersion >= 0, "INVALID_REQUEST", "상태 버전은 0 이상이어야 합니다.")
        checkGame(game.stateVersion == request.expectedVersion, "STATE_CONFLICT", "최신 게임 상태를 조회해 주세요.")
    }
    private fun roll(): Int = dice().also {
        checkGame(it in GameRules.DICE_MIN..GameRules.DICE_MAX, "DICE_UNAVAILABLE", "주사위 결과가 잘못됐습니다.")
    }
    private fun checkGame(condition: Boolean, code: String, message: String) {
        if (!condition) throw GameException(code, message)
    }
    private fun copyState(game: GameState) = game.copy(
        players = game.players.toList(), tiles = game.tiles.toList(),
        pendingQuiz = game.pendingQuiz?.let { it.copy(choices = it.choices.toList()) },
        usedQuestionIds = game.usedQuestionIds.toList())
    private fun copyInitiative(result: InitiativeResult) = result.copy(rolls = result.rolls.toList(), game = copyState(result.game))
    private fun copyMovement(result: MovementResult) = result.copy(
        movementPath = result.movementPath.toList(), events = result.events.toList(), game = copyState(result.game))

    companion object {
        /** 이동 전 위치는 경로에서 제외합니다. 0번 칸에 도착하거나 통과하면 출발 보상을 받습니다. */
        fun movementPath(position: Int, diceValue: Int): List<Int> {
            require(position in GameRules.tiles.indices)
            require(diceValue in GameRules.DICE_MIN..GameRules.DICE_MAX)
            return (1..diceValue).map { (position + it) % GameRules.tiles.size }
        }
    }
}


