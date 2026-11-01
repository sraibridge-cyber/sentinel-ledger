# Known-event test

Purpose: show that the logger records real sensor events at the times they happened. A dated test record is stronger evidence than any claim about the logger.

## Before you start
- Install the build and grant usage access if you want the app shown beside each event (optional; the log says "not known" without it).
- Keep the phone charged and the logger running. Note the start time.
- Write each event down on paper with its time, as you do it. Use the phone's clock.

## The events (run each one, in this order)
1. Open the camera app for 10 seconds, then close it. Note the start and end time.
2. Start a voice memo with the screen off for 10 seconds, then stop it. Note the times.
3. Turn a VPN on, wait 10 seconds, turn it off. Note the times. (Skip if you have no VPN.)
4. Open the camera with the screen on, close it, then lock the phone and leave it.

## Pass criteria
- Each start and stop appears in the log within 5 seconds of your written time.
- Event 2 raises a screen-off alert. Event 1 and 4 do not, unless the screen was off.
- The log verifies with the verifier, and the head hash you save matches.
- Any missing heartbeat appears as a gap. Note any gap in your sheet.

## Record
Keep the sheet, the exported log, and the saved head hash together. Date them. That set is the test record.

## What this test does not show
- It does not show that the logger catches events other than these, or events while the app is stopped.
- It does not name the app that used a sensor, unless the foreground record shows one as a clue.
- It does not show that the phone was free of other monitoring before or after the test.
