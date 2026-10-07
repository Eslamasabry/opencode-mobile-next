# BC1 — interrupted dpkg recovery on the dev emulator

Date: 2026-10-07. Device: `emulator-5554`, x86_64, installed app Ubuntu and PRoot.
Baseline revision: `dc67923c0a91402cc9f30ce071447892f783d26e`.
Production shell helper: `lib/builtin/setup/setup_scripts.dart`'s literal
`setupPrelude`, extracted directly without changing the source or installing an APK.

Finish line: an interrupted real dpkg configuration recovers through the setup
helper, requested packages report `install ok installed`, and a failed repair
lists `dpkg --audit`. Non-goal: other BC items, UI, live apt repositories or the
shared app's package database.

Run:

```sh
python3 tool/qa/bc1_dpkg_emulator.py \
  --baseline-ref dc67923c0a91402cc9f30ce071447892f783d26e
```

The harness uses only the installed app's executable PRoot/Ubuntu programs. It
creates two tiny local packages in a disposable `/data/local/tmp/oc-bc1-<uuid>`
fixture and binds it into Ubuntu. All dpkg invocations use that fixture's
`--admindir`, `--instdir`, and log, with `--force-script-chrootless` for its tiny
post-install script. That script writes its exact PID and waits. The harness
terminates only that PID while dpkg is configuring the package, then waits for
the parent dpkg to finish. It makes no app installation, uninstallation,
credential access or network request. Every ADB command uses the shared emulator
flock; the complete PRoot proof holds it for about one second.

The package depends on a second local package that is initially unavailable.
`--force-depends` is used only to enter the interrupted post-install step. This
leaves a real `half-configured` package that `dpkg --configure -a` alone cannot
heal. An offline apt shim represents the missing dependency becoming available:
only `-f install` installs that local dependency using real dpkg and retries
configuration. The shim does not exercise apt's repository resolver or network
transport; it isolates the setup helper's repair ordering, package verification,
and diagnostics from the shared environment.

Results in [emulator.log](emulator.log):

- Real interruption: dpkg reports that the post-install process was terminated;
  `dpkg -s` reports `install ok half-configured`, and audit names the package.
- Baseline helper: exits 100, leaves the package half configured, and never
  attempts dependency repair.
- Fixed helper: announces "Repairing interrupted package installation", calls
  `apt-get -f install`, verifies the requested package with `dpkg -s`, exits 0,
  and leaves both packages installed with an empty audit.
- A fresh interruption with dependency repair unavailable: exits 100, prints
  `Details: dpkg --audit` with the package name, and says to retry setup.
- Shared rootfs `var/lib/dpkg/status` SHA-256 is identical before and after the
  proof. The disposable fixture is removed in `finally`.

The log records SHA-256 hashes for both extracted shell preludes. Python syntax
compilation and scoped `git diff --check` also passed. No Flutter tests, APK
build, physical-device qualification, signing, deployment or release was run by
this harness.
