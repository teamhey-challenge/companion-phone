#!/usr/bin/env bash
# 공모전 제출용 APK를 apk/ 폴더에 놓는다.
#   scripts/stage-apk.sh <팀명> <앱이름>
# 배포 서버의 최신 APK를 받아 SHA-256을 목록 페이지와 대조한 뒤 apk/<팀명>_<앱이름>_release.apk 로 저장한다.
# 팀명·앱 이름은 파일명에 그대로 쓰므로 공백·경로·특수문자는 거부한다.
# 환경변수:
#   APK_BASE  내려받을 주소(기본 https://hey.lastmile.ltd/download/phone)
#   APK_INDEX SHA-256 목록 페이지(기본 https://hey.lastmile.ltd/download/)
#   APK_OUT   저장 폴더(기본 <저장소>/apk)
#   APK_FILE  내려받지 않고 이미 만든 파일을 쓴다. 예: `./gradlew :app:assembleRelease` 뒤 dist/apk/*.apk
#             (배포 서버의 latest.apk는 CI가 만든 팀 디버그 키 서명본이다. 릴리스 키 서명본이 필요하면 APK_FILE로 준다.)
set -euo pipefail

[ "$#" -eq 2 ] || { echo "사용법: $0 <팀명> <앱이름>" >&2; exit 2; }
team=$1; app=$2
for v in "$team" "$app"; do
  # 경로·공백·특수문자만 막는다(한글은 로캘과 무관하게 통과시킨다).
  if [ -z "$v" ] || [ "${v#.}" != "$v" ] || printf '%s' "$v" | LC_ALL=C grep -q '[][/\\ \t:*?"<>|$`;&()'"'"'~!#%^{}=+,@]'; then
    echo "팀명·앱이름에 빈 값·점 시작·공백·경로·특수문자는 쓸 수 없어요: '$v'" >&2; exit 2
  fi
done

base=${APK_BASE:-https://hey.lastmile.ltd/download/phone}
index=${APK_INDEX:-https://hey.lastmile.ltd/download/}   # 폰·차 앱 파일 목록과 SHA-256이 있는 페이지
out_dir=${APK_OUT:-"$(cd "$(dirname "$0")/.." && pwd)/apk"}
out="$out_dir/${team}_${app}_release.apk"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

if [ -n "${APK_FILE:-}" ]; then
  [ -f "$APK_FILE" ] || { echo "APK_FILE이 없어요: $APK_FILE" >&2; exit 1; }
  cp "$APK_FILE" "$tmp/latest.apk"
else
  echo "목록 확인: $index"
  curl -fsS --connect-timeout 15 -m 60 "$index" -o "$tmp/index.html"
  echo "받는 중: $base/latest.apk"
  for t in 1 2 3 4 5 6; do
    curl -fsSL -C - --connect-timeout 15 --speed-limit 1000 --speed-time 30 -o "$tmp/latest.apk" "$base/latest.apk" && break
    [ "$t" -lt 6 ] || { echo "내려받기에 실패했어요" >&2; exit 1; }
    sleep 3
  done
  sha=$(sha256sum "$tmp/latest.apk" | awk '{print $1}')
  grep -q "$sha" "$tmp/index.html" || { echo "받은 APK의 SHA-256($sha)이 목록 페이지에 없어요. 다시 실행해 주세요." >&2; exit 1; }
fi
unzip -tq "$tmp/latest.apk" >/dev/null || { echo "APK가 손상됐어요" >&2; exit 1; }

# 서명 확인(apksigner가 있을 때만): 서명자 인증서를 출력한다.
signer=$(ls -d "${ANDROID_HOME:-/nonexistent}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)
if [ -n "${signer:-}" ]; then
  "$signer" verify --print-certs "$tmp/latest.apk" | awk '/certificate SHA-256 digest/{print "서명 인증서 " $0; exit}'
else
  echo "apksigner가 없어 서명 검증은 건너뛰어요(ANDROID_HOME/build-tools 필요)"
fi

mkdir -p "$out_dir"
rm -f "$out_dir"/*_release.apk
mv "$tmp/latest.apk" "$out"
echo "저장: $out"
echo "SHA-256: $(sha256sum "$out" | awk '{print $1}')"
echo "다음: git add apk && git commit -m 'chore: 제출용 APK' && git push (파일이 크면 GitHub Releases에 올리고 README에 링크)"
