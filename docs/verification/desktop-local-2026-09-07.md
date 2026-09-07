# E8 local desktop readiness — 2026-09-07

Finish line: package an existing Linux x64 or ARM64 bundle with its actual ELF
architecture, reject mixed native libraries and mismatched versions, and retain
installation/reproducibility behavior. Non-goal: call desktop runtime verified
without a matching host and actual run evidence.

## Source change

The Linux tar name previously always said `x64`, while `.deb` architecture came
from the packaging host. Copying an ARM64 bundle to an x64 host (or the reverse)
could label it incorrectly. Packaging now reads the runner's ELF header, checks
all bundled `.so` and versioned `.so` files, and uses the resulting architecture
for both artifact names and Debian metadata. The default bundle directory follows
the host; `--bundle` remains available for copied bundles. `--build` runs in the
repository directory even when invoked elsewhere.

No app behavior, credentials, profile data, migrations, or accessibility flow
changes. No signing, CI invocation, release, or runtime-support promotion.

## Host preflight

The worker's execution host is Ubuntu 24.04.4 ARM64 in the phone's
`containers/ubuntu/rootfs` proot container, confirmed by `/proc/self/root`.
It is **different from** `containers/opencode-ubuntu/rootfs`, where the shared
repository lives. Build prerequisites were installed in the `ubuntu` container;
this does not install them in `opencode-ubuntu`. Both containers can access the
repository by its full phone path.
The local Flutter 3.47.2 cache has Linux ARM64 release engine artifacts. The
locked `sherpa_onnx_linux` 1.13.7 plugin explicitly supplies aarch64 native
libraries. Initial PATH resolved Android Termux `clang`/`pkg-config`, and Ubuntu
CMake/Ninja/GTK development packages were absent. Those Android tools must not
be used for the Linux runner.

The required Ubuntu packages are installed only for local build feasibility;
this is not evidence that the app built or launched. The available Flutter is
upstream, not the repository-required Shorebird fork. Windows needs a Windows
host with the Visual Studio desktop toolchain. No such host is available here.
There is no `macos/` runner in this repository and no macOS/Xcode host here.

## Root-scheduled checks

The coordinator ran the following checks: shell syntax passed, the six Python
packaging cases passed, and the ten Flutter packaging-contract cases passed.
These checks exercise packaging, not application runtime:

```sh
bash -n scripts/package-linux.sh
python3 -m unittest discover -s test -p linux_packaging_test.py -v
flutter test --concurrency=1 test/desktop_packaging_contract_test.dart
```

The Python fixtures exercise real tar/deb packaging with inert ELF headers for
both architectures, mixed-library rejection, invalid runner rejection, version
mismatch rejection, and deterministic tar output. They never execute the fixture
as an app, install a package, or establish runtime correctness.

For a local ARM64 compile attempt, use Ubuntu tools and a temporary Ninja wrapper
that executes `/usr/bin/ninja -j1 "$@"`. Place the wrapper in a private temporary directory and prepend that directory to PATH. Flutter invokes Ninja directly, so
`CMAKE_BUILD_PARALLEL_LEVEL` alone does not bound that invocation. Prepend that
wrapper directory and Flutter to `/usr/bin:/bin`; do not change global PATH or
compiler alternatives. Then root may schedule:

Run inside the `ubuntu` container, with the full repository path as the working
directory. Do not nest `proot-distro login` from an existing proot session or
point another container's host PATH at these binaries directly.

```sh
env PATH=/path/to/ninja-wrapper:/path/to/flutter/bin:/usr/bin:/bin \
  /path/to/flutter/bin/flutter build linux --release --target-platform linux-arm64
scripts/package-linux.sh --bundle build/linux/arm64/release/bundle
```

Physical desktop install, keyring-backed credential persistence, connect/resume,
keyboard/file-drop flows, and multi-monitor behavior remain unverified until
performed on suitable hosts. A successful phone-hosted compile is separate from
those runtime results. Existing Windows experimental distribution remains gated.
