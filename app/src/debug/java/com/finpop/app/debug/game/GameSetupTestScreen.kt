package com.finpop.app.debug.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finpop.app.core.model.*
import com.finpop.app.data.game.*
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

/** 화면·저장소는 테스트 화면을 닫거나 재생성하면 초기화됩니다. */
@Composable
fun GameSetupTestScreen() {
    var fixed by remember { mutableStateOf(false) }
    var selectedDice by remember { mutableIntStateOf(1) }
    var repository by remember { mutableStateOf(InMemoryGameRepository()) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var initiative by remember { mutableStateOf<InitiativeResult?>(null) }
    var movement by remember { mutableStateOf<MovementResult?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
                message = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: GameException) {
                message = "${error.code}: ${error.message}"
            } catch (error: Exception) {
                message = "테스트 처리 실패: ${error.message}"
            } finally { busy = false }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("초기 설정·주사위 테스트", style = MaterialTheme.typography.headlineSmall)
            Text("로컬 메모리 검증용입니다. 실제 서버·DB·인증에는 연결하지 않습니다.")
            Row {
                Checkbox(checked = fixed, onCheckedChange = { fixed = it }, enabled = game == null && !busy)
                Text("고정 시나리오: 사용자 선공 + 선택한 이동 값")
            }
            if (fixed) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..6).forEach { value ->
                        FilterChip(
                            selected = selectedDice == value,
                            onClick = { selectedDice = value },
                            enabled = game == null && !busy,
                            label = { Text(value.toString()) },
                        )
                    }
                }
            }
            Button(enabled = game == null && !busy, onClick = {
                perform {
                    // 고정 모드는 선공 추첨 6:1 이후 선택한 이동 값을 사용합니다.
                    val queue = ArrayDeque(listOf(6, 1, selectedDice))
                    repository = if (fixed) InMemoryGameRepository(dice = { queue.removeFirst() })
                        else InMemoryGameRepository()
                    game = repository.createGame(CreateGameRequest(characterId = "CHEESE"), UUID.randomUUID().toString())
                    initiative = null
                    movement = null
                }
            }) { Text("1. 게임 생성") }

            val current = game
            var remainingSeconds by remember(current?.pendingQuiz?.attemptId) { mutableIntStateOf(20) }
            LaunchedEffect(current?.pendingQuiz?.attemptId) {
                val quiz = current?.pendingQuiz ?: return@LaunchedEffect
                while (true) {
                    val millis = java.time.Duration.between(Instant.now(), Instant.parse(quiz.deadlineAt)).toMillis()
                    remainingSeconds = ((millis.coerceAtLeast(0) + 999) / 1000).toInt()
                    if (millis <= 0 && !busy) {
                        perform {
                            val latest = requireNotNull(game)
                            if (latest.pendingQuiz?.attemptId == quiz.attemptId)
                                game = repository.resolveQuizTimeout(latest.gameId, quiz.attemptId,
                                    GameActionRequest(latest.stateVersion), UUID.randomUUID().toString())
                        }
                        break
                    }
                    delay(250)
                }
            }
            if (current != null) {
                Text("규칙 ${current.rulesVersion} · 상태 ${current.phase} · 버전 ${current.stateVersion}")
                Text("현재 차례: ${current.currentPlayerId ?: "선공 결정 전"}")
                current.players.forEach { player ->
                    Text("${player.playerId} (${if (player.isAi) "AI" else "사용자"}) · 위치 ${player.position} · ${player.gameGold}G · ${player.remainingTurns}턴")
                }
                Button(enabled = current.phase == GamePhase.INITIATIVE_PENDING && !busy, onClick = {
                    perform {
                        val result = repository.determineInitiative(current.gameId,
                            GameActionRequest(current.stateVersion), UUID.randomUUID().toString())
                        initiative = result
                        game = result.game
                    }
                }) { Text("2. 선공 결정") }
                initiative?.let { result ->
                    Text("추첨 기록: " + result.rolls.joinToString(" → ") { "${it.p1}:${it.p2}" })
                    Text("선공: ${result.firstPlayerId}")
                }
                Button(
                    enabled = current.phase == GamePhase.TURN_READY && current.currentPlayerId == PlayerId.P1 && !busy,
                    onClick = {
                        perform {
                            val result = repository.rollDice(current.gameId,
                                GameActionRequest(current.stateVersion), UUID.randomUUID().toString())
                            movement = result
                            game = result.game
                        }
                    },
                ) { Text("3. 이동 주사위") }
                movement?.let { result ->
                    Text("주사위: ${result.diceValue} · 경로: ${result.movementPath.joinToString(" → ")}")
                    Text("도착: ${result.arrivalTile.tileId}번 / ${result.arrivalTile.topic}")
                    Text("출발 보상: ${result.events.sumOf { it.amount }}G")
                }
                current.pendingQuiz?.let { quiz ->
                    HorizontalDivider()
                    Text("연결 검증용 예시 퀴즈 · 남은 시간 ${remainingSeconds}초",
                        style = MaterialTheme.typography.titleMedium)
                    Text(quiz.question)
                    quiz.choices.forEach { choice ->
                        OutlinedButton(enabled = !busy && remainingSeconds > 0, onClick = {
                            perform {
                                game = repository.answerQuiz(current.gameId, quiz.attemptId, choice.id,
                                    GameActionRequest(current.stateVersion), UUID.randomUUID().toString())
                            }
                        }) { Text("${choice.id}. ${choice.text}") }
                    }
                }
                current.lastQuizResult?.let { result ->
                    Text("판정: ${result.resolution} · 정답: ${result.correctChoiceId}")
                    Text(result.explanation)
                    result.payment?.let { Text("방문 정산: ${it.fromPlayerId} → ${it.toPlayerId}, ${it.amount}G") }
                }
                current.pendingPurchase?.let { pending ->
                    Text("${pending.tileId}번 칸 · 구매가 ${pending.price}G")
                    val enoughGold = current.players.first { it.playerId == PlayerId.P1 }.gameGold >= pending.price
                    if (!enoughGold) Text("G가 부족합니다. 구매를 건너뛰어 주세요.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && enoughGold, onClick = {
                            perform {
                                game = repository.decidePurchase(current.gameId, pending.tileId, PurchaseAction.BUY,
                                    GameActionRequest(current.stateVersion), UUID.randomUUID().toString())
                            }
                        }) { Text("칸 구매") }
                        OutlinedButton(enabled = !busy, onClick = {
                            perform {
                                game = repository.decidePurchase(current.gameId, pending.tileId, PurchaseAction.PASS,
                                    GameActionRequest(current.stateVersion), UUID.randomUUID().toString())
                            }
                        }) { Text("구매 패스") }
                    }
                }
                Button(enabled = current.phase == GamePhase.TURN_REVIEW && !busy, onClick = {
                    perform {
                        game = repository.completeTurn(current.gameId, requireNotNull(current.currentTurnId),
                            GameActionRequest(current.stateVersion), UUID.randomUUID().toString())
                    }
                }) { Text("4. 차례 마무리") }
                if (current.phase == GamePhase.SPECIAL_TILE_PENDING)
                    Text("특수칸 분기 확인: 무인도·황금열쇠 효과는 다음 구현 대상입니다. 효과를 생략한 채 차례를 종료하지 않습니다.")
                if (current.phase == GamePhase.TURN_READY && current.currentPlayerId == PlayerId.P2)
                    Text("현재 AI 차례입니다. AI 진행은 아직 연결하지 않았습니다. 테스트 초기화로 다시 확인할 수 있습니다.")
                OutlinedButton(enabled = !busy, onClick = {
                    perform { game = repository.getGame(current.gameId) }
                }) { Text("저장 상태 다시 조회") }
                HorizontalDivider()
                Text("현재 보드 소유권", style = MaterialTheme.typography.titleMedium)
                GameRules.tiles.forEach { tile ->
                    Text("${tile.tileId}. ${tile.topic} · ${tile.price}G · 소유: ${current.tiles.first { it.tileId == tile.tileId }.ownerPlayerId ?: "없음"}")
                }
            }
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(enabled = !busy, onClick = {
                repository = InMemoryGameRepository()
                game = null; initiative = null; movement = null; message = ""
            }) { Text("테스트 초기화") }
        }
    }
}


