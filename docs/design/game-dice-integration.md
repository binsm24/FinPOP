# 주사위 이벤트와 게임 보드 UI 연결

현재 저장소에는 팀원의 `core.model` 및 `GameRepository` 구현이 없습니다. 아래는 전달받은 코드에 적용할 통합 안내이며, 해당 저장소의 모델 선언과 AI 처리 코드를 확인한 뒤 적용해야 합니다. 첨부 파일 자체는 수정하지 않았습니다.

## UI 계약 (구현 완료)

`MainGameBoardScreen.kt`의 `GameBoardUiState`:

| 값 | 역할 |
| --- | --- |
| `currentPlayerId` | 현재 차례. P1만 터치로 굴릴 수 있음 |
| `dicePlayerId` | 굴리는 사람 또는 마지막 결과의 주인. P1 눈은 파랑, P2 눈은 빨강 |
| `diceValue` | Repository에서 확정한 1~6 결과 |
| `isRolling` | true이면 애니메이션, false이면 확정 결과 표시 |
| `canRollDice` | TURN_READY, 남은 턴, 무인도 대기 여부 등을 반영한 입력 허용 상태 |

`onRollDice: (GameBoardPlayerId) -> Unit`으로 클릭 주체를 받습니다. AI는 클릭을 흉내 내지 않고 게임 진행 계층에서 상태를 갱신합니다. `isPlayerTurn`은 이제 `currentPlayerId`에서 계산합니다.

현재 UI ID는 `GameBoardPlayerId`이며 도메인의 `PlayerId`와 별도입니다. 통합 위치(ViewModel 등)에 명시적으로 매핑하세요.

```kotlin
fun PlayerId.toBoardPlayerId(): GameBoardPlayerId = when (this) {
    PlayerId.P1 -> GameBoardPlayerId.P1
    PlayerId.P2 -> GameBoardPlayerId.P2
}
```

## 팀원 코드의 수정 위치

### 1. MovementResult에 굴린 사람 포함

`core.model`의 실제 `MovementResult` 선언에 다음 필드를 추가하세요. 기존 필드는 유지합니다.

```kotlin
val rolledBy: PlayerId
```

전달받은 Repository의 `rollDice()`에서 `val result = MovementResult(...)`를 만드는 부분:

```kotlin
val result = MovementResult(
    value, path, events, GameRules.tiles[path.last()], landed,
    rolledBy = player.playerId,
)
```

위 예시는 새 필드를 기존 생성자의 마지막에 추가한 경우입니다. 다른 생성 위치도 새 필드를 채워야 합니다. `copyMovement()`는 data class `copy()`를 사용하므로 새 필드를 그대로 보존합니다. 주사위 결과 필드의 실제 이름은 첨부에 모델 선언이 없어 확인되지 않았습니다. 아래 `result.diceValue` 예시는 실제 첫 번째 필드 이름에 맞춰 주세요.

`landed.currentPlayerId`로 결과 주인을 추측하지 마세요. 착지/턴 처리에서 다음 사람으로 바뀔 수 있으므로 이동 전의 `player.playerId`를 기록합니다.

### 2. rollDice의 P1 제한과 AI 경로

첨부 `rollDice()`에는 아래 P1 전용 처리 세 곳이 있습니다.

```kotlin
checkGame(game.currentPlayerId == PlayerId.P1, ...)
val player = game.players.first { it.playerId == PlayerId.P1 }
MovementEvent("START_REWARD", PlayerId.P1, reward)
```

현재 상태로는 AI 요청을 이 함수에 전달해도 실패합니다. 사용자용 `rollDice()`의 P1 검증은 유지하고, 게임 진행 계층에서 호출하는 AI 전용 경로를 추가하는 편이 안전합니다. 공통 이동 계산을 내부 함수로 분리한다면:

- 사용자 진입점은 P1 차례를 검사하고, AI 진입점은 P2 차례를 검사합니다.
- 내부 함수는 검증된 `actorId`를 받아 `players.first { it.playerId == actorId }`로 대상을 찾습니다.
- 보상 이벤트의 ID는 `player.playerId`, 결과의 `rolledBy`도 `player.playerId`로 넣습니다.
- phase/currentTurnId/remainingTurns/islandWaitTurns, 버전 검사, 잠금, 요청 재사용 처리를 보존합니다.
- 요청 캐시 `Command`에는 AI 작업 구분 또는 actorId를 포함해 다른 주체의 요청이 동일 명령으로 취급되지 않게 합니다.

사용자가 넘긴 ID만 믿고 P1 제한을 삭제하면 안 됩니다. 첨부의 `mutate()` 역시 P1 전용이므로 AI의 퀴즈/구매/턴 완료까지 구현할 때는 그 경로도 별도로 점검해야 합니다.

### 3. ViewModel에서 UI 상태 갱신

다음은 연결 순서 예시입니다. `uiState`, `repository`, `request`, 오류 처리는 실제 ViewModel 구조에 맞춰 적용하세요.

```kotlin
// 호출 직전 game.currentPlayerId를 보관합니다.
val actorId = game.currentPlayerId
uiState = uiState.copy(
    dicePlayerId = actorId.toBoardPlayerId(),
    isRolling = true,
    canRollDice = false,
)
try {
    // 최소 900ms 표시와 저장소 요청을 함께 기다립니다.
    val result = coroutineScope {
        val movement = async { repository.rollDice(game.gameId, request, requestId) }
        delay(900)
        movement.await()
    }
    // 위 호출은 P1 전용입니다. AI 턴은 AI 전용 진입점의 결과를 받습니다.
    game = result.game
    uiState = uiState.copy(
        currentPlayerId = game.currentPlayerId.toBoardPlayerId(),
        dicePlayerId = result.rolledBy.toBoardPlayerId(),
        diceValue = result.diceValue, // 실제 MovementResult 필드 이름 확인
        isRolling = false,
        canRollDice = false, // 착지 이벤트 처리 후 최신 game 상태로 다시 계산
    )
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    // 오류 안내 및 최신 게임 상태 재조회. 실패를 성공 결과로 표시하지 않습니다.
    uiState = uiState.copy(isRolling = false, canRollDice = false)
} finally {
    uiState = uiState.copy(isRolling = false)
}
```

실제 요청 중복 방지도 ViewModel에서 수행하세요. `requestId`는 UUID이며 같은 명령 재시도 시 유지하고, 새 명령에는 새 ID를 사용합니다. `request.expectedVersion`은 최신 `game.stateVersion`으로 구성합니다.

입력 가능 여부는 최소한 `game.phase == GamePhase.TURN_READY`, `game.currentTurnId != null`, P1 차례, 해당 플레이어의 `remainingTurns > 0`, `islandWaitTurns == 0`, 요청 진행 여부를 함께 반영하세요. UI는 서버/Repository 검증을 대신하지 않습니다.

`Navigation.kt`의 `Random.nextInt(1, 7)`과 `delay(900)`은 현재 화면 확인용 로컬 데모입니다. 실제 통합 때는 이 상태 생성 부분을 ViewModel 상태로 교체하세요. 최종 눈을 UI에서 다시 추첨하지 않습니다.

## 보드 위치를 연결할 때 별도 확인

첨부 로직은 **0번이 출발**이지만 디자인 기반 UI는 **0번이 무인도, 7번이 출발**입니다. 팀원의 전체 `GameRules.tiles`를 확인해 ID 매핑을 만든 뒤 말 위치·점유·칸 클릭 ID를 연결해야 합니다. 현재 위치 숫자를 그대로 복사하면 다른 칸에 표시될 수 있습니다.
