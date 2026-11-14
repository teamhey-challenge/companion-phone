#!/usr/bin/env bash
# Local check for the phone app: build the debug APK, unit tests, design-system usage scan.
# Needs the submodule (git submodule update --init).
set -euo pipefail
cd "$(dirname "$0")"
[ -f shared/android/settings.gradle.kts ] || git submodule update --init
exec nice -n 19 ./gradlew --max-workers=2 :app:assembleDebug check "$@"
