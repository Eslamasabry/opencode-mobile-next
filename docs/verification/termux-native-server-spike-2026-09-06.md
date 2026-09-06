# Termux-native server experiment — partial evidence, 2026-09-06

**Status: INCONCLUSIVE for proot-free use.** This lead review corrects the
earlier "spike passed" conclusion; it does not rerun the experiment. Preserve
the observations below, but do not use them to replace the supported setup.
Related [F7](../backlog/innovation-2026-09-06.md) and
[execution guardrails](../backlog/roadmap-2026-09-06.md).

## What the session actually recorded

The commands ran in an aarch64 Ubuntu guest under Termux proot. Launching
Termux's bionic bash as its subprocess does **not** escape proot's path/syscall
translation. A successful run on the physical phone is not necessarily a
successful run without the Ubuntu guest layer.

| Observation in the session | What it supports | What it does not establish |
|---|---|---|
| npm listed glibc and musl arm64 packages for `1.18.25` | Upstream distributes prebuilt executables | Android/Bionic compatibility or a supported install method |
| Rounded archive sizes were 58M/60M; executable sizes 176M/185M | Approximate sizes of those files | Complete installed footprint, bandwidth saving or setup duration |
| Both ELF files declared an interpreter | They are not dependency-free static executables | That a musl label removes all runtime/tool dependencies |
| musl loader plus musl-built C++/GCC libraries enabled version output | That combination can launch in the guest environment | A production distribution/license/update plan |
| Explicit loader invocation failed; a patched interpreter copy printed version | One workaround advanced the experiment | The root cause of the loader error or safe self-upgrade of the patched binary |
| A later listener reported HTTP 401 | A listener/auth rejection existed | Successful authentication, valid health payload, SSE or tool execution |
| Earlier `ServeError` was followed by success on another port | A port-specific or other environment issue is plausible | Confirmed port-conflict diagnosis; that was an inference |

Ubuntu's loader package was installed in the guest; supporting libraries were
extracted from Alpine packages. Compressed package sizes and installed library
sizes were mixed in the earlier summary. The claimed "~2 MB support", one-minute
setup, and swap-one-file upgrade were not complete measurements. The presence
of an `upgrade` CLI command does not prove it preserves a patched interpreter,
custom library path, stored sessions or rollback.

No authenticated end-to-end test or clean non-proot invocation was recorded.
The experiment used `1.18.25`; the later `1.18.29` installation/pin change is a
separate event and is not coverage of this experiment.

## Next experiment — approval and isolation first

1. Obtain approval for an isolated device/runtime test. Start from a genuine
   Termux shell outside the guest layer; record a sanitized launch chain and
   runtime environment sufficient to establish that proot is not involved.
   Do not print full process arguments/environments or modify the live server.
2. Pin a server binary, loader and libraries; record source/version, integrity,
   licenses, extracted sizes and patching steps. Use a separate scratch
   directory and retain the working installation unchanged.
3. Start only a test server on an unused loopback port with synthetic credentials
   kept out of command arguments/logs. Capture its exact PID/ownership token.
   Validate both unauthenticated rejection **and authenticated success** against
   a known route, with status and schema checks rather than an empty curl body.
4. Exercise session create/list, SSE bytes and reconnect, shell/Git/file tools,
   DNS/CA/TLS, cancellation and clean shutdown. A real provider turn requires
   separate credential/cost approval; fake fixtures can cover earlier gates.
5. Check background behavior, update+rollback, restart and data preservation;
   verify a replacement binary does not undo the interpreter patch. Compare
   actual download/storage/setup measurements against the current setup.
6. Stop only the captured test PID; inspect return codes without pipelines that
   hide the executable's failure. No pattern kills, no production restart,
   no global loader/Node/npm modifications as part of cleanup.

## Decision

Continue as a feasibility investigation. If the clean Termux run needs a
fragile compatibility stack or loses required tools, retain proot. Only an
authenticated, useful, maintainable non-proot workflow can justify a migration
story. No app code or supported deployment should change from this record alone.
