# 로그인 화면 적용

Stitch의 `로그인_화면/code.html`, `DESIGN.md`, `screen.png`를 바탕으로 작성한 네이티브 Jetpack Compose 화면이다. 화면별 HTML과 스크린샷을 우선하여 연보라 배경, 둥근 버튼, 로고, 문구를 옮겼다.

## 파일과 실행 전제

- 화면: `app/src/main/java/com/finpop/app/feature/auth/LoginScreen.kt`
- 패키지: `com.finpop.app.feature.auth`
- 필요한 라이브러리: Compose UI, Foundation, Material 3, `ui-tooling-preview`, 디버그용 `ui-tooling`. Activity에서 사용하려면 `activity-compose`도 필요하다.
- 저장소의 Gradle 설정, AndroidManifest, MainActivity가 시작 화면을 거쳐 로그인 화면을 실행하도록 구성되어 있다. 시작 화면은 [시작 화면 안내](splash-compose.md)를 참고한다. AGP 8.9.1, Gradle 8.11.1, Kotlin/Compose Compiler 2.2.10, SDK 35, 최소 API 26을 사용한다.
- Android Studio에서 저장소 루트 `FinPOP`을 열고 Gradle Sync 후 `app`을 실행한다. 기존에 일반 폴더로 열었다면 프로젝트를 닫고 루트를 다시 열어 Gradle 프로젝트로 인식시킨다.
- Gradle JDK는 17을 사용한다. 이 PC에서는 `E:/6000.0.62f1/Editor/Data/PlaybackEngines/AndroidPlayer/OpenJDK`의 JDK 17로 확인했다. Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK에서 해당 경로 또는 별도의 JDK 17을 선택한다. 내장 JBR 25는 이 Gradle 버전과 호환되지 않는다.
- 개인 SDK 경로는 `local.properties`, 개인 JDK 경로는 `.gradle/config.properties`에 저장하며 Git에서 제외한다. 팀원은 자기 PC의 경로를 사용한다. IDE의 `GRADLE_LOCAL_JAVA_HOME` 선택은 `.gradle/config.properties`를 참조한다.
- Compose가 설정된 프로젝트에서 파일을 열고 Split 또는 Design을 선택하면 Preview를 볼 수 있다. 기본, 작은 화면, 큰 글꼴, 로딩 상태 Preview가 포함되어 있다.

## Activity 또는 Navigation에서 연결

기존 `setContent` 안에서 앱 테마로 감싸 호출한다. 아래의 각 함수는 호출하는 쪽에서 구현해야 하는 연결 지점이다.

```kotlin
import com.finpop.app.feature.auth.LoginScreen

// 기존 Activity의 setContent 또는 NavHost의 로그인 destination 내부
MaterialTheme {
    LoginScreen(
        onGoogleLogin = { /* Google 로그인 요청 */ },
        onGuestLogin = { /* Supabase 익명 인증 요청 */ },
        onTermsClick = { /* 이용약관 화면으로 이동 */ },
        onPrivacyClick = { /* 개인정보 처리방침 화면으로 이동 */ },
    )
}
```

Preview의 빈 콜백은 화면 확인 전용이다. 실제 로그인 기능이 구현된 것은 아니다. 인증 상태는 ViewModel 등 호출 측에서 관리하고, 진행 중에는 `loadingAction = LoginAction.Google` 또는 `LoginAction.Guest`, 완료/실패 후에는 `null`을 전달한다. 진행 중에는 두 인증 버튼이 비활성화된다. 실패 문구는 `errorMessage`로 전달한다.

API 연결 시 Google ID 토큰 교환 → `/v1/me/bootstrap` → 대기방 이동 흐름을 사용한다. 게스트의 기록 유지·계정 전환 정책은 미확정이므로 화면에서 임의로 결정하지 않는다. HTML의 일정 시간 후 버튼을 원상복구하는 데모 스크립트는 실제 인증 성공으로 취급하지 않았다.

## 디자인과 화면 크기

- 배경 `#FAF8FF`, 글자 `#0F172A`, 보조 글자 `#475569`, 게스트 버튼 `#F2F3FF`.
- 시스템 바 영역은 `safeDrawingPadding()`으로 피한다. 상위 Scaffold에서 이미 시스템 여백을 적용했다면 중복 여백이 없는지 확인한다.
- 작은 화면이나 큰 글꼴에서는 스크롤하며 버튼과 약관에 접근할 수 있다. 버튼은 최소 56dp, 약관은 Material TextButton의 터치 영역을 사용한다.
- 로고와 게임패드는 Canvas로 그리며, Google 마크는 HTML의 SVG 경로를 그대로 사용한다. 추가 이미지 다운로드나 아이콘 확장 라이브러리가 필요하지 않다.
- 제공 파일에는 웹 폰트 파일이 없다. 본문은 Android 기본 글꼴, 로고는 시스템의 rounded/bold 글꼴로 대체하여 원본 Fredoka 및 웹 글꼴과 글자 모양이 다를 수 있다. 최종 로고 원본 벡터나 폰트를 받으면 교체할 수 있다.

## 검증 범위

소스와 참조 HTML의 문구·색상·버튼 이벤트 구조를 대조했다. 2026-11-15에 JDK 17로 `./gradlew.bat :app:assembleDebug` 빌드 성공을 확인했으며 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성된다. Compose Preview와 기기 화면은 Android Studio에서 별도로 확인한다. MainActivity의 버튼은 연결 예정 안내를 표시하며 실제 인증은 아직 구현하지 않았다.
