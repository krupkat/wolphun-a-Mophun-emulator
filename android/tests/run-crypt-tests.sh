#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
if [[ -n "${JAVA_HOME:-}" ]]; then
  javac="$JAVA_HOME/bin/javac"
  java="$JAVA_HOME/bin/java"
else
  javac=javac
  java=java
fi
"$javac" --release 8 -d "$classes" \
  "$root/android/app/src/main/java/com/manedsoftware/wolphun/MophunCrypt.java" \
  "$root/android/tests/com/manedsoftware/wolphun/MophunCryptTest.java"
"$java" -cp "$classes" com.manedsoftware.wolphun.MophunCryptTest "$@"
