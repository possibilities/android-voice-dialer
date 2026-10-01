# Verification

Cloud build on 2026-10-01:

- `testDebugUnitTest`: 24 passed, zero failures
- `lintDebug`: passed, zero errors; warnings for intentionally fixed Termux paths, dependency freshness and English-only UI strings
- `assembleDebug`: passed, ARM64 debug APK
- APK signing and manifest inspection: checked with Android SDK tools

20 state-machine tests cover first durable thread creation, resume, no duplicate starts, hang-up during creation/offer/start, saving late creation responses, keeping threads after hang-up or missing-thread errors, uncertain creation recovery, rejecting ephemeral threads, wrong-thread events, asynchronous SDP, waiting for media before Connected, fail-closed approvals, and constant loopback endpoint, cancelled timeout generations, and socket failure during hang-up/offer/initialization.

Four real local WebSocket fixture tests verify the credential-free 401 probe, authenticated initialization, rejection of an unauthenticated listener without revealing a token, rejection of the wrong token, and no connection before pairing. These use synthetic test capabilities and never contact Codex or a phone.

Not verified on a phone or emulator: view layout/rotation, Android permission dialogs, Termux RUN_COMMAND interaction, foreground microphone/background behavior, actual acoustic quality, audio routing, account-authenticated realtime entitlement, end-to-end audio, provider reconnection, or Android/OEM process survival. The cloud has no accelerated Android device available. Unit tests are not substitutes for these checks.

No phone installation, microphone permission, Termux permission, phone auth change, new phone credential, LAN exposure, or upstream community runtime execution was performed by this build task.
