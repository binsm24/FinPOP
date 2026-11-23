package com.finpop.app.debug.game

import com.finpop.app.feature.game.GameBoardPlayerId
import com.finpop.app.feature.game.GameBoardUiState
import org.json.JSONObject

/** 서버는 출발 0번, 화면은 출발 7번입니다. 화면 파일의 칸 정의는 유지합니다. */
internal fun serverTileToBoard(tileId: Int): Int {
    require(tileId in 0..13)
    return (tileId + 7) % 14
}

internal fun boardTileToServer(tileId: Int): Int {
    require(tileId in 0..13)
    return (tileId + 7) % 14
}

internal fun canRollOnServerBoard(
    phase: String, playerId: String?, turns: Int, islandWait: Int, blocked: Boolean,
): Boolean = phase == "TURN_READY" && playerId == "P1" && turns > 0 && islandWait == 0 && !blocked

/** 서버가 확정한 수치를 화면 상태로 바꾸며 화면의 샘플 기본값을 사용하지 않습니다. */
internal fun JSONObject.toServerBoardState(
    dice: Int, roller: GameBoardPlayerId, rolling: Boolean, blocked: Boolean,
    animatedPosition: Int?,
): GameBoardUiState {
    val players = getJSONArray("players")
    fun player(id: String): JSONObject = (0 until players.length())
        .map { players.getJSONObject(it) }.single { it.getString("playerId") == id }
    val user = player("P1")
    val ai = player("P2")
    val tiles = getJSONArray("tiles")
    fun owned(id: String): Set<Int> = (0 until tiles.length()).map { tiles.getJSONObject(it) }
        .filter { it.optString("ownerPlayerId") == id }
        .map { serverTileToBoard(it.getInt("tileId")) }.toSet()
    val active = optString("currentPlayerId")
    return GameBoardUiState(
        playerGold = user.getInt("gameGold"), aiGold = ai.getInt("gameGold"),
        playerTurnsRemaining = user.getInt("remainingTurns"), aiTurnsRemaining = ai.getInt("remainingTurns"),
        currentPlayerId = if (active == "P2") GameBoardPlayerId.P2 else GameBoardPlayerId.P1,
        dicePlayerId = roller,
        canRollDice = canRollOnServerBoard(optString("phase"), active,
            user.getInt("remainingTurns"), user.getInt("islandWaitTurns"), blocked),
        diceValue = dice, isRolling = rolling,
        playerPosition = serverTileToBoard(if (roller == GameBoardPlayerId.P1) animatedPosition ?: user.getInt("position") else user.getInt("position")),
        aiPosition = serverTileToBoard(if (roller == GameBoardPlayerId.P2) animatedPosition ?: ai.getInt("position") else ai.getInt("position")),
        playerOwnedTiles = owned("P1"), aiOwnedTiles = owned("P2"),
    )
}

/** AI 요청도 현재 AI 차례이며 이동 가능한 상태일 때만 보냅니다. */
internal fun canRequestAiMovement(phase: String, playerId: String?, blocked: Boolean): Boolean =
    phase == "TURN_READY" && playerId == "P2" && !blocked