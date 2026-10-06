# Maintainer UI update 1.0.43+48

Source: e1ed89757206daa78c5272c079e375fe5a533aa9, pushed to mobile-next/calm-ui-1043.

Android quality workflow run 34747218141 completed successfully with apk_only=true. This is signed checkpoint delivery, not a completed stable quality gate or public release. Local focused UI/discovery/storage tests and the analyzer passed; full-source release validation is being completed on the subsequent 1.0.43+49 publication candidate.

APK SHA-256: ca40ef2d4191e7d3b7a722c68a1581a3264d754025fbd34caf6e7a03bc031e32

Signer SHA-256: 2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC

Package: io.github.eslamasabry.opencode_mobile; versionName 1.0.43; versionCode 48; size 201153840 bytes. Verified with Android build-tools 36.1.0 apksigner and aapt.

Installed with adb install -r on isolated emulator-5560 over maintainer-signed 1.0.42+47. The saved Stable test server profile reconnected to the isolated QA server; its saved project remained selected. Opening the existing New session restored the exact unsent draft, “Keep this draft after the calm UI upgrade.” No prompt/model invocation, credentials change, device data reset or cleanup was performed. Native screenshots are in docs/qa/calm-native-2026-09-13.

Download served on the existing tailnet-only HTTP service: http://100.101.102.103:8788/opencode-mobile-1.0.43+48.apk. Both this path and /app-release.apk returned HTTP200 and the exact APK content length. The prior artifact is retained under opencode-mobile-1.0.42+47.apk; the generic app-release.apk alias now points to the new artifact. Download availability depends on this development host and Tailscale.

Web integration source 1b3e4eb runs at http://100.101.102.103:8789/ with Flutter web-server --hot. One hot restart was needed for changed widget definitions; the browser then rendered the real welcome screen and navigated to the explicitly simulated demo. Browser profile, shell, home, library and editor focused checks: 74 passed; analyzer clean. This is a development preview, not a production web release or authenticated-server browser end-to-end proof.
