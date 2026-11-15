#!/usr/bin/env bash
# usage: ci/check-budgets.sh <apk>   (needs ANDROID_HOME with cmdline-tools; no jq/python in the android image)
# Compares APK size and dex method references with ci/budgets.json; writes out/budgets.md.
set -euo pipefail
apk=${1:?apk path}
cd "$(dirname "$0")/.."
analyzer="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
size=$(stat -c %s "$apk")
num() { sed -n "s/.*\"$1\": *\([0-9]*\).*/\1/p" ci/budgets.json | head -n 1; }
bsize=$(num apkBytes)
bdex=$(num dexMethodRefs)
dex=$("$analyzer" dex references "$apk" | awk '{ n += $NF } END { print n + 0 }')
mkdir -p out
{
  echo "| item | measured | budget |"
  echo "|---|---|---|"
  echo "| apk bytes | $size | $bsize |"
  echo "| dex method refs | $dex | $bdex |"
} | tee out/budgets.md
fail=0
[ "$size" -le "$bsize" ] || { echo "APK size $size > budget $bsize" >&2; fail=1; }
[ "$dex" -le "$bdex" ] || { echo "dex references $dex > budget $bdex" >&2; fail=1; }
exit $fail
