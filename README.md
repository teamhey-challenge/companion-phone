# companion-phone

헤이리 폰 앱 (Jetpack Compose, `ai.hey.phone`, minSdk 28 / targetSdk 35). D-36: W1 QR 입장·동의, W2 연결 완료, W3 기억·말투·연결 해제. D-47: 차 없이 가입, 오늘·대화·친구·기억·설정 홈.

> 공모전 제출용 저장소다. 앱 Hey(캐릭터 헤이리), 팀 Team Hey. 저장소 이름 `companion-*`은 그대로다.

## 앱 소개

차 안의 캐릭터 AI 컴패니언 "헤이리"의 **폰 앱**이다. 차 화면의 QR을 찍어 폰을 차와 연결하거나(W1·W2), 차 없이 이름만 넣고 시작한다. 홈은 오늘(같이 다닌 하루) · 대화(글·음성) · 친구(코드·링크·QR, 메시지, 도착 알리기) · 기억(W3) · 설정이다. 친구 소식은 알림으로 온다. 동의 철회·삭제는 바로 서버에 반영된다.

## APK 위치

- 제출용 APK: `apk/TeamHey_Hey-phone_release.apk`(릴리스 키 서명본). 다시 만들려면 `scripts/stage-apk.sh --release TeamHey Hey-phone`. 파일이 크면 GitHub Releases에 올리고 여기에 링크를 적는다.
- 항상 최신 빌드: `https://hey.lastmile.ltd/download/phone/latest.apk` (목록은 `https://hey.lastmile.ltd/download/`).

## 테스트 환경

- Android 에뮬레이터(API 35)와 Pleos 차량 에뮬레이터를 짝으로 놓고 QR 연결 흐름을 시험했다(`companion-e2e` 시나리오 P1). 폰 실기기 시험 결과는 이 저장소에 없다.
- 서버는 `https://hey.lastmile.ltd/api`(기본값, `-PserverUrl=`로 바꾼다). 앱 링크는 `https://hey.lastmile.ltd/p/{code}`.
- 실행: `./check.sh`(디버그 빌드 + 단위 시험), 설치는 `adb install`.

## 저장소 구성

| 저장소 | 내용 |
|---|---|
| [companion-car](https://github.com/teamhey-challenge/companion-car) | 차량 앱 |
| [companion-phone](https://github.com/teamhey-challenge/companion-phone) | 폰 앱(이 저장소) |
| [companion-server](https://github.com/teamhey-challenge/companion-server) | 대화·기억·TTS 서버 |
| [companion-web](https://github.com/teamhey-challenge/companion-web) | 폰 웹(폰 앱의 백업)·디자인 시스템 Storybook |
| [companion-shared](https://github.com/teamhey-challenge/companion-shared) | 디자인 토큰·프로토콜(서브모듈 `shared/`) |

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
