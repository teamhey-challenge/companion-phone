# companion-phone

헤이파리 폰 앱 (Jetpack Compose, `ai.hey.phone`, minSdk 28 / targetSdk 35). D-36: W1 QR 입장·동의, W2 연결 완료, W3 기억·말투·연결 해제.

> 공모전 제출용 저장소다. 앱 이름·팀명은 아직 정해지지 않아 임시 이름 `companion-*`을 쓴다.

## 앱 소개

차 안의 캐릭터 AI 컴패니언 "헤이파리"의 **폰 앱**이다. 차 화면의 QR을 찍어 폰을 차와 연결하고(W1: 동의·연결, W2: 연결 완료), 컴패니언이 기억한 내용을 보고 하나씩 또는 전부 지우며 연결을 끊는다(W3). 동의 철회·삭제는 바로 서버에 반영된다.

## APK 위치

- 제출용 APK: `apk/<팀명>_<앱이름>_release.apk`. 팀명·앱 이름이 정해진 뒤 `scripts/stage-apk.sh <팀명> <앱이름>` 한 줄로 만든다(배포 서버의 최신 폰 APK를 받아 SHA-256을 목록과 대조하고 이름을 붙인다). 직접 빌드한 파일은 `APK_FILE=<경로> scripts/stage-apk.sh <팀명> <앱이름>`. 파일이 크면 GitHub Releases에 올리고 여기에 링크를 적는다.
- 항상 최신 빌드: `https://hey.lastmile.ltd/download/phone/latest.apk` (목록은 `https://hey.lastmile.ltd/download/`).

## 테스트 환경

- Android 에뮬레이터(API 35)와 Pleos 차량 에뮬레이터를 짝으로 놓고 QR 연결 흐름을 시험했다(`companion-e2e` 시나리오 P1). 폰 실기기 시험 결과는 이 저장소에 없다.
- 서버는 `https://hey.lastmile.ltd/api`(기본값, `-PserverUrl=`로 바꾼다). 앱 링크는 `https://hey.lastmile.ltd/p/{code}`.
- 실행: `./check.sh`(디버그 빌드 + 단위 시험), 설치는 `adb install`.

## 저장소 구성

| 저장소 | 내용 |
|---|---|
| [companion-car](https://github.com/darren-pleos-challenge/companion-car) | 차량 앱 |
| [companion-phone](https://github.com/darren-pleos-challenge/companion-phone) | 폰 앱(이 저장소) |
| [companion-server](https://github.com/darren-pleos-challenge/companion-server) | 대화·기억·TTS 서버 |
| [companion-web](https://github.com/darren-pleos-challenge/companion-web) | 폰 웹(폰 앱의 백업)·디자인 시스템 Storybook |
| [companion-shared](https://github.com/darren-pleos-challenge/companion-shared) | 디자인 토큰·프로토콜(서브모듈 `shared/`) |

## 구성과 빌드

- `shared/`: companion-shared 서브모듈, 차 앱과 같은 태그(v0.4.1). 색·글꼴 토큰은 `ai.hey:tokens-compose`(includeBuild)에서 온다.
- `app/src/main/kotlin/ai/hey/phone/`
  - `ui/`: 폰 디자인 시스템(HeyTheme, HeyText, HeyButton, GlassButton, ToggleRow, CheckRow, MemoryRow, HeyDialog, PageScaffold, HeaderBar…). 화면은 이것만 쓴다. 후속: companion-car `core:designsystem`과 한 벌로 합쳐 companion-shared/android로 옮긴다.
  - `data/`: `ApiClient`(OkHttp + kotlinx.serialization), `TokenStore`(EncryptedSharedPreferences).
  - `nav/`: `Routes`(`join/{code}`=W1, `done`=W2, `memory`=W3), `PhoneNavHost`, 딥링크 파싱.
  - `feature/pair/`: W1·W2. `feature/memory/`: W3.
- 링크: `https://hey.lastmile.ltd/p/{code}`(App Link, autoVerify) 와 `heyparri://p/{code}`.
- 서버 주소: `-PserverUrl=https://…`(기본 `https://hey.lastmile.ltd/api`).
- 확인: `./check.sh` (assembleDebug + 단위 시험). CI는 서명한 APK를 `phone-apk`로 올린다.
- 릴리스: `:app:assembleRelease`(R8 + 리소스 축소). `~/.config/hey/signing.properties`(차 앱과 같은 파일)가 있으면 그 키로 서명하고, 없으면 디버그 키로 서명한다. 시험용 변형 `:app:assembleReleaseE2e`는 릴리스와 같은 축소 + 디버그 서명이고 `-Pe2eServerUrl=`로 지정한 시험 서버에만 cleartext로 붙는다(`src/e2e/res`). 릴리스에는 cleartext가 없다. companion-e2e의 시나리오 P1이 이 변형으로 W1→W2→W3을 돈다.
- 폰 다크 모드는 시스템 설정을 따른다(D-26).
