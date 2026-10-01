# Source compatibility review · 2026-10-01

Inspected source, not a device-validation claim. No upstream Termux binary or install script was executed during this review.

## Runtime choice

[DioNanos/codex-termux](https://github.com/DioNanos/codex-termux) / `@mmmbuto/codex-cli-termux`, release [v0.156.1-termux.1](https://github.com/DioNanos/codex-termux/releases/tag/v0.156.1-termux.1), Android 10+ ARM64. The README's removal of TUI realtime/cpal device support does **not** remove the app-server realtime implementation. Android WebRTC owns the microphone and speaker here.

- [V1/V3 WebRTC selection and V2 rejection](https://github.com/DioNanos/codex-termux/blob/v0.156.1-termux.1/codex-rs/core/src/realtime_conversation.rs#L1268-L1379)
- [ChatGPT versus API-key auth headers](https://github.com/DioNanos/codex-termux/blob/v0.156.1-termux.1/codex-rs/core/src/client.rs#L442-L461)
- [Call creation with Codex authentication](https://github.com/DioNanos/codex-termux/blob/v0.156.1-termux.1/codex-rs/core/src/client.rs#L655-L688)
- [WebSocket media API-key requirement](https://github.com/DioNanos/codex-termux/blob/v0.156.1-termux.1/codex-rs/core/src/realtime_conversation.rs#L1773-L1796)
- [Origin rejection and loopback listener policy](https://github.com/DioNanos/codex-termux/blob/v0.156.1-termux.1/codex-rs/app-server-transport/src/transport/websocket.rs#L89-L141)
- [Optional app-server capability auth](https://github.com/DioNanos/codex-termux/blob/v0.156.1-termux.1/codex-rs/app-server-transport/src/transport/auth.rs#L27-L60)
- [App-server SDP mock integration tests](https://github.com/DioNanos/codex-termux/blob/v0.156.1-termux.1/codex-rs/app-server/tests/suite/v2/realtime_conversation.rs#L2054-L2148)

## Android and Termux

- [RUN_COMMAND requirements, result callbacks, and security implications](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Package visibility declaration](https://developer.android.com/training/package-visibility/declaring)
- [Microphone foreground-service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)
- [Official Termux installation notes and Android process limits](https://github.com/termux/termux-app#installation)

Only `com.termux` is queried. No QUERY_ALL_PACKAGES, storage, accessibility, root, notification-listener, or boot permission is requested. RUN_COMMAND is broad and requires user approval. It is required for initial capability pairing in this build; install and later server restart commands can be run manually. The app never changes Termux settings without the user running the shown configuration command.

## Build dependencies

- Android Gradle Plugin 8.12.0, Gradle 8.13, Java 17
- [WebRTC SDK Android 150.7871.01](https://central.sonatype.com/artifact/io.github.webrtc-sdk/android/150.7871.01), published on Maven Central by webrtc-sdk (third-party prebuilt WebRTC distribution)
- OkHttp 4.12.0 and Gson 2.11.0, from Maven Central
- JUnit 4.13.2 for protocol state-machine tests

No runtime dependency is downloaded by the Android app itself. Gradle downloads build dependencies, and the phone owner chooses whether to install the separately reviewed Termux port.
