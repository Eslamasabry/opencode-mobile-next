# Termux terminal engine and external I/O

The emulator and view are Termux `v0.118.3`, the Flutter application's pinned release.
Upstream commit: `5b657c6adf4304e5198951ce815fe0205dcac29c`.

`src/main/java/com/termux/terminal/TerminalSession.java` comes from
[that exact upstream file](https://github.com/termux/termux-app/blob/5b657c6adf4304e5198951ce815fe0205dcac29c/terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java).
The unmodified source SHA-256 is
`0fc1edbb2897cfd2e00d2a2e5abfdbd3e65df472c38d6169a2beab516c5c0347`.
The repository's pinned license exception is preserved in `UPSTREAM-LICENSE.md`;
the Apache 2.0 license is in `LICENSE-APACHE-2.0.txt`.

The local PTY constructor and its upstream process, queue, JNI and lifecycle code
remain intact. The fork adds an explicit `ExternalIo` constructor and branches for
external write, resize and close, plus `appendExternalOutput`. A remote session
uses the same `TerminalEmulator` and `TerminalView` but creates no PTY, child,
file descriptor or I/O worker. IME, hardware keys, paste and emulator replies use
the same external write hook. No reflection bypass or substitute local process is
used for remote rendering. The existing upstream local FD wrapper is unchanged.

The build depends on the hash-pinned upstream `terminal-emulator:v0.118.3` AAR from JitPack.
The `TermuxSessionFork` artifact transform in `build-logic` (applied by every Android convention
plugin) checks it against its pin in `third_party/SHA256SUMS` and removes only
`TerminalSession.class` and its inner classes from `classes.jar`; all other classes and every
ABI's JNI bytes are retained, and the rewritten ZIPs use sorted, stored, fixed-time entries. The
native source fork supplies the removed classes. The terminal-view dependency explicitly excludes
its original emulator dependency, so the app receives one `TerminalSession` definition. No derived
AAR is stored or fetched: every checkout produces it from committed files and the pinned upstream.
