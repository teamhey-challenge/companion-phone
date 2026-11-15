# companion-phone

앱 이름 **Hey**(캐릭터 헤이리), 팀 **Team Hey**. 저장소 이름 `companion-*`은 그대로다.

차 안의 캐릭터 AI 컴패니언 "헤이리"의 **폰 앱** 저장소다. 차 화면의 QR(또는 코드)로 폰을 차와 연결한다. 홈은 오늘(같이 다닌 하루) · 대화(글·음성) · 친구(코드·링크·QR, 메시지, 도착 알리기) · 기억 · 설정이다. 기억은 여기서 보고 지울 수 있고, 동의 철회·삭제는 바로 서버에 반영된다. Jetpack Compose, 패키지 `ai.hey.phone`, minSdk 28 / targetSdk 35.

## 제출물과 주소

| 항목 | 위치 |
|---|---|
| 폰 앱 APK(릴리스 키 서명) | `apk/TeamHey_Hey-phone_release.apk`, 항상 최신본은 https://hey.lastmile.ltd/download/phone/latest.apk |
| 차량 앱 APK | companion-car 저장소 `apk/TeamHey_Hey_release.apk`, 최신본은 https://hey.lastmile.ltd/download/latest.apk |
| 서버 | https://hey.lastmile.ltd/api (앱 기본값) |
| 앱 링크 | https://hey.lastmile.ltd/p/{code} (App Link) |
| 파일 목록·SHA-256 | https://hey.lastmile.ltd/download/ |

APK를 다시 만들려면 `scripts/stage-apk.sh --release TeamHey Hey-phone`.

## 화면 규격

세로 방향 폰 화면용이다(터치 타깃 48 dp 이상, 시스템 글자 1.3배까지 레이아웃 유지, 밝은/어두운 테마는 시스템 설정을 따른다). 차 화면 규격(1600x900 dp, 멀티윈도우 1251/840/560 dp)은 companion-car 저장소에 있다.

## 테스트 환경

- Android 에뮬레이터(API 35)와 Pleos Connect 에뮬레이터(`pleos_connect_v2_1`)를 짝으로 놓고 QR 연결 흐름을 시험했다(`companion-e2e` 시나리오 P1). 폰 실기기 시험 결과는 이 저장소에 없다.
- 빌드: JDK 17, Gradle 8.13(래퍼), Android Gradle Plugin 8.11.2, Kotlin 2.1.0. JDK 17과 Android SDK가 있으면 Android Studio 없이도 빌드된다.

## 빌드와 실행

```sh
git clone --recurse-submodules https://github.com/teamhey-challenge/companion-phone.git
cd companion-phone
./check.sh                       # 디버그 빌드 + 단위 시험
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

제출용 APK를 그대로 쓰려면 `adb install -r apk/TeamHey_Hey-phone_release.apk`. 서버 주소를 바꾸려면 `-PserverUrl=https://…`(기본 `https://hey.lastmile.ltd/api`). 릴리스는 `:app:assembleRelease`(R8 + 리소스 축소)이고 릴리스에는 cleartext 통신이 없다.

## 차와 연결하기

차 앱(companion-car)에 뜬 QR을 앱 안 스캐너로 찍거나 코드를 직접 입력해 차를 연결한다. 심사자 에뮬레이터에서 차량 앱이 서버에 등록되려면 등록 비밀 파일이 필요하다. 팀이 요청 시 전달하며, 자세한 방법은 companion-car README에 있다. 폰 앱에는 비밀이 없다.

## 사용하는 SDK

폰 앱은 Pleos SDK(Vehicle·NaviHelper·SpeechToText·TextToSpeech)를 쓰지 않는다. 이 SDK들은 차량 앱(companion-car)이 쓴다. 폰 앱의 음성 입력은 Android의 `SpeechRecognizer`(한국어, 한 문장, 첫 마이크 탭에서 RECORD_AUDIO 권한 요청)이고, 서버에는 인식된 글자만 보낸다.

## 개인정보

- 폰 앱은 위치 권한을 요청하지 않고 좌표를 저장하지 않는다. 마이크 오디오는 서버로 보내지 않는다(차량 쪽 음성 인식은 Pleos 온디바이스 STT).
- 동의 철회·기억 삭제는 바로 서버에 반영된다. 토큰은 `EncryptedSharedPreferences`에 저장한다.

## 구성

- `shared/`: companion-shared 서브모듈(차 앱과 같은 태그). 색·글꼴 토큰은 `ai.hey:tokens-compose`에서 온다.
- `app/src/main/kotlin/ai/hey/phone/`: `ui/`(폰 디자인 시스템), `data/`(`ApiClient`, `TokenStore`), `nav/`(라우트·딥링크), `feature/`(화면).
- 앱 링크는 `https://hey.lastmile.ltd/p/{code}`와 `heyparri://p/{code}`.

## 저장소 구성

| 저장소 | 내용 |
|---|---|
| [companion-car](https://github.com/teamhey-challenge/companion-car) | 차량 앱 |
| [companion-phone](https://github.com/teamhey-challenge/companion-phone) | 폰 앱(이 저장소) |
| [companion-server](https://github.com/teamhey-challenge/companion-server) | 대화·기억·TTS 서버 |
| [companion-web](https://github.com/teamhey-challenge/companion-web) | 폰 웹(폰 앱의 백업)·디자인 시스템 Storybook |
| [companion-shared](https://github.com/teamhey-challenge/companion-shared) | 디자인 토큰·프로토콜(서브모듈 `shared/`) |
