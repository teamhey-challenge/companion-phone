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
- 릴리스: `:app:assembleRelease`(R8 + 리소스 축소). `~/.config/hey/signing.properties`(차 앱과 같은 파일)가 있으면 그 키로 서명하고, 없으면 디버그 키로 서명한다. R8 증명용 `:app:assembleReleaseE2e`는 릴리스와 같은 축소 + 디버그 서명이고, `-Pe2eServerUrl=http://100.69.201.20:<포트>`로 darren-vm의 시험 서버(cleartext는 이 주소만, `src/e2e/res`)에 붙는다. 릴리스에는 cleartext가 없다. companion-e2e의 시나리오 P1이 이 변형으로 W1→W2→W3을 돈다.
- 폰 다크 모드는 시스템 설정을 따른다(D-26).
