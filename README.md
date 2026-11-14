# companion-phone

헤이파리 폰 앱 (Jetpack Compose, `ai.hey.phone`, minSdk 28 / targetSdk 35). D-36: W1 QR 입장·동의, W2 연결 완료, W3 기억·말투·연결 해제.

- `shared/`: companion-shared 서브모듈, 차 앱과 같은 태그(v0.4.1). 색·글꼴 토큰은 `ai.hey:tokens-compose`(includeBuild)에서 온다.
- `app/src/main/kotlin/ai/hey/phone/`
  - `ui/`: 폰 디자인 시스템(HeyTheme, HeyText, HeyButton, GlassButton, ToggleRow, CheckRow, MemoryRow, HeyDialog, PageScaffold, HeaderBar…). 화면은 이것만 쓴다. 후속: companion-car `core:designsystem`과 한 벌로 합쳐 companion-shared/android로 옮긴다.
  - `data/`: `ApiClient`(OkHttp + kotlinx.serialization), `TokenStore`(EncryptedSharedPreferences).
  - `nav/`: `Routes`(`join/{code}`=W1, `done`=W2, `memory`=W3), `PhoneNavHost`, 딥링크 파싱.
  - `feature/pair/`: W1·W2. `feature/memory/`: W3.
- 링크: `https://hey.lastmile.ltd/p/{code}`(App Link, autoVerify) 와 `heyparri://p/{code}`.
- 서버 주소: `-PserverUrl=https://…`(기본 `https://hey.lastmile.ltd/api`).
- 확인: `./check.sh` (assembleDebug + 단위 시험). CI는 서명한 APK를 `phone-apk`로 올린다.
- 폰 다크 모드는 시스템 설정을 따른다(D-26).
