# A local terminal on the phone: emulator proof, 2026-09-24/25

Branch `feat/local-terminal` (worktree `agent-a7b33ab6377d3437a`), spec
[docs/design/local-terminal-2026-09-24.md](../../design/local-terminal-2026-09-24.md).

## Scope

- **Local PTY session (Kotlin):** `android/.../LocalTerminal.kt` starts `bash -l`
  in the built-in Ubuntu through `BuiltinLinux.prootCommand` on a real PTY from
  Termux's Apache 2.0 `terminal-emulator` library (`com.termux.terminal.PtyAccess`
  over `JNI.createSubprocess`). Resize, write, output as one event stream with
  an `ack` per chunk (32 KB chunks, 256 KB held before the shell itself waits),
  exit code, and stop of the whole process tree (`BuiltinLinux.stopPidTree`).
  Sessions belong to a singleton manager, not to a screen; the last 512 KB of
  output is kept for a Dart side that starts over.
- **Channel, both halves one owner:** `io.github.eslamasabry.opencode_mobile/local_terminal`
  (methods `start list attach write ack resize stop remove processCount`) and
  `…/local_terminal/events` (`output`, `exit`). Dart side:
  `lib/builtin/local_terminal.dart` (`LocalTerminalSessions`, `LocalShell`).
- **Display:** option A, the `xterm` Dart view fed by the local PTY
  (decision below), with two xterm 4.0.0 faults fixed in the shell's own
  `Terminal` subclass (wide characters at the last column, selection after
  `clear`).
- **Key bar:** `lib/ui/kit/terminal_key_bar.dart` (ours, not Termux's GPL
  `ExtraKeysView`): Esc, Tab, sticky Ctrl, sticky Alt, arrows, Home/End,
  PgUp/PgDn, `/ - | ~`; two rows, one scrolling row on a short screen.
- **Entry and states:** `TerminalPage` offers "This phone" / "OpenCode server"
  (This phone is the default once the built-in Ubuntu is installed);
  `lib/ui/screens/local_terminal_screen.dart` uses the design kit
  (`KitLoadingBar` while the shell starts, `KitStateView` for Linux not set up
  with Set up, the shell ended with Restart, a failed start with Try again and
  Details; `KitStatusLine` for the cost of a shell while the AI Team runs; the
  session list, New shell, Paste, Stop and Close in the top bar's menu).
  Entries: Project › Terminal, the desktop shortcut, and the "This phone"
  card's ⋯ › Terminal, which stays available while the server starts or
  stops (`9d70650a`).
- **Licences:** `NOTICE` and `THIRD_PARTY_NOTICES.md` (shown in About › Open
  source) name the Apache 2.0 `terminal-emulator` module; nothing from the
  GPLv3 `termux-app` or `termux-shared` is used.

## Builds

| Pass | Commit | APK | SHA-256 |
|---|---|---|---|
| Pass 1 (found two bugs) | `9d70650a` | `app-release.apk`, x86_64 only, 56.3 MB | `5c818d508747569d5cb528f976b2615e7b21b2aef113e2786cca47f0c8d461f2` |
| **Final pass** | **`0fd36b9f`** | `app-release.apk`, x86_64 only, 56.3 MB | **`92d309ec0c21ff12b5efb28e17101ce2ea4d0b2df0a9dfaa1b5d92e9c7158514`** |
| A/B measurements (earlier session, 2026-09-24) | A: `feat/local-terminal` between `9c769d05` and `5cc94b6b` (exact commit not recorded); B: `bae4c1fd` on `spike/local-terminal-option-b` | not kept | not recorded |

Built with the pinned Flutter 3.47.1
(`flock /home/owner/Storage/tmp/oc-build.lock $F build apk --release --target-platform android-x64`),
signed with the local release key. Commits after `0fd36b9f` change only this
record and `tool/qa/local_terminal/`.

## Devices

| | emulator-5554 |
|---|---|
| AVD | `Pixel_6`, 1080×2400, 420 dpi |
| Android | 14 (API 34), `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug` |
| RAM | 2 GB (`MemTotal 2021100 kB`), `-gpu swiftshader_indirect` (software rendering) |
| Phantom settings | `settings_enable_monitor_phantom_procs` = null (default: on, limit 32) |
| Notable | Fresh app data (the AVD's data partition is temporary); no Termux. The built-in Ubuntu was installed through the app's own setup: Set up at 00:46:43, ready at 00:52:44 (6 min). `vim` and `htop` added in the terminal with `apt-get install -y vim htop` (16 s). |

## The display decision: A (xterm Dart view), with numbers

Both were measured on the same emulator on 2026-09-24 (`ab/`), same workload
`time seq 1 200000` in a shell started by `LocalTerminal.kt`:

| | A: `xterm` Dart view (32 KB chunks) | B: Termux `terminal-view` as a platform view (`bae4c1fd`) |
|---|---|---|
| `seq 1 200000`, shell's own `real` | **2.3 s** (`ab/A-32k-seq200k.png`) | **28.8 s** (`ab/B-seq200k-done.png`) |
| SurfaceFlinger frames while drawing | A-32k on `seq 1 2000000`: 121 frames, gap p50 83.6 ms, p90 129.6 ms (`ab/A-32k-seq2m-sf.txt`) | 127 frames, gap p50 85.1 ms, p90 119.3 ms (`ab/B-seq200k-sf.txt`) |
| `dumpsys gfxinfo` | 0 frames: Flutter draws into its own surface, which gfxinfo does not count | 345 frames, 98.3 % janky, p50 101 ms (`ab/B-seq200k-gfxinfo.txt`) |
| Ctrl-C during a flood | stops at once (`ab/A-32k-flood-ctrl-c.png`) | not measured |
| Chunk size check | 256 KB chunks: `seq 1 2000000` 13.4 s but 36 frames, gap p50 319 ms (`ab/A-256k-seq2m-sf.txt`); 32 KB: 16.6 s, 121 frames. 32 KB kept: the screen keeps moving | – |

On the final build (`0fd36b9f`, no second emulator running) A did better
again: `seq 1 200000` 0.61 s, 27 frames, gap p50 36 ms, p90 66 ms
(`04-seq200k-sf.txt`); `seq 1 2000000` flooding: 127 frames, gap p50 34.8 ms,
p90 51.5 ms, max 349 ms, and Ctrl-C from the key bar stopped it at 4.1 s
(`04-seq2m-ctrl-c.png`, `04-seq2m-flood-sf.txt`).

**Decision: A.** It is 12× faster on the same flood, frames are as frequent,
IME, key bar, selection and copy work (checks 2, 5, 7), and it draws inside
the app's own look with no platform view. B stays on the unmerged spike
branch. A's correctness faults found on the device were in the `xterm`
package, not the design, and are fixed in the app (bugs 1 and 2 below).

## Runs (final build `0fd36b9f`, emulator-5554)

| # | Check (spec Proof) | Expected | Actual | Result |
|---|---|---|---|---|
| 0 | Project › Terminal after setup | Opens on "This phone" (the default with Ubuntu installed), a prompt at once | `root@localhost:~#`, This phone selected (`00-terminal-opens-on-this-phone.png`) | PASS |
| 1 | `ls --color /`, `echo $TERM`, `tput cols`; rotate | Colours; `xterm-256color`; cols follow the width | Coloured listing, `xterm-256color`, 51 cols × 19 lines portrait; 109 cols landscape; 51 again back in portrait (`01-ls-term-cols-portrait.png`, `01-cols-landscape.png`, `01-cols-portrait-again.png`) | PASS |
| 2 | `vim hello.txt`, type, `:wq` | Opens, insert works, saves | `-- INSERT --` with the text, Esc from the key bar, `:wq`, `cat` prints it (`02-vim-typing.png`, `02-vim-wq.png`, `02-vim-saved.png`) | PASS |
| 3 | `htop -d 5` (and `top`), `q` | Redraws, `q` quits back to the prompt | htop draws meters and processes and redraws: 12 frames in 10 s, gap p50 582 ms (htop's own 0.5 s period under proot; `03-htop-10s-sf.txt`); `q` restores the shell screen (`03-htop.png`, `03-htop-quit.png`). `top` in pass 1 (`pass1-9d70650a/c03-top.png`) | PASS |
| 4 | `seq 1 200000` and a flood, frame measurement before/after | Stays responsive | See the decision: 0.61 s, 27 frames p50 36 ms; flood 127 frames p50 34.8 ms, Ctrl-C at once. `gfxinfo` before/after both 0 frames (`04-gfxinfo-before.txt`, `04-gfxinfo-after.txt`): it does not see Flutter's surface, so SurfaceFlinger latency is the measure. A noisy real job: `apt-get install vim htop` scrolled 16 s of output (`pass1-9d70650a/c04-apt-install-vim-htop.png`) | PASS |
| 5 | Tab completion, arrows, Ctrl-C on `sleep 100`, Ctrl-R | All four work | `ls /us⇥` → `/usr/`, `lo⇥` → `local/`; ↑↑ recalls `echo first`; Ctrl then c stops `sleep 100` (`^C`); Ctrl then r, `hello` finds `cat hello.txt` and runs it (`05-*.png`) | PASS |
| 6 | A long line and CJK/emoji | Wrap at the edge, nothing cut or shifted | Long line wraps at 51; 32 CJK characters: 25 on the first line (last column left blank), 7 on the next; emoji rows likewise (`06-long-line-cjk-emoji.png`, `06-cjk-emoji-full-resolution.png`). **Failed in pass 1** (bug 1) | PASS (after fix) |
| 7 | Select and copy | Long press selects, Copy, the text pastes back | After `seq 1 20000` and `clear` (the pass-1 failure case): long press highlights `selectme`, Copy appears, ⋯ › Paste puts `selectme` into `echo pasted: `, which prints it (`07-selected.png`, `07-menu.png`, `07-pasted-ran.png`). **Failed in pass 1** (bug 2) | PASS (after fix) |
| 8 | OpenCode server stopped | The local terminal still works | Card › Stop → "Stopped", header "Reconnecting to This phone…" (`08-server-stopped.png`); ⋯ › Terminal (`08-card-menu-terminal.png`) opens a shell: `curl 127.0.0.1:4097` → `server-not-answering`, `uname -a`, Ubuntu 24.04.5 (`08-terminal-with-server-stopped.png`). Project › Terminal also opened while "Connection lost" (`11-ended-reached-while-offline.png`). "OpenCode server" in the same page says "Could not load terminal processes" with Try again (`08-server-source-while-stopped.png`) | PASS |
| 9 | Leave the screen and come back | Same session, scrollback kept | A `tick` loop started, back to Project and the Work tab for 12 s, back to Terminal: ticks continued while away (4 → 30), scrolling up shows `MARK-before-leaving` and the earlier commands (`09-before-leaving.png`, `09-away-on-work-tab.png`, `09-back-again.png`, `09-back-scrolled-up.png`) | PASS |
| 10 | Processes of the app's uid with one shell open | proot + bash = 2 per shell | Server running: 5 (app, proot + `opencode serve`, proot + `bash -l`) (`10-procs-one-shell-server-running.txt`); server stopped: 3 (app, proot, bash) (`10-procs-one-shell-server-stopped.txt`); after Restart: 3 (`10-procs-after-restart.txt`) | PASS |
| 11 | Shell ended: state and Restart | Output kept, "The shell ended" with Restart | `exit` → output stays, KitStateView "The shell ended · It exited with code 0.", Restart opens a new shell (`11-shell-ended-restart.png`, `11-after-restart.png`) | PASS |
| 12 | Stop this shell kills the tree | Confirm, then nothing of the shell is left | `sleep 500 & sleep 600 & bash -c 'sleep 700'`: 6 processes; ⋯ › Stop this shell › Stop: 1 process (the app) (`12-procs-shell-with-children.txt`, `12-stop-confirm.png`, `12-stopped.png`, `12-procs-after-stop.txt`) | PASS |

## Product bugs found and fixed

1. **CJK/emoji at the last column (`cafbaa1f`).** xterm 4.0.0 wrote a
   double-width character that met the last column half past the edge and began
   the next line with its empty second half (`pass1-9d70650a/c06-long-line-cjk-emoji.png`:
   26 characters on a 51-column line). Fixed in the local shell's `Terminal`
   subclass. Test `a wide character at the last column wraps whole`; without
   the fix: `Expected: a string starting with '中文😀'  Actual: '文😀'`.
2. **Select and copy after `clear` (`0fd36b9f`).** xterm 4.0.0 trims the
   scrollback without renumbering the lines that stay, so after `clear` a
   selection landed that many lines lower: no highlight, and Copy copied an
   empty string (`pass1-9d70650a/c07-selected.png`; a fresh shell worked,
   `pass1-9d70650a/c07-fresh-shell-selection-works.png`). Fixed by taking the
   visible lines out and putting them back. Test `after clear, a selection
   copies the words it covers`; without the fix: `Expected: 'pick'  Actual: ''`.
3. **Terminal unreachable while the server starts (`9d70650a`).** The phone
   card's whole ⋯ menu was disabled while the server started or stopped, so a
   start that never answered left no way into a shell. The menu now offers
   Terminal alone then. Test `Terminal stays in the menu while a server start
   does not answer`; without the fix: `Expected: exactly one matching candidate
   Actual: _KeyWidgetFinder:<Found 0 widgets with key [<'phone-server-terminal'>]: []>`.

The server's own terminal (`TerminalScreen`, "OpenCode server") uses the same
`xterm` package and still has bugs 1 and 2; it was not changed here.

## Tests

`flutter test -j 3` on `test/local_terminal_test.dart` (the session manager's
Dart side, output, input, size, adoption, the two xterm fixes, the channel
shapes), `test/local_terminal_screen_test.dart` (states, keys, shells, leaving
and coming back, the source choice, the compact layout, off Android),
`test/terminal_key_bar_test.dart` (sticky Ctrl, Ctrl plus a letter → the
control byte, arrows in both cursor modes, 44×48 targets),
`test/phone_server_card_test.dart`, and the other files that touch the
terminal page or the phone card (`design_standard_test`,
`design_standard_setup_test`, `desktop_shortcuts_test`, `l10n_coverage_test`,
`library_refresh_test`, `product_ui_regression_test`, `project_hub_test`,
`terminal_accessibility_test`, `terminal_input_queue_test`,
`goldens/phone_setup_golden_test`): 14 files, 192 tests, all pass at
`0fd36b9f`. `flutter analyze lib test`: no issues. The full suite was not run.

## Evidence

Everything above is in this folder: `NN-*.png` (screenshots, half size,
64 colours), `*-sf.txt` (SurfaceFlinger frame latency of the app's surfaces),
`*-gfxinfo*.txt`, `10-*`/`12-*` process lists. `pass1-9d70650a/` holds the first
pass, including the two failures. `ab/` holds the A-vs-B measurements from
2026-09-24; `00-setup-*-session1.png` the earlier session's setup.

## How to reproduce

```bash
F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
flock /home/owner/Storage/tmp/oc-build.lock $F build apk --release --target-platform android-x64
TMPDIR=/tmp/emu-tmp ~/Android/Sdk/emulator/emulator -avd Pixel_6 -no-window -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
adb -s emulator-5554 install -r build/app/outputs/flutter-apk/app-release.apk
# In the app: On this phone › Set up (about 6 minutes), Create a project,
# Project › Terminal. In the terminal: apt-get install -y vim htop
bash tool/qa/local_terminal/proof.sh emulator-5554 1 2 3 4 5 6 7
python3 tool/qa/local_terminal/term.py emulator-5554 procs:10-procs   # one shell open
# Checks 8, 9, 11, 12 are taps in the app (card › Stop, ⋯ › Terminal; Back and
# return; exit and Restart; ⋯ › Stop this shell), as in the Runs table.
```

`term.py` types with `adb shell input text`, taps screen pixels, saves
screenshots, SurfaceFlinger latency (`dumpsys SurfaceFlinger --latency`,
cleared first with `--latency-clear`), gfxinfo and the app's process list.

## NOT proven

- **A real phone.** Every run is on an x86_64 emulator with software
  rendering; arm64, a real GPU and a real keyboard (Gboard) were not used.
  Frame numbers on a phone will differ.
- **The cost line with the AI Team on.** Shown by widget tests only
  (`AI Team on: the line says what a shell costs`); the AI Team was not
  installed on this emulator. The process count per shell (2) is measured.
- **The "Linux isn't set up" state and a failed start on a device.** Widget
  tests only.
- **Landscape with the keyboard up.** The screen goes compact (no source
  choice, one scrolling key row), but on this emulator's tall keyboard only one
  terminal line is left (`01-cols-landscape.png`). Not tuned further.
- **A Dart side that starts over** (the app's engine restarted while Android
  keeps the shells): `attach` and `load` are unit-tested; not forced on the
  device. A killed app process ends its shells (they are its children).
- **Long sessions and Android's phantom-process killer** with several shells
  and the AI Team together.
- **Mouse, hardware keyboard and IME composition** (Japanese/Chinese input).
- **htop's CPU column** reads `N/A` under proot (its `/proc` view), a proot
  limitation outside this work.
