# Pinned phone Paseo install evidence — 2026-10-03

The phone agent host uses Paseo **0.9.2**, matching the existing private
connection pilot. It does not install a second app server or enable relay
access. The native host owner supplies loopback launch and secret handling.

## Install contract

`PaseoPhoneScripts` provides `nodeInstall`, `nodeCheck`,
`install(packageLock: ...)`, and `check`. Both installers require the native
setup component's `agentUser: true` execution mode: UID 1000 and HOME
`/home/oc`. They refuse root before writing. The caller first prepares the
`oc` Linux identity. Merely invoking `runuser` inside PRoot's root identity
mode is not the required execution seam.

Node is selected only as a dependency of Paseo. Node **v24.21.0** is downloaded
from [the official release directory](https://nodejs.org/dist/v24.21.0/) and
verified against the existing app's ARM64/x64 SHA-256 pins before execution.
Node and Paseo are owned by `oc` below `/home/oc/.local`; no global npm install
or provider account setup occurs.

The committed `assets/agents/paseo-package-lock.json` freezes the complete
registry closure, including optional architecture packages, rather than
using `npm install @getpaseo/cli@0.9.2` with floating transitives. Its SHA-256 is
`82d16f9c432dcaacaf881045a7f4da83806775bdfdfe409efd4cf21377fe1fea`.
Every resolved package uses HTTPS `registry.npmjs.org` and a SHA-512 SRI digest;
the Dart factory rejects an altered lock before constructing a script.
The JSON is transported as gzip/base64 in the durable setup script, keeping
that script below Linux's per-argument limit; the decoded SHA-256 is checked
again before npm runs.
`npm ci --ignore-scripts` verifies those digests and disables all dependency
lifecycle hooks. Its environment contains no inherited provider credentials,
and fresh, separate empty user/global npm config files prevent account-config
inheritance. Raw npm output is private temporary data, deleted on completion
or failure; setup receives a fixed plain failure message.

## Native modules and source evidence

Primary published packages were read directly from the npm registry:

- [`@getpaseo/cli@0.9.2`](https://registry.npmjs.org/@getpaseo%2fcli/0.9.2)
  pins its first-party server/client/protocol to 0.9.2, but several other
  dependencies are ranges. Its command is `bin/paseo`.
- [`@getpaseo/server@0.9.2`](https://registry.npmjs.org/@getpaseo%2fserver/0.9.2)
  pins `node-pty` 1.2.0-beta.15 and `sherpa-onnx-node` 1.12.28. It also includes
  `@anthropic-ai/claude-agent-sdk` 0.3.246; that SDK pin is distinct from the
  app's explicitly installed Claude CLI pin.
- [`node-pty@1.2.0-beta.15`](https://registry.npmjs.org/node-pty/1.2.0-beta.15)
  published tarball contains `prebuilds/linux-arm64/pty.node` and
  `prebuilds/linux-x64/pty.node`. Its actual `lib/utils.js` searches those
  prebuilds directly; `scripts/post-install.js` only moves Windows ConPTY
  files. No source build or install hook is required on supported glibc Linux.
- [`sherpa-onnx-node@1.12.28`](https://registry.npmjs.org/sherpa-onnx-node/1.12.28)
  delegates to optional platform packages with floating ranges. The lock
  explicitly overrides those six platform packages to **1.12.28** to match
  the wrapper, rather than taking today's range-selected 1.13.8.

After download, the candidate must load `node-pty` and `sherpa-onnx-node`, run
an esbuild transform using its packaged executable, and return exactly
`0.9.2` from `paseo --version`. An unsupported native module leaves the old
install intact and returns “Paseo cannot run on this phone yet.” The launcher
is replaced only after successful candidate checks. Cancellation can discard
and rebuild the candidate; immutable artifact caching/Node downloads resume
through the setup prelude. Installation alone never asserts agent restoration,
authentication, daemon health, or ARM64 qualification.

## Verification scope

Temporary workstation-only `npm ci --ignore-scripts --no-audit --no-fund`
installed 285 selected packages. Native module loads, esbuild transform, and
`paseo --version` succeeded on x64 Linux with PC Node **v24.11.1**. Selected
`node_modules` used **493 MiB** (`du -sh`); this is an observation, not an ARM64
download or disk-size guarantee. No global software was installed, provider
credentials were used, daemon was started, or login was attempted.

This proves the published x64 closure can load without install scripts on
the PC. It does not prove the pinned Node version, Android PRoot, emulator,
ARM64, a real agent session, or restart restoration. The owner's in-app
device self-test remains the gate for those host runtime prerequisites.
The root integration runner executes the added shell/fake tests and analyzer.
