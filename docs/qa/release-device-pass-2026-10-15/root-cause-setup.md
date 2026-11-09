# Python setup and the OpenCode 2 move offer

Candidate: `codex/release-p1-setup`, starting at `4d7288a59`.
Integration base: `ab472e705e2ee235601d027096621b993c0abbfe`
(`git merge-base master HEAD`). No push, signing, release, or physical-phone run.

## P1: the check was not the failed install

The preserved emulator's `files/linux/setup.json` identifies Python as failed
from `1792059729233` to `1792059729458`: **225 ms**. Its actual error is:

> [oc] Some packages are still unfinished. Retry setup to repair the interrupted installation.

The matching setup log names `libonig5:amd64`, with missing list and md5sums
control files. Its dpkg status was `install reinstreq half-installed`, version
`6.9.9-1build1`. The package log ends its unpack at 09:58:48 UTC. Later
`dpkg --configure -a` runs completed other packages but left this one behind.
See [recovered job and log](setup-recovered-evidence.txt).

The Python check uses `python3` directly. Exit 127 means it is not installed;
that is a normal negative inventory result and causes installation to be
scheduled. Python installation uses Ubuntu apt packages, not Node, an OpenCode
1 path, or a Python archive URL/hash. `libonig5` is a dependency of the AI Team's
`jq` package. The recovered data establishes interrupted package installation;
it does not establish what interrupted that unpack or the cause of the earlier
Node download failure.

The repair helper only tried dependency repair when `dpkg --configure -a`
failed. That command can succeed while an unpacked package needs reinstallation.
The subsequent nonempty `dpkg --audit` then stopped every retry immediately.

Both Python scripts and the repair prelude are byte-identical to the integration
base. Running that base's actual helper against the preserved emulator data
reproduced `CHECK_RC=127` and `REPAIR_RC=1`, with the same libonig audit. See
[base reproduction](setup-base-repro.txt). This defect predates phase 2; this
was a base-script reproduction on the preserved rootfs, not a complete base APK
installation test.

### Repair and failure display

For a nonempty audit, setup now asks dpkg for structured status and reinstalls
only validated package names marked `reinstreq`, at their recorded versions,
with `--no-remove`. It configures and re-audits before installing the requested
tool. Unknown damage, a failed query, an unavailable version, or a failed repair
still stops safely. No package removal or broad upgrade was added.

The actual package repair error now has plain English/Arabic copy. The progress
adapter preserves the original failure in its redacted Details log even if no
script output was captured, and avoids duplicate reasons. Generic installation
failures name the component. The existing Details fold remains scrollable below
the checklist; the screenshot's absence of a visible fold was not proof that
Android had lost the log.

### Emulator evidence and limits

Held `/home/owner/Storage/tmp/oc-emulator.lock` throughout. Started Pixel_6 as
PID `540139`, used only `adb -s emulator-5554`, and stopped that exact PID before
releasing the lock. Used APK 2324's existing data; no replacement APK installed.
Read the failed job with emulator adb root, then ran the extracted scripts
against its Ubuntu using its bundled PRoot. This tests the real package repair
and Python installation, not the updated Flutter UI or native installer gate.

The first repaired probe successfully reinstalled libonig at the same version
([excerpt](setup-reinstall-libonig.txt)), then encountered a **probe error**:
its `/data/user/0` rootfs alias did not match existing PRoot synthetic hardlinks
containing `/data/data`. Perl therefore appeared missing to that probe. Using
the production `/data/data` path restored Perl execution without an app change.
The final run passed `import ensurepip, venv`, `python3 -m pip --version`,
`python3 --version`, and a clean `dpkg --audit`; libonig reports
`install ok installed`. Python is 3.12.3, pip 24.0. See
[final check](setup-fixed-python.txt). No credentials or conversation contents
were read for this diagnosis.

## P2: offer did not consult the guard

The offer previously depended on runtime, busy state, and dismissal, while the
history safety check ran only after the screen promised a copy. Both existing
histories therefore produced an attractive offer followed by a correct refusal.

This phone now reads that same guard before showing the move offer and rechecks
on tap. Only a successful check admits the offer. Separate histories hide it;
the existing switch row explains that OpenCode 2 has its own conversations and
Older OpenCode keeps its conversations. Tapping gives the explanation and
**Stay on Older OpenCode**, without stopping a runtime or mutating a profile.
Unavailable/unsafe checks also hide the offer and explain that the check could
not finish. The execution-time guard is unchanged; no merge or bypass was added.

Added a Widgetbook state for separate histories and behavioral tests for blocked,
unavailable, pending, and changed-after-load checks, including runtime and
profile preservation. Synthetic shell checks cover older-only admission,
two-history refusal with unchanged bytes, and an unsafe symlink.

## Verification and handoff

- Native: **456 tests passed**, no failures/errors/skips, 29 classes. See
  [native result](setup-native-tests.txt). No Kotlin production files changed.
- Pinned Flutter analyzer: **No issues found**, after resolving this checkout's
  Widgetbook dependencies offline. The initial analyzer attempt encountered
  missing Widgetbook/analyzer package configuration; no dependency locks changed.
- Focused Dart test results: see [check record](setup-focused-tests.txt).
- UI capture runner: **2 tests passed**. These are real widgets with synthetic
  data, not device captures: [switch row](setup-separate-histories.png),
  [Stay sheet](setup-stay.png), [Python repair](setup-python-repair.png),
  [expanded Details](setup-python-details.png).
- This phone's 3 catalog use-cases rendered through the standard checker in its
  English, Arabic, large-text, and dark passes. Its existing setup poll mock emits
  timeout diagnostics; this filtered run does not claim the full standard ratchet.
- Catalog coverage remains blocked by **four existing uncatalogued screens**:
  `LiveStylePickerScreen`, `LiveStylePreviewScreen`, `WidgetConfigureScreen`, and
  `WidgetSteerScreen`. None was added or modified here. The new This phone state
  is catalogued; no baseline was widened.
- Full repository serial suite and updated APK/device UI pass: **not run**.
  The emulator proof is limited to the real package scripts described above.
- Accessibility: existing kit rows, alert action, scroll and Details controls
  are reused; no new control type or gesture was introduced.
- Security/data: history check is read-only, blocked actions never stop or
  switch runtimes, the original refusal guard stays in place, failure Details
  are redacted (including a synthetic credential regression). No stored-format
  migration, credentials, signing keys, or physical phone were involved.

ARB keys (English and Arabic):

- `phoneSetupErrorPackageRepair`
- `setupSwitchSeparateHistoryDetail`
- `setupSwitchHistoryUnavailableDetail`
- `setupSwitchStay`

The requested Gradle unit-test command invokes Flutter compilation, which
regenerated localization as a build side effect. Generated localization changes
are discarded from this branch; the maintainer owns final `gen-l10n`.
