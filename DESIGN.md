# Sensor array and evidence log: design (draft 1)

Status: draft for Q&A. Nothing here is built or decided. Open questions are at the end.

## What this is

A phone app that watches the device's own sensors and settings for changes and keeps a tamper-evident record of what it saw. It works from the owner's own phone, with the owner's consent, and reads only what Android lets an ordinary app read. It is the successor to SIMM (System Information Metrics Monitor), which stays as the readings tool.

## What Android lets an ordinary app see

Be precise about this, because the honest limits are the design.

- **Settings and state, readable:** accessibility services, notification listeners, device administrators, user-installed certificates, VPN and proxy, developer options. SIMM already reads these.
- **Camera and microphone, as events and counts:** Android reports when the camera becomes busy or free, and when audio recording starts or stops. These give exact times. Whether they reveal the app's name is not established. Verify on a device before designing around it.
- **Location, not readable:** an ordinary app cannot see which other app is using location.
- **Other apps' sensor history, not readable:** Android's own Privacy dashboard lists which apps used the camera, mic and location over the last day. Other apps cannot read that list. The owner can read it on screen.

So the sensor array can record when the camera or mic was in use, and what the device's settings were. It cannot record which app used them, or any location use, unless Android changes what it exposes.

## The evidence log

- Every reading and every event is a record in an append-only file on the device. Each record stores the hash of the record before it (a hash chain), using SHA-256.
- Records are also grouped into a Merkle tree. The root of the tree summarises a batch, and a single record can be proved to be in a batch without sending the whole log.
- "Immutable" is the wrong word. The log is tamper-evident. Anyone with root access to the phone can delete the whole file and write a new one. The chain only shows that edits or deletions happened after a point the owner kept a copy of. The head hash is the copy to keep off the phone.

## Temporal anchoring and export

Anchoring means a third party vouches that a given root existed at a given time. The usual method is an RFC 3161 timestamp request, or a public transparency log.

**Decision (Kyle, Q1):** the app never phones home. It makes no network request of its own. Anything that leaves the phone does so because the owner exports it. The app must make export easy: the log as Markdown or JSON, plus the head hash. Anchoring, if wanted, happens on a computer from the exported root, not on the phone.

## The protective part

"Protective" should mean the owner is told when something changes. Candidate alerts:

- A new device administrator, certificate, notification listener or accessibility service.
- VPN or proxy switched on or off.
- Camera or mic in use while the screen is off.

Each alert is a flag, not a verdict. The app should say what changed and let the owner decide. It should never claim that someone is monitoring the phone.

**Decision (Kyle, Q3):** intrusive or extractive use should trigger an immediate alert to the owner. Two limits apply. First, the alert is only as strong as what Android reports: a camera or mic event says a sensor was in use, not which app used it or whether the owner did. Second, the alert must name the reading, not a cause. Settings and network changes (new administrator, certificate, listener, VPN or proxy) are alerts too.

**Decision (Kyle, Q4, from his earlier answer):** background running is permission-based. It stays off unless the owner grants it, and the owner can revoke it.

## Scoring

The CC and SS framework (see the Drive report, Technical Report v3) can score consistency, but only after a reference set exists to set a threshold. Until then the app shows no pass or fail. Any score is a consistency measure, not evidence of monitoring.

## Limits the design keeps in view

- It cannot prove nobody is monitoring the device.
- It cannot name apps for camera or mic unless Android exposes that.
- It cannot read location use.
- It runs only while the monitor is running. Android may stop a background service, and the app must tell the owner when it stops.
- A rooted or compromised phone can hide or rewrite everything the app records.

## Ethics and scope

- The app is for the owner's own device, with the owner's consent.
- It does not read other people's data, messages, audio or video, and it records no audio, video or screen content.
- It does not collect anything about other people.

## Calibration (proposed, not decided)

A reading means little without a baseline for the same device. The framework's own rule is that τ comes from a reference run on the specific system, not from a guess. So a calibration phase is a reasonable next design step:

- The user starts calibration on a device they know to be clean, and the app records clock, timing and vitals over a set period.
- The mean and spread from that run give the device's own reference. A threshold can then be derived from it and checked on a later run.
- The baseline is sealed into the log, so any later change to it shows.

Limits to state in the app, not hide:

- A baseline records what the device looked like when it was taken. If the device was already compromised then, the baseline is wrong and the app will treat the wrong state as normal. Calibrate only on a device the owner has checked by other means.
- A timing or CPU change can come from many causes (sleep, a busy processor, a power setting). It is never proof of spying, so the app reports the change and does not name a cause.
- Claims that a timing spike reveals keylogging, or that the framework will "catch" a particular attack, are not supported. Nothing in this design has been tested against real attacks.

## Open questions

1. ~~Anchoring~~ Answered: no phoning home; export on request only (see above).
2. ~~Background running~~ Answered (Kyle, Q2): notifications are the user's choice. The app offers the options and does not decide for the user: (a) run in the background with a visible notification, or (b) run only while the app is open, with gaps recorded as gaps. Either way the app shows its current state.
3. **Alerts:** which changes should notify the owner immediately, and which should only be logged?
4. ~~Repository~~ Answered (Kyle, Q5): a new public repository, starting with this design document only.
5. ~~Name~~ Answered (Kyle): the app is called Sentinel Ledger. "Aegis-Shield" stays the shield project; SIMM stays the readings tool.
