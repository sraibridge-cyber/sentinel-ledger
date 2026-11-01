#!/usr/bin/env bash
# Checks the repository's privacy promises: no internet permission, no network or analytics APIs,
# no outside addresses in the sources. Run from the repository root.
set -euo pipefail
fail=0
if grep -q 'android.permission.INTERNET' android/src/main/AndroidManifest.xml; then
  echo "FAIL: the manifest declares INTERNET"; fail=1
fi
NET='java\.net\.|HttpURLConnection|OkHttp|Retrofit|WebSocket|Firebase|Crashlytics|Amplitude|Mixpanel|Sentry'
if grep -RInE "$NET" core/src/main sdk/src/main android/src/main/java >/tmp/net.txt 2>/dev/null; then
  echo "FAIL: network or analytics API in the sources:"; head -n 10 /tmp/net.txt; fail=1
fi
if grep -RInE 'https?://' core/src/main sdk/src/main android/src/main/java >/tmp/url.txt 2>/dev/null; then
  echo "FAIL: outside address in the sources:"; head -n 10 /tmp/url.txt; fail=1
fi
if [ "$fail" -ne 0 ]; then echo "privacy checks: FAILED"; exit 1; fi
echo "privacy checks: PASSED"
