# slice-script-pins — pinned OpenCode in the host script, Tailscale from apt (2026-09-28)

Source: the "Found outside `lib/`, not changed (follow-ups)" list in
[`../slice-close-security-2026-09-28/README.md`](../slice-close-security-2026-09-28/README.md).

## Finish line and non-goal

Finish line: `scripts/host/ubuntu-opencode.sh` never pipes an installer into a
shell. It installs one pinned OpenCode release after a SHA-256 check and fails
closed on a mismatch. The guides install Tailscale from its signed apt
repository. A test keeps both true for `lib/`, `docs/*.md`, `scripts/**` and
`tool/host/**`.

Non-goal: moving the app's pin (`HostScripts.commit`). That needs a published
commit, and a push needs the owner's approval.

## What changed

### 1. `scripts/host/ubuntu-opencode.sh`: pinned OpenCode release

Before: `install` ran `curl -fsSL https://opencode.ai/install | bash` when
OpenCode was missing, and `update` ran `opencode upgrade`. Neither was pinned
or checked.

After:

- `install` (only when no `opencode` is found, as before) downloads the
  release archive for this CPU from OpenCode's official GitHub releases:
  `https://github.com/anomalyco/opencode/releases/download/v1.18.32/<asset>`.
  It saves the archive to a `mktemp -d` directory, compares
  `sha256sum` with the checksum recorded in the script, and unpacks only after
  that check passes. It then installs `opencode` to `~/.opencode/bin` (the
  place OpenCode's own installer used, which `find_opencode` already searches),
  replacing any old copy with a rename. Finally it checks that
  `opencode --version` prints `1.18.32`.
- On a mismatch it fails closed with a plain message: "The OpenCode download
  does not match its published checksum, so nothing was installed." The
  message names the file, the expected checksum and the actual one. Nothing is
  unpacked or installed, the service is not touched, and the temporary
  directory is removed by an `EXIT` trap.
- An unsupported CPU, or a missing `curl`, `tar` or `sha256sum`, stops the
  script with a plain message that says what to do.
- `update` now moves OpenCode to the pinned release, with the same check. It
  never downgrades: if the installed OpenCode already reports the pinned
  version or a newer one, it is left alone. A newer OpenCode may have migrated
  its data forward. `update` then points the unit at the binary it chose
  (`write_unit` takes an optional path) and restarts. The service, flags,
  environment variables, bind rules, password handling and idempotency are
  unchanged. The usage text is the same except for the `update` line.

Versions and checksums used:

| Host CPU (`uname -m`) | Asset | SHA-256 |
|---|---|---|
| `x86_64` / `amd64` | `opencode-linux-x64-baseline.tar.gz` (60 608 354 bytes) | `763af386ef88a8cab18df00fcf055690e5a55e31a7088beabe02307142a6adce` |
| `aarch64` / `arm64` | `opencode-linux-arm64.tar.gz` (60 418 875 bytes) | `568461b7d4d8c19865c97e9a1102e613049c6039d01fe772154de873c1865840` |

- Version: OpenCode 1 **1.18.32**, the version the app pins
  (`TermuxRuntime.openCode1.pinnedVersion` in `lib/termux/bridge.dart`;
  upgrade record `docs/verification/dependency-upgrades-2026-09-27.md` §5).
  The release is tag `v1.18.32` of `anomalyco/opencode`, published
  2026-09-21T22:51:20Z, target `f5ce4f881e477c7b75421cea2d20939f0ddd71fb`.
  A test ties the script's `OPENCODE_VERSION` to the app's pin.
- The script runs `opencode serve`, so it installs OpenCode 1. OpenCode 2
  (`@opencode/cli` 2.0.10) is not installed by this script, and nothing was
  pinned for it here.
- The x64 build is the `baseline` one (runs without AVX2), the same choice
  the app's own Ubuntu setup makes (`opencode-linux-x64-baseline`). Ubuntu
  is glibc, so the `-musl` assets are not used.
- Where the checksums come from: the release publishes no separate checksum
  file (no `SHA256SUMS` or `*.sha256` asset). GitHub publishes a SHA-256
  `digest` for every release asset. I read those digests with
  `gh api repos/anomalyco/opencode/releases/tags/v1.18.32` on 2026-09-28. I
  also downloaded both archives from the URLs above and computed
  `sha256sum` myself. Both computed values matched GitHub's digests. Each
  archive holds exactly one file, `opencode`, and the extracted x64 binary
  printed `1.18.32` for `--version`. The downloads were deleted afterwards.

Local end-to-end run (x86_64, 2026-09-28). A fake `curl` served the
downloaded official archive and a fake `systemctl` stood in for systemd, with
`HOME` and `TMPDIR` in a scratch directory. `install` checked the checksum,
installed 1.18.32 to `~/.opencode/bin/opencode`, wrote the unit with that
`ExecStart` and exited 0. With a tampered archive, `install` exited 1 with the
mismatch message and left no binary and nothing in `TMPDIR`.

### 2. The app's pin: kept, with a pending update recorded

The app downloads the host script from `HostScripts.commit`
(`c62f159ae3c1741cb4ec0ef92b4941c0ddfc0a18`, release 1.0.44) and checks sha256
`1f42642f…d721`. Those bytes are published and unchanged, so the app's
command stays correct. It still installs OpenCode through
`opencode.ai/install`, and it keeps doing so until the pin moves. The pin
cannot move until a commit holding the new script is pushed. Pushing needs the
owner's approval, and this slice did not push.

`test/host_script_pin_test.dart` now splits "the pin is right" from "the
repository is right":

- **`<name>: the checksum matches the pinned commit's bytes`** hashes
  `git cat-file blob <pin>:<path>`, not the working tree. It passes as long as
  what people actually download matches `HostScripts.<name>.sha256`. As
  before, it is skipped only when git lacks the commit (a shallow clone).
- **`<name>: the checkout holds the pinned bytes or a recorded update`**
  passes if the working-tree file hashes to the pinned checksum, or if it
  hashes to the entry in `_pendingPinUpdate`. The second case is the
  "pin update pending push" note: the constant at the top of the test,
  written to be easy to find. Another edit to the script without updating
  that entry fails the test. Once the pin moves, a leftover entry also fails
  the test ("remove its _pendingPinUpdate entry").
- **`the repository script installs a pinned, checked OpenCode`** checks the
  working-tree script. It must have no piped installer, no
  `opencode.ai/install` and no `opencode upgrade`. Its `OPENCODE_VERSION` must
  equal the app's OpenCode 1 pin, the source must be the GitHub release URL,
  there must be two 64-hex checksums, and the checksum comparison must come
  before `tar -xzf`.
- `HostScripts`' doc comment in `lib/ui/setup_commands.dart` points at the
  pending entry. The app's code and copy are unchanged.

### Exact pin update after the owner approves a push

1. Push a commit that contains `scripts/host/ubuntu-opencode.sh` with SHA-256
   **`7c0f335cf7667e5b00d48ffb71bed1d2593c842d47823e76721ba5e7976b3c45`**
   (the file as committed here). Check it:
   `git show <commit>:scripts/host/ubuntu-opencode.sh | sha256sum`.
2. In `lib/ui/setup_commands.dart`:
   - `HostScripts.commit` = that full 40-character hash.
   - `HostScripts.release` = the release that commit belongs to. If it is not
     a release, reword the "Downloads the script from release …" line that
     uses it.
   - `HostScripts.ubuntu.sha256` = `7c0f335cf7667e5b00d48ffb71bed1d2593c842d47823e76721ba5e7976b3c45`.
   - `HostScripts.front.sha256` stays
     `b672254944c3d77cb18f85b2337773e330f82d29ed64690d57e4fdc26235cd47`,
     because `tool/host/cp_front/front.py` did not change. Its URL moves with
     the commit.
3. In `docs/ubuntu-host.md` and `docs/ai-team-host.md` §5, replace the pinned
   `curl -fsSLo … && echo '<sha>  …' | sha256sum -c - && mv …` blocks with the
   new `verifiedDownload` text (new commit in the URL, new sha for the Ubuntu
   script). The guide test fails until they match. In `docs/ubuntu-host.md`,
   drop the two "release 1.0.44 script" sentences.
4. In `test/host_script_pin_test.dart`, empty `_pendingPinUpdate`.
5. Run `test/host_script_pin_test.dart`, `test/host_script_install_test.dart`
   and `test/host_management_screen_test.dart`, plus the goldens that show
   the command, if any. The command text changes, so review its golden.

If the script changes again before the push, update the sha in
`_pendingPinUpdate` and use that value in step 2 instead.

### 3. Tailscale from the signed apt repository

Before, `docs/ai-team-host.md` §1a said
`curl -fsSL https://tailscale.com/install.sh | sh && sudo tailscale up`, and
§1d (WSL) said the same.

After, §1a gives Tailscale's published apt-repository steps for Ubuntu 24.04:

```sh
sudo mkdir -p --mode=0755 /usr/share/keyrings
curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/noble.noarmor.gpg | sudo tee /usr/share/keyrings/tailscale-archive-keyring.gpg >/dev/null
curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/noble.tailscale-keyring.list | sudo tee /etc/apt/sources.list.d/tailscale.list
sudo apt-get update && sudo apt-get install tailscale
sudo tailscale up
```

It also says how to substitute another release (`ubuntu/jammy`,
`debian/bookworm`, …). §1d now points at §1a's steps. These commands fetch a
keyring and a source list to files, not a program to run. The `.list` file
pins `signed-by=` to that keyring, so `apt` rejects any package it did not
sign.

Sources, checked 2026-09-28:

- <https://pkgs.tailscale.com/stable/#linux>, the "Ubuntu 24.04 (Noble
  Numbat)" block: these exact five commands.
- <https://tailscale.com/docs/install/linux> (Install Tailscale on Linux,
  last validated 2026-01-05). It says: "If you prefer not to use `curl | sh`,
  visit the Tailscale Packages - stable track page for manual installation
  instructions".
- HTTP 200 for `…/ubuntu/{noble,jammy}.{noarmor.gpg,tailscale-keyring.list}`
  and `…/debian/{bookworm,trixie}.…`. The noble `.list` reads
  `deb [signed-by=/usr/share/keyrings/tailscale-archive-keyring.gpg] https://pkgs.tailscale.com/stable/ubuntu noble main`.

No app UI or `app_en.arb` string shows a Tailscale install command, so no copy
changed.

### 4. The no-piped-download sweep covers the guides and scripts

`nothing the app or its guides show pipes a download into a shell` (renamed
from "nothing in the app …") now scans:

- `lib/**/*.dart` (minus generated localizations) and `app_en.arb`, as before
- `docs/*.md` (the top-level guides)
- `scripts/**`
- `tool/host/**`

It skips `__pycache__`. The regex is unchanged:
`(curl|wget) … | [sudo] (ba|z|da)?sh`. `curl … | sudo tee <file>` does not
match, and it should not, because that writes data to a file.
`docs/audits/*` and `docs/qa/*` quote `curl | bash` as history, and they are
not scanned.

### Also updated

`docs/ubuntu-host.md` now describes what each script version does: the
pinned 1.0.44 script still runs OpenCode's own installer, and the repository
script installs 1.18.32 after a checksum check. It says `update` moves to the
pinned release and never downgrades.

## Tests

New file `test/host_script_install_test.dart` (Linux only). It runs the real
script under `bash` with a fake `curl` that serves a small generated archive,
and a fake `systemctl`. It uses no network and no real services.

- a mismatched download installs nothing, prints the mismatch message and the
  actual checksum, requests exactly the pinned GitHub URL for this CPU,
  leaves `TMPDIR` empty, and never enables the service
- a matching download (in a copy of the script whose recorded checksums are
  the fake archive's) is installed to `~/.opencode/bin` and becomes the
  unit's `ExecStart`
- `install` leaves an existing OpenCode alone and does not download
- `update` moves an older OpenCode (1.0.0) to the pinned release and repoints
  the unit
- `update` never downgrades a newer OpenCode (99.0.0) and does not download
- `update` with a mismatched download exits 1, keeps the old binary and does
  not restart

Changed file `test/host_script_pin_test.dart`: see §2 and §4.

Verified by reverting: with the pre-slice script and guide restored,
`host_script_pin_test.dart` failed 3 tests (pending entry, repository script,
sweep: `docs/ai-team-host.md`, `scripts/host/ubuntu-opencode.sh`), and
`host_script_install_test.dart` failed too (all three tests in the captured
output failed: the old script has no checksum constants and pipes the
installer). With this slice, all pass.

Commands run in `/home/owner/Storage/Code/oc_app-slice-script-pins` through
`tool/qa/machine_lock.sh`, pinned Flutter 3.47.1:

- `flutter test --no-pub --concurrency=1 test/host_script_pin_test.dart test/host_script_install_test.dart`:
  **19 passed**.
- `flutter test --no-pub -j 3 test/first_run_computer_path_test.dart test/host_management_screen_test.dart test/revamp/screen_servers_2_test.dart test/slice_close_servers_profile_editor_test.dart test/team_host_guide_sheet_test.dart test/termux_setup_v2_words_test.dart test/ui_glossary_test.dart test/kit_ratchet_test.dart`
  (every existing test that reads the changed files, plus the kit ratchet):
  **113 passed**.
- `flutter analyze --no-pub` on the whole project: **no issues**.

No UI changed, so there are no before/after images.

## Still needs a real host / owner

- An install on a real Ubuntu machine with systemd and network, on both x86_64
  and aarch64. The download here came from the real URLs, but `install` ran
  with a fake `curl` and `systemctl`.
- The Tailscale steps on a clean Ubuntu 24.04 (they are taken verbatim from
  Tailscale's page, not run here).
- Push approval, then the pin update above. Until then, people who follow the
  app or the guide still get the 1.0.44 script with its piped OpenCode
  installer.
