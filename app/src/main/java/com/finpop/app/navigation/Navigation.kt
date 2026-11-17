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
import kotlinx.coroutines.delay
import kotlin.random.Random
import com.finpop.app.ui.component.MainTab

/** Local guest entry only; server authentication is connected separately. */
@Composable
fun Navigation(onMessage: (String) -> Unit) {
    var showGameBoard by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showGameBoard) { showGameBoard = false }

    Crossfade(targetState = showGameBoard, label = "Login to game board") { gameBoard ->
        if (gameBoard) {
            var diceValue by rememberSaveable { mutableIntStateOf(4) }
            var isRolling by remember { mutableStateOf(false) }
            var dicePlayerId by rememberSaveable { mutableStateOf(GameBoardPlayerId.P1) }
            LaunchedEffect(isRolling) {
                if (isRolling) {
                    delay(900)
                    diceValue = Random.nextInt(1, 7)
                    isRolling = false
                }
            }
            MainGameBoardScreen(
                state = GameBoardUiState(diceValue = diceValue, isRolling = isRolling, dicePlayerId = dicePlayerId),
                onMenuClick = { onMessage("메뉴 연결 예정") },
                onTabSelected = { tab ->
                    if (tab != MainTab.Board) onMessage("${tab.label} 화면 연결 예정")
                },
                onRollDice = { playerId ->
                    if (!isRolling && playerId == GameBoardPlayerId.P1) {
                        dicePlayerId = playerId
                        isRolling = true
                    }
                },
                onTileClick = { onMessage("보드 칸 상세 화면 연결 예정") },
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
