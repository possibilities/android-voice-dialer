# Real-device acceptance checklist

Use a disposable test workspace first. Installation and each permission belong to the phone owner. The APK is a development build.

1. Android 10+ ARM64: install APK after reviewing it. Confirm first screen, setup scrolling, large-font layout and system-bar insets.
2. With Termux absent, confirm missing-app detection and official F-Droid button. Cancel the install flow; return safely.
3. With Termux present and Codex absent, authorize the optional integration only if desired. Confirm Check installed Codex reports missing. Deny permission and ensure copy/paste still works.
4. Review/install the pinned community runtime and log into Codex in Termux. Explicitly approve Pair & start, verify the background token-write succeeds, and confirm the foreground app-server requires capability-token auth. Deny pairing and ensure Call refuses. Confirm no token appears in app logs, URL, command arguments or clipboard. Confirm no LAN listener or public tunnel.
5. Tap Call and deny mic permission. No audio or call should begin. Retry and grant; optional notification denial should not prevent an explicitly started call.
6. Establish real ChatGPT-auth WebRTC audio. Verify bidirectional speech, interruption, mute/unmute, speaker/earpiece and audio-focus loss. Confirm runtime entitlement errors are visible, never a fake Connected state.
7. Ask a distinctive safe question. Hang up, call again, and confirm both same thread ID and preserved context in a full Codex client.
8. Rotate, background, lock and reopen during a call. Confirm call controls and foreground notification remain available. Verify Hang up immediately removes microphone activity.
9. Kill/restart the Android app and Termux separately. No microphone restarts automatically; a deliberate new call uses the saved thread. Check OEM battery restrictions honestly.
10. Hang up at each startup stage; rapidly tap call/hang-up. No orphan capture, surprise call, duplicate thread, or late callback revives old media.
11. Stop/delete the backend thread and call again: visible failure, no silent replacement. Test interrupted first-thread creation and explicit ID recovery.
12. Trigger a harmless approval request in a test workspace. This client must never auto-approve. Open a full Codex client to handle work that needs an approval UI.

Local pairing requires explicit user approval and protects server access with a bearer token. It does not provide mutual server authentication against a malicious app pre-binding the port; do not treat this as protection for a compromised phone. Keep Termux verbose/debug command logging off, because Termux is the trusted recipient of pairing stdin.
