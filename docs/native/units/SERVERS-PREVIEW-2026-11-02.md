# Native preview server journey — 2026-11-02

Finish line: a person can add an OpenCode 2 server in the separate native preview, explicitly approve remote HTTP, save and connect, then start a real conversation and open its chat. The scope excludes production Flutter adoption and migration, QR pairing, pin management, projects, and alternative backends.

## UI and navigation

- Conversations exposes a localized Servers action; its callback retains the existing door API and opens server management. The two affected screen-test label expectations now use Servers.
- New `:feature:servers` owns `ChatsRoute.New` and `ServersRoute.Home`. The app coordinator adds the module and provides the runtime `ServerConnections` binding.
- Server name, address, username, password and explicit HTTP permission use existing kit parts. No new hand-built controls or kit components were required.
- Saved servers expose Connect, Start conversation and a confirmed Remove server action. A failed operation stays on the screen with plain English or Arabic copy; server error bodies and exception text are never displayed.
- New-conversation submission saves, connects and creates in that order. It navigates only to the exact `ConversationRef` returned by the runtime. Concurrent taps are ignored while an operation is admitted; failures do not trigger a retry.
- HTTP permission is passed only when the explicitly confirmed address equals the submitted address. Editing the address resets the displayed choice, and the ViewModel compares it again at submission.
- The password stays in an unsaved `TextFieldState` and a transient command argument. It is never put in `ServersUi`, saved-instance state, logs, or a data-class `toString`. A successful store clears the field; a failed store leaves it available for correction. `KitField(Secret)` requires the root `LocalKitSecretSink` to register keystrokes with the app redactor.
- Copy is in the new `values/strings_servers_preview.xml` and `values-ar/strings_servers_preview.xml`; the frozen converted `strings_servers.xml` resources remain unchanged.

## Contract

Uses `io.github.eslamasabry.oc.engine.connection.ServerConnections` and `SavedServer`. The runtime owns persistence, trust validation, credential storage, connection lifecycle, deletion and transport. The UI never imports a protocol client.

## Validation

Seven focused ViewModel regressions cover default/explicit HTTP permission, consent invalidation after address changes, failed secure save, successful exact-reference navigation, failed connection, duplicate taps during connection and failed creation without retry. `ServersRoute.Home` is included in the route-saving roundtrip test.

The coordinator ran all seven ViewModel tests and the route roundtrip successfully, compiled both consumers and assembled the native preview. The Android 15 emulator completed real authenticated save/connect/create, send/stream, force-stop restart, same-package APK update and confirmed removal. The stored password was absent in plaintext and its encrypted entry count changed from one to zero on removal. Screenshots and exact qualification state are in the combined report. The root screen also now supplies its dialogs with one shared kit host; two debug-assertion regressions were observed red and then green. This remains a preview, not native parity or physical-device/release qualification.
