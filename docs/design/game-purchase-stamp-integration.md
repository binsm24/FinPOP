# 땅 구매와 도장 연결

구현 위치: `feature/game/MainGameBoardScreen.kt`.

## 구매 성공 시

ViewModel의 UI 상태를 갱신하세요. 다음 예시는 MutableStateFlow를 사용하는 경우입니다.

```kotlin
// 구매가 성공했다고 확인한 뒤에만 실행합니다.
// eventId는 확정된 구매 1건의 안정적인 ID입니다. 재전달/재시도에도 같은 ID를 사용합니다.
val event = TilePurchaseUiEvent(
    eventId = purchaseEventId,
    tileId = mappedUiTileId,
    ownerId = if (ownerId == PlayerId.P1) GameBoardPlayerId.P1 else GameBoardPlayerId.P2,
)
_uiState.update { it.withPurchase(event) }
```

`withPurchase()`는 소유 목록 갱신과 이벤트 큐 추가를 함께 수행합니다. P1은 파란 도장, P2는 빨간 도장입니다. 해당 칸을 반대편 소유 목록에서 제거하며, 같은 eventId는 완료 후에도 현재 게임 상태에서 재처리하지 않습니다.

UI 칸 ID는 1, 2, 4, 5, 6, 8, 9, 11, 12, 13만 구매 대상으로 받습니다. 특수 칸과 잘못된 ID는 예외로 감지합니다. 팀원 시스템은 0번이 출발이고 UI는 7번이 출발이므로, 실제 GameRules.tiles와 대조하여 매핑한 뒤 전달하세요.

## 애니메이션 완료 연결

```kotlin
MainGameBoardScreen(
    state = uiState,
    onMenuClick = { /* 메뉴 */ },
    onTabSelected = { /* 탭 */ },
    onRollDice = { playerId -> /* 주사위 */ },
    onTileClick = { tileId -> /* 칸 상세 */ },
    onPurchaseAnimationFinished = { eventId ->
        viewModel.onPurchaseAnimationFinished(eventId)
    },
)

// ViewModel
fun onPurchaseAnimationFinished(eventId: String) {
    _uiState.update { it.purchaseAnimationFinished(eventId) }
}
```

큐의 첫 이벤트만 재생합니다. 약 650ms 동안 확대 → 내려찍기 → 반동 → 고정 순서로 진행하고 콜백으로 첫 이벤트를 제거하면 다음 이벤트가 시작됩니다. 완료 콜백은 반드시 연결하세요. 오래된 완료 콜백은 다음 이벤트를 제거하지 않습니다. 콜백이 재구성 중 바뀌어도 최신 콜백을 사용합니다.

## 저장된 게임 열기

기존 소유 상태는 `playerOwnedTiles`와 `aiOwnedTiles`에 직접 넣고 `pendingPurchases`는 비워 둡니다. 기존 도장은 정적으로 표시되고 새 구매처럼 재생되지 않습니다. `withPurchase()`는 새로 확정된 구매에만 사용하세요. 이벤트 큐는 화면에 진입하기 전의 전체 구매 이력을 재생하는 용도가 아닙니다.

ViewModel은 같은 게임 동안 큐와 수신 ID를 보관하고, 새 게임에는 새 GameBoardUiState를 만드세요. 프로세스 재시작/저장 게임 복원 시에는 최신 소유 상태를 조회하고 이벤트 스트림의 소비 위치도 복원하거나 기존 이력을 걸러야 합니다. UI의 수신 ID 집합만으로 프로세스 간 중복 처리를 보장하지 않습니다.

## 확인 방법

Android Studio의 `Board · purchase stamp demo`를 Interactive 모드로 실행하고 일반 칸을 누르세요. 파랑/빨강 구매를 번갈아 시뮬레이션하며, 빠르게 여러 칸을 누르면 큐 순서를 확인할 수 있습니다. 이 구매 시뮬레이션은 Preview 전용입니다. 실제 앱의 칸 클릭은 구매로 처리하지 않습니다.

현재 Navigation에는 완료 콜백까지 연결되어 있습니다. 실제 Repository 구매 성공 이벤트를 받는 연결은 팀원의 게임 시스템 통합 시 위 패턴으로 추가해야 합니다.
