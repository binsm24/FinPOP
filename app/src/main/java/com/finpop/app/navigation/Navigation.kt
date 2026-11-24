package com.finpop.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.finpop.app.feature.auth.LoginScreen
import com.finpop.app.feature.game.MainGameBoardScreen
import com.finpop.app.feature.game.GameBoardUiState
import com.finpop.app.feature.game.GameBoardPlayerId
import com.finpop.app.feature.game.TilePurchaseUiEvent
import com.finpop.app.feature.game.gameBoardDestination
import com.finpop.app.feature.game.quizTile
import com.finpop.app.feature.quiz.AiQuizResultPopup
import com.finpop.app.feature.quiz.TileQuizPopup
import kotlinx.coroutines.delay
import kotlin.random.Random
import com.finpop.app.ui.component.MainTab

/** Local guest entry only; server authentication is connected separately. */
@Composable
fun Navigation(
    onMessage: (String) -> Unit,
    // Temporary guest-game fallback until the team quiz resolver is wired in.
    resolveAiQuiz: (tileId: Int) -> Boolean = { Random.nextBoolean() },
) {
    var showGameBoard by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showGameBoard) { showGameBoard = false }

    Crossfade(targetState = showGameBoard, label = "Login to game board") { gameBoard ->
        if (gameBoard) {
            var boardState by remember { mutableStateOf(GameBoardUiState()) }
            var diceValue by rememberSaveable { mutableIntStateOf(4) }
            var isRolling by remember { mutableStateOf(false) }
            var dicePlayerId by rememberSaveable { mutableStateOf(GameBoardPlayerId.P1) }
            var activeQuizTileId by remember { mutableStateOf<Int?>(null) }
            var quizCollapsed by remember { mutableStateOf(false) }
            var aiQuizCorrect by remember { mutableStateOf<Boolean?>(null) }
            val activeTile = activeQuizTileId?.let(::quizTile)
            val aiTile = aiQuizCorrect?.let { quizTile(boardState.aiPosition) }
            val hasPopup = activeTile != null || aiTile != null

            fun finishAiTurn() {
                aiQuizCorrect = null
                boardState = boardState.copy(currentPlayerId = GameBoardPlayerId.P1)
            }

            fun finishPlayerTurn() {
                activeQuizTileId = null
                quizCollapsed = false
                if (boardState.aiTurnsRemaining > 0) {
                    boardState = boardState.copy(currentPlayerId = GameBoardPlayerId.P2)
                    dicePlayerId = GameBoardPlayerId.P2
                    isRolling = true
                }
            }

            LaunchedEffect(isRolling, dicePlayerId) {
                if (isRolling && dicePlayerId == GameBoardPlayerId.P1) {
                    delay(900)
                    diceValue = Random.nextInt(1, 7)
                    val destination = gameBoardDestination(boardState.playerPosition, diceValue)
                    boardState = boardState.copy(
                        playerPosition = destination,
                        playerTurnsRemaining = boardState.playerTurnsRemaining - 1,
                    )
                    isRolling = false
                    activeQuizTileId = quizTile(destination)?.id
                    if (activeQuizTileId == null) finishPlayerTurn()
                } else if (isRolling) {
                    delay(900)
                    diceValue = Random.nextInt(1, 7)
                    val destination = gameBoardDestination(boardState.aiPosition, diceValue)
                    val toll = if (destination in boardState.playerOwnedTiles) {
                        (quizTile(destination)?.price ?: 0) / 2
                    } else 0
                    val paid = toll.coerceAtMost(boardState.aiGold)
                    boardState = boardState.copy(
                        aiPosition = destination,
                        aiTurnsRemaining = boardState.aiTurnsRemaining - 1,
                        aiGold = boardState.aiGold - paid,
                        playerGold = boardState.playerGold + paid,
                    )
                    isRolling = false
                    if (quizTile(destination) == null) finishAiTurn()
                    else aiQuizCorrect = resolveAiQuiz(destination)
                }
            }
            MainGameBoardScreen(
                state = boardState.copy(
                    diceValue = diceValue, isRolling = isRolling, dicePlayerId = dicePlayerId,
                    canRollDice = !hasPopup && boardState.canRollDice,
                ),
                onPurchaseAnimationFinished = { eventId ->
                    boardState = boardState.purchaseAnimationFinished(eventId)
                },
                onMenuClick = { if (!hasPopup) onMessage("메뉴 연결 예정") },
                onTabSelected = { tab ->
                    if (!hasPopup && tab != MainTab.Board) onMessage("${tab.label} 화면 연결 예정")
                },
                onRollDice = { playerId ->
                    if (!isRolling && !hasPopup && boardState.isPlayerTurn &&
                        boardState.playerTurnsRemaining > 0 && playerId == GameBoardPlayerId.P1
                    ) {
                        dicePlayerId = playerId
                        isRolling = true
                    }
                },
                onTileClick = { onMessage("보드 칸 상세 화면 연결 예정") },
                blurBoard = hasPopup && !quizCollapsed,
                overlay = activeTile?.let { tile ->
                    {
                        val available = tile.id !in boardState.playerOwnedTiles && tile.id !in boardState.aiOwnedTiles
                        TileQuizPopup(
                            tile = tile,
                            gold = boardState.playerGold,
                            canAcquire = available,
                            collapsed = quizCollapsed,
                            onToggleCollapsed = { quizCollapsed = !quizCollapsed },
                            onAcquire = {
                                if (activeQuizTileId == tile.id && available && boardState.playerGold >= tile.price) {
                                    boardState = boardState.copy(playerGold = boardState.playerGold - tile.price)
                                        .withPurchase(TilePurchaseUiEvent(
                                            "player-turn-${boardState.playerTurnsRemaining}", tile.id, GameBoardPlayerId.P1,
                                        ))
                                    finishPlayerTurn()
                                }
                            },
                            onFinish = ::finishPlayerTurn,
                        )
                    }
                } ?: aiTile?.let { tile ->
                    val correct = aiQuizCorrect == true
                    { AiQuizResultPopup(tile, correct, onConfirm = ::finishAiTurn) }
                },
            )
        } else {
            LoginScreen(
                onGoogleLogin = { onMessage("Google 로그인 연결 예정") },
                onGuestLogin = { showGameBoard = true },
                onTermsClick = { onMessage("이용약관 화면 연결 예정") },
                onPrivacyClick = { onMessage("개인정보 처리방침 화면 연결 예정") },
            )
        }
    }
}
