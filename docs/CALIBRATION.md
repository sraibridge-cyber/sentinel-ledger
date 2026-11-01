# On-device calibration: how the device gets its own baseline

## What it can and cannot tell the owner

It can tell whether the device's current timing and clock readings match this device's own calibrated normal. It cannot tell "private" from "monitored." Nothing on the phone can see who is watching, and there are no labelled examples of monitored phones to set a monitored threshold. The app says "within baseline" or "outside baseline" and never "monitored."

## Steps

1. **Owner confirms the device is clean.** The owner ticks a statement: "I have checked this phone by other means and I believe it is clean." The app writes the owner's confirmation into the log. A baseline taken on a compromised phone records the wrong state as normal, so this step matters.
2. **Collect.** The app samples timer lateness and clock skew about once a minute for at least 30 minutes, with the screen both on and off.
3. **Score each reading.** Each reading maps to two domain scores (timer and clock) with a fixed mapping, `v1-uncalibrated`. Those scales and weights are our choice and are not measured. The reading's mu is the weighted geometric mean of the two scores.
4. **Derive the threshold.** tau is the lowest calibration mu minus a margin of 0.01. With fewer than 30 samples, the app sets no threshold at all and says so.
5. **Seal the baseline.** A CALIBRATION record goes into the log with the sample count, the lowest and median mu, tau, the mapping version, and the time range. Values are stored as integers, for example mu x 1,000,000, so the standalone verifier can check them.
6. **Check at runtime.** Each reading is compared with tau. The result is logged as WITHIN_BASELINE or OUTSIDE_BASELINE, with the note that this is not a judgement about who is watching.
7. **Recalibrate only when the owner asks.** Each new baseline is a new record. Older baselines stay in the log.

## Limits

- One device's calibration gives its own reference, not a population norm.
- The lowest of 30 samples is a weak estimate. More samples, taken across more conditions, give a steadier threshold.
- The mapping and margin are not calibrated. They are documented choices, and their effect should be tested before anyone relies on them.
- Any verdict about monitoring would need labelled examples of monitored and unmonitored phones. We do not have those, and the app does not claim one.
