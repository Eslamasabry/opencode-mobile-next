# BA storage admission for phone-agent payloads

Finish line: the real phone-agent install handoff carries enough authored space
metadata for its download and extracted payload to coexist, while retaining the
existing floor and shared dependency policy. This changes admission, not agent
qualification, consent bytes, account state or removal behavior. Device filling,
new downloads and new APK/device qualification are outside this change.

## Metadata and admission

`SetupComponent` adds nullable `installedBytes` and a preserving
`withInstalledBytes(int?)` clone. `downloadBytes` remains the download estimate;
it must not be inflated to encode an extracted payload or workspace reserve.

`requiredSetupFreeBytes(downloadBytes, {int? installedBytes})` keeps the existing
legacy requirement: at least 300,000,000 bytes and twice the nonnegative download
size. A positive known installed size additionally requires the nonnegative
download size plus that installed size plus 64 MiB (67,108,864 bytes). The larger
requirement wins. Additions and doubling saturate at the native signed-long
ceiling, 9,223,372,036,854,775,807; overflow must not lower admission. Null or
nonpositive installed size retains the legacy policy.

`BuiltinPhoneAgents._prepareEngine()` independently selects the maximum known
`downloadBytes` and maximum known positive `installedBytes` across the recipe's
supported architectures, and copies both only into `agent-{id}`. Combining
maxima from different architectures is deliberate: restoration must not depend
on another asynchronous ABI probe, and the selected admission must cover every
authored known-size artifact. This is a bound over catalog metadata, not a claim
that an unknown artifact's actual payload size was measured. If no installed
size is known, that field remains null.

Shared Node, Paseo and bootstrap components retain their own metadata and
requirements. Download pins, transfer/consent display sizes and installation
scripts remain unchanged. Native setup continues checking the handed-off
`data.requiredFreeBytes` before each component's work; failure retains the
existing plain storage guidance and way forward.

## Pin and size sources

The reviewed recipes in [the authored catalog](../../lib/domain/agent_catalog.dart)
pin release, architecture, HTTPS artifact and SHA-256. This change consumes their
existing sizes; it neither changes pins nor infers capability proof from them.

| Agent and pinned release | Maximum download bytes | Maximum payload bytes | Admission bytes |
| --- | ---: | ---: | ---: |
| Codex 0.160.0 | 109,304,578 | 289,101,384 | 465,514,826 |
| Goose 1.53.0 | 94,681,336 | 298,665,904 | 460,456,104 |
| Oh My Pi 18.5.1 | 280,536,544 | 280,536,544 | 628,181,952 |
| fx 0.0.12 | 5,482,652 | 12,520,232 | 300,000,000 |

Codex's two coexisting authored files total 398,405,962 bytes before the
reserve. Goose's conservative cross-architecture sum is 393,347,240 bytes:
its arm64 archive is slightly larger, while its x64 executable is larger.
Both exceed the former 300 MB admission. Oh My Pi's executable extraction copies
the downloaded artifact into the payload, so the two full files coexist; the
additional reserve raises its old 561,073,088-byte requirement to 628,181,952.
fx's known payload and reserve remain below the unchanged 300 MB floor.

The prior APK 2196 device runs measured allocated installed files separately:
[Codex's installed-inventory record](../qa/FQ-install-2026-10-08/codex-device.json)
reports 289,153,024 bytes and
[Goose's record](../qa/FQ-install-2026-10-08/goose-device.json) reports 298,717,184
bytes. Those observations include filesystem allocation and small install
metadata; they are not direct measurements of transient peak usage. The peak
figures above are arithmetic from the pinned download/payload sizes and the
production script's simultaneous artifact/extraction lifetime. The 64 MiB
reserve is the admission policy's allowance for metadata and working space,
not a separately measured guarantee for arbitrary packages.

## Verification boundary

The host handoff regressions inspect actual `startSetup` specs for Codex, Goose,
Oh My Pi and fx, including unchanged requirements for shared dependencies.
Codex and Goose must fail against the old 300 MB handoff before this fix. Shared
policy and component-cloning tests separately cover null metadata, exact
thresholds, overflow and preserving download metadata. Focused checks run through
the pinned toolchain and shared machine lock; the root lead records results.
This contract makes no new APK, device low-storage or full-suite claim.
