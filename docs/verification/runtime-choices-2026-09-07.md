# Runtime choices and native proof boundary — 2026-09-07

Read-only discovery observed aarch64 and a traced Ubuntu guest. The process's
`TracerPid` was 4361; `ps -p 4361 -o pid=,ppid=,comm=` reported `proot`, and
`readlink /proc/4361/exe` resolved to Termux's proot executable. No full process
arguments or environment were printed. `opencode2` was not on this shell's PATH.
No runtime was installed, started, stopped, authenticated, or migrated.

The existing-server route is the supported alternative: enter an already
running OpenCode 1 or 2 server's address and let the existing authenticated
probe establish the connection. This route does not require Termux permissions.
The managed installer continues to offer pinned OpenCode 1 in Ubuntu and reuse
of an inspected existing managed installation.

Native/musl and managed OpenCode 2 installation remain unavailable pending the
isolated proof in [the native experiment](termux-native-server-spike-2026-09-06.md). No claim is made
that version output or package availability satisfies it.

### Prepared native preflight (not executed)

Run from a separately authorized, genuine Termux shell, never from this guest:

```bash
set -eu
printf 'architecture=%s\n' "$(uname -m)"
OC_PROOF_TRACER=$(awk '/^TracerPid:/ { print $2 }' /proc/$$/status)
[ "$OC_PROOF_TRACER" = 0 ] || {
  printf 'Blocked: the proof shell is traced; establish an untraced Termux launch.\n' >&2
  exit 78
}
[ "${PREFIX:-}" = /data/data/com.termux/files/usr ] || exit 78
[ "$(readlink /proc/$$/exe)" = "$PREFIX/bin/bash" ] || exit 78
```

The approved runtime experiment must pin the binary/loader/libraries and their
integrity before launch. Use a dedicated scratch HOME/XDG tree and project,
synthetic credentials, an unused loopback port (for example 4137 after checking
its listener), and a captured exact PID plus process-start identity. Cleanup
must signal only that owned PID after rechecking identity. Never kill by pattern.

Do not adapt the OpenCode 1 command to OpenCode 2 until its exact installed CLI
help and authentication contract have been verified. Do not read the phone's
account store or use a real provider turn to substitute for fixture proof.
Validate authenticated health, session create/list, SSE/reconnect, shell/Git/file
tools, DNS/CA/TLS, cancellation, clean restart, isolated update/rollback, and
byte-preserved synthetic session/project data. A paid provider run is a separate
explicit authorization. Record each result; unrun rows remain unverified.

## Implementation and verification

The setup screen exposes the existing-server route before Termux installation
or permission steps, including the desktop fallback. A failed installation
inspection is presented as unknown; the user must explicitly review the Ubuntu
choice before setup can continue. A successful inspection retains the existing
start-without-download flow. No profile storage format, installer version,
protocol, or native channel changes are required.

Focused fixture/capture verification passed in the integration lead's serial
checks. Cases cover missing Termux, denied permission, desktop large
text, failed-inspection cancel/continue, and the existing package-free restart.
Synthetic light/dark captures use `tool/capture/setup_progress_test.dart`.
The UI uses flat sections and a scrollable desktop fallback. Address navigation
does not issue a Termux command or store a credential. Runtime proofs above
remain unexecuted and are separate from these UI fixtures.
