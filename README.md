# Sentinel Ledger

A phone app that records when the camera and microphone start and stop, and when watched settings change. It keeps those records in a tamper-evident log and alerts the owner. It does not claim to prove who is watching.

Status: design (DESIGN.md) and Stage 2 code in progress on feat/stage-2-logger. Nothing here has run on a phone yet.

- core/: the mapper (what happened and when) and alert rules. Plain Kotlin, tested with JUnit.
- android/: the background logger service and a simple start/stop screen.
- sdk/: the chain (hash-linked log) code, copied from the CSS Labs Android SDK.

The app has no internet permission and sends nothing off the phone. Exporting the log is the owner's choice.
