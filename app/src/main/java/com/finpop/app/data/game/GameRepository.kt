package com.finpop.app.data.game

import com.finpop.app.core.model.GameState
import com.finpop.app.core.model.InitiativeResult
import com.finpop.app.core.model.MovementResult

/** 로컬 테스트용 입력입니다. 운영 환경에서는 서버가 이 값을 조회하고 검증해야 합니다. */
data class GameProfile(
    val characterId: String = "CHEESE",
    val characterLevel: Int = 1,
    val quizStage: Int = 1,
)
data class CreateGameRequest(val mode: String = "AI", val characterId: String)
data class GameActionRequest(val expectedVersion: Int)

class GameException(val code: String, message: String) : IllegalStateException(message)

/**
 * 앱에서 게임 기능을 사용하는 인터페이스입니다. 추후 원격 구현에서 인증된 API를 호출합니다.
 * 로컬 모델은 API 전송용 DTO가 아닙니다. 퀴즈·이벤트 데이터는 해당 기능을 연결할 때 추가합니다.
 */
interface GameRepository {
    suspend fun answerQuiz(gameId: String, attemptId: String, choiceId: Int, request: GameActionRequest, requestId: String): GameState
    suspend fun resolveQuizTimeout(gameId: String, attemptId: String, request: GameActionRequest, requestId: String): GameState
    suspend fun decidePurchase(gameId: String, tileId: Int, action: com.finpop.app.core.model.PurchaseAction, request: GameActionRequest, requestId: String): GameState
    suspend fun completeTurn(gameId: String, turnId: String, request: GameActionRequest, requestId: String): GameState
    suspend fun createGame(request: CreateGameRequest, requestId: String): GameState
    suspend fun getGame(gameId: String): GameState
    suspend fun determineInitiative(gameId: String, request: GameActionRequest, requestId: String): InitiativeResult
    suspend fun rollDice(gameId: String, request: GameActionRequest, requestId: String): MovementResult
}


