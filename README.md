# Local Voice for Android

A small native Android phone-style dialer for **Codex running on the same phone in Termux**. One durable conversation, across calls and app restarts. Cream, ink and green UI, matching the [web dialer](https://github.com/possibilities/voice-dialer).

## Status

**Experimental, build-tested. Real-device and account-backed voice verification are still required.**

- Android 10+ (API 29), ARM64 APK
- Native WebRTC microphone/playback, call notification, mute and speaker/earpiece controls
- Fixed local control endpoint `ws://127.0.0.1:8765`; no remote server selector, tunnel, or LAN bind
- First call starts a persistent Codex thread; later calls resume its stored ID
- Hang-up stops audio/realtime only; it never archives, deletes, forks or interrupts a Codex work turn
- Android can kill either process. A new call resumes the saved thread after restarting the local server. Nothing promises a process that “runs forever”
- Package visibility detects Termux. Codex is checked only through user-authorized Termux command access, or by connecting to the server. No cross-app filesystem probing
- Installation and login stay in Termux. No silent APK/package installation, no copied OpenAI credentials

“Local” describes the Codex server selection and control connection. **Voice media and inference still use OpenAI over the internet.**

## Build

Java 17, Android SDK 36 and Gradle 8.13:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (ARM64, debug-signed). The [Actions workflow](https://github.com/possibilities/android-voice-dialer/actions/workflows/android.yml) builds and uploads the APK and reports. A debug APK is for testing; this is not a Play Store release.

## Phone setup

1. Install [Termux from F-Droid](https://f-droid.org/en/packages/com.termux/). Keep its add-ons from the same source; don't mix signing sources.
2. Review [DioNanos/codex-termux](https://github.com/DioNanos/codex-termux), the community Android ARM64 port. The source compatibility review uses [v0.156.1-termux.1](https://github.com/DioNanos/codex-termux/releases/tag/v0.156.1-termux.1). This app does not install or run that package for you.
3. After deciding to trust the community package, use the reviewable install commands in Setup. Sign in using `codex login` in Termux. The Android app does not read the login file.
4. In Setup, choose **Pair this app with Termux**. After the explicit persistent-access confirmation, the app creates an encrypted local capability, provisions its private Termux token file using a short background command, then requests a foreground server session. This is separate from your OpenAI login. It requires the optional RUN_COMMAND permission described below. No pairing or credential is created during the build.
5. To restart the already-paired server manually in Termux:

   ```sh
   codex -c 'realtime.type="conversational"' app-server --listen ws://127.0.0.1:8765 --ws-auth capability-token --ws-token-file /data/data/com.termux/files/home/.localvoice/control.token
   ```

6. Open Local Voice and tap **Call Codex**. Android asks for microphone access. Notification permission is recommended so the active call and Hang up action remain visible.

Setup includes buttons to open Termux, review installation, copy commands, check Codex and start the existing runtime. Automated check/start is optional: it requires `com.termux.permission.RUN_COMMAND` and `allow-external-apps=true`. The app explains the broad permission before requesting it. Manual copy/paste remains available for installing Codex and restarting an already-paired server. Initial pairing currently requires RUN_COMMAND; it does not offer an unauthenticated fallback.

### Local-control security

Calls refuse to connect before pairing. The control token is encrypted with an Android Keystore AES-GCM key, excluded from backups, sent to Termux through its explicit command-service stdin, and used as a WebSocket Authorization header. The server requires `--ws-auth capability-token`. Before sending the credential, each call checks that an unauthenticated WebSocket upgrade is rejected with 401; an old unauthenticated listener is refused. The token is not an OpenAI credential and this app never puts it in URLs, command-line arguments, the clipboard or its logs. Termux is a trusted recipient; its own debug/verbose logging may expose command input, so keep such logging disabled.

A bearer token authenticates this client to the server. It does not provide mutual server authentication or protect against a hostile app that pre-binds port 8765 and impersonates the server. Only pair with a trusted Termux installation and trusted apps on the device; stop stale listeners and check the server session before calling. Do not claim this prototype is hardened for a compromised phone. No LAN listener or public tunnel is configured.

## Durable conversation behavior

The thread ID is synchronously saved in app-private preferences before starting voice. Backend history remains in Termux's Codex home. Backups/device transfers of the pointer are disabled to avoid silently resuming the wrong installation.

If first-thread creation loses its response, the app refuses to create another thread blindly. Recover the existing ID from a full Codex client using **Recover existing thread ID**. A missing/deleted saved thread is an explicit error, never an automatic new conversation. Clearing app data or uninstalling removes the pointer; it does not delete Termux history.

The microphone runs in a user-started foreground service. Rotation/backgrounding do not intentionally end the call. Process death stops audio, and `START_NOT_STICKY` prevents surprise microphone restarts. OEM battery restrictions and Termux process killing can still disconnect calls.

## Protocol and compatibility

Control uses Codex's experimental app-server WebSocket transport without an Origin header. The native client initializes with experimental API opt-in, probes `thread/realtime/listVoices`, starts/resumes the durable thread, offers WebRTC and applies `thread/realtime/sdp`. The UI says Connected only after both server startup and ICE/media connection.

WebRTC defaults to V1 when no version is supplied in the reviewed fork. ChatGPT auth is implemented in its call-creation/sideband code. Account access to that backend is not guaranteed. Unsupported APIs and backend errors remain visible. We deliberately do not use raw PCM/WebSocket media, which requires API-key auth in that runtime.

This is a voice client, not a full tool-approval UI. Incoming tool/approval requests fail closed with an unsupported-client error, and the user sees a notice. Speech such as “yes” is never treated as arbitrary tool approval.

See [compatibility evidence](docs/compatibility.md), [verification](docs/verification.md), and [phone acceptance checklist](docs/phone-acceptance.md).
