# 게임 초기 설정·주사위: Android 로컬 구현

기준: 노션 API 명세의 보드·규칙 조회 / 새 AI 대결 / 선공 결정 / 이동 주사위.
현재 구현은 단일 사용자 개발·테스트용 Kotlin Repository이다. HTTP API나 Supabase 서버가 아니다.

## 위치와 사용

- core/model/GameModels.kt: 보드·참가자·게임·추첨·이동 결과.
- data/game/GameRules.kt: 규칙 버전 1.0, 14칸 주제·금액.
- data/game/GameRepository.kt: 앱이 사용하는 suspend 인터페이스.
- data/game/InMemoryGameRepository.kt: 교체 가능한 메모리 구현.
- 테스트: app/src/test/java/com/finpop/app/data/game/InMemoryGameRepositoryTest.kt.

Repository 인스턴스를 유지한 상태에서 createGame → determineInitiative → rollDice → getGame 순서로 호출한다.
각 새로운 동작에는 UUID requestId를 생성하고, 실패 후 같은 동작을 재시도할 때는 같은 ID와 같은 요청을 유지한다.
expectedVersion은 마지막 응답 게임의 stateVersion이다. 같은 요청의 재시도는 버전 검사보다 먼저 처리된다.
앱 화면·인증 버튼에는 아직 연결하지 않았다. Kotlin 모델은 HTTP JSON DTO가 아니며 별도 직렬화가 필요하다.

## 지원하는 동작

양측 위치 0·700G·7턴, 소유권 초기화. 동률 재추첨과 선공 확정.
사용자 차례의 1~6 이동 경로와 위치 계산, 출발칸 도착·통과 시 200G.
선공·이동 시에는 남은 턴을 차감하지 않는다. 차례 마무리는 별도 구현 대상이다.
요청 ID 충돌, 상태 버전 충돌, AI 차례·중복 이동 차단. 한 인스턴스 내 처리는 잠금으로 직렬화한다.

## 의도적으로 남겨둔 범위

LANDING_PENDING은 로컬 중간 상태이며 노션의 서버 phase 계약에 추가된 상태가 아니다.
이동 후 캐릭터 능력, 퀴즈 발급, 황금열쇠·무인도 효과를 처리하기 전 여기에서 멈춘다.
QUIZ_OPEN을 반환하거나 미발급 퀴즈·미처리 효과를 성공한 것처럼 표시하지 않는다.
AI가 선공이면 사용자 이동은 차단된다. AI 차례 진행 기능 연결 전에는 대결을 계속할 수 없다.

실제 DB 구조를 받은 후 인증된 사용자, 캐릭터 해금·레벨·학습 단계를 서버에서 검증해야 한다.
GameProfile은 테스트 입력이며 클라이언트 값으로 운영 상태를 확정하면 안 된다.
운영에서는 서버가 주사위·보상·턴·저장을 확정하고 Android는 그 결과를 표시해야 한다.
서버 API 구현과 HTTP DTO, 영속 저장·다중 기기 중복 요청 처리는 아직 없다.
메모리 상태는 Repository 재생성·프로세스 종료 시 사라진다.

## 검증

JDK 17에서 저장소 루트의 gradlew.bat :app:testDebugUnitTest :app:assembleDebug 실행.


## 독립 테스트 화면

debug 소스셋의 com.finpop.app.debug.game.GameSetupTestActivity가 테스트 진입점이다.
기존 MainActivity·로그인·feature/game은 수정하지 않았다. release에는 테스트 Activity·화면·Manifest가 포함되지 않는다.
debug 앱 설치 후 앱 목록의 “FinPOP 게임 테스트” 아이콘으로 실행한다.
Android Studio의 Android App 실행 구성에서도 Launch를 Specified Activity로 선택해 위 Activity를 지정할 수 있다.

게임 생성 → 선공 결정 → 이동 주사위 → 저장 상태 다시 조회 순서로 확인한다.
일반 모드는 무작위 추첨이다. AI 선공일 때는 사용자 이동이 차단된다.
고정 시나리오는 사용자 선공(6:1)과 선택한 이동 값(1~6)을 재현한다. 서버 난수가 아닌 테스트 입력이다.
화면 재생성 시 상태가 초기화된다. 테스트 초기화 버튼은 저장소 자체를 새로 만든다.
첫 이동만 검증하므로 출발 보상은 0G가 정상이다. 한 바퀴 통과는 규칙 테스트에서 별도 검증한다.
