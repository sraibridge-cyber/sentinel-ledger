#!/usr/bin/env bash
# End-to-end check. Runs the privacy check, compiles the SDK, core and app, runs the unit tests,
# writes a calibration log through the SDK's own store, then checks it with the standalone verifier.
# Edited copies of the log must be rejected. Needs KOTLINC, ANDROID_JAR, JUNIT_JARS (junit.jar:hamcrest.jar) and python3.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${KOTLINC:?set KOTLINC to the kotlinc binary}"
: "${ANDROID_JAR:?set ANDROID_JAR to android.jar}"
: "${JUNIT_JARS:?set JUNIT_JARS to junit.jar:hamcrest.jar}"
STD="$(cd "$(dirname "$KOTLINC")/.." && pwd)/lib/kotlin-stdlib.jar"
VERIFY="$ROOT/verifier/verify_ledger.py"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/test" "$WORK/app" "$WORK/harness"

run() {
  local name=$1; shift
  if ! "$@" > "$WORK/$name.log" 2>&1; then
    echo "FAILED: $name"; tail -40 "$WORK/$name.log"; exit 1
  fi
  echo "  ok: $name ($(grep -c 'warning:' "$WORK/$name.log" || true) compiler warnings)"
}

echo "== privacy check"
(cd "$ROOT" && bash scripts/check-privacy.sh)

echo "== compile"
run core-sdk-tests "$KOTLINC" "$ROOT/core/src/main/java" "$ROOT/sdk/src/main/java" "$ROOT/core/src/test/java" \
  -cp "$ANDROID_JAR:$JUNIT_JARS:$STD" -jvm-target 17 -d "$WORK/test"
run android-app "$KOTLINC" "$ROOT/core/src/main/java" "$ROOT/sdk/src/main/java" "$ROOT/android/src/main/java" \
  -cp "$ANDROID_JAR:$STD" -jvm-target 17 -d "$WORK/app"

echo "== unit tests"
run unit java -cp "$WORK/test:$ANDROID_JAR:$JUNIT_JARS:$STD" org.junit.runner.JUnitCore \
  io.sentinel.core.MapperAlertsTest io.sentinel.core.SystemTaxTest io.sentinel.core.HeartbeatTest io.sentinel.core.CalibrationTest
grep "^OK" "$WORK/unit.log"

echo "== harness: write logs through the SDK store"
run harness-compile "$KOTLINC" "$ROOT/e2e/ChainHarness.kt" -cp "$WORK/test:$ANDROID_JAR:$STD" -jvm-target 17 -d "$WORK/harness"
run harness-run java -cp "$WORK/harness:$WORK/test:$ANDROID_JAR:$STD" ChainHarnessKt "$WORK"
cat "$WORK/harness-run.log"

expect_pass() {
  local label=$1; shift
  if python3 "$VERIFY" "$@" > "$WORK/v.out" 2>&1; then echo "  pass: $label"
  else echo "  FAILED, rejected: $label"; cat "$WORK/v.out"; exit 1; fi
}
expect_reject() {
  local label=$1; shift
  if python3 "$VERIFY" "$@" > "$WORK/v.out" 2>&1; then echo "  FAILED, accepted: $label"; exit 1
  else echo "  rejected as expected: $label ($(head -1 "$WORK/v.out"))"; fi
}

echo "== standalone verifier"
HEAD_HASH=$(tail -1 "$WORK/ledger.jsonl" | python3 -c 'import json,sys; print(json.loads(sys.stdin.read())["hash"])')
expect_pass "clean log" "$WORK/ledger.jsonl"
expect_pass "clean log with saved head hash" "$WORK/ledger.jsonl" "$HEAD_HASH"
expect_reject "wrong saved head hash" "$WORK/ledger.jsonl" "$(python3 -c 'print("0"*64)')"
sed 's/"verdict":"WITHIN_BASELINE"/"verdict":"OUTSIDE_BASELINE"/' "$WORK/ledger.jsonl" > "$WORK/tampered.jsonl"
grep -q "OUTSIDE_BASELINE" "$WORK/tampered.jsonl" || { echo "tamper edit did not apply"; exit 1; }
expect_reject "edited verdict" "$WORK/tampered.jsonl"
grep -v '"type":"CALIBRATION"' "$WORK/ledger.jsonl" > "$WORK/dropped.jsonl"
expect_reject "deleted baseline record" "$WORK/dropped.jsonl"
expect_reject "log with no GENESIS (SDK rejects it too)" "$WORK/no-genesis.jsonl"

echo "E2E PASSED"
