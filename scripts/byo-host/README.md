# Private BYO host bundle

Original stdlib Python supervisor and deterministic packaging for bundle **1.1.0**,
OpenCode **1.18.32**. This is the host half of the Ubuntu SSH adoption backend.
No infrastructure API credential is stored here. No service is published, no
upstream artifact is executed by these files, and no real host has been
provisioned by the offline tests. The release builder downloads pinned public
OpenCode archives; packaging and host installation use reviewed digests.

## Supported host and owner prerequisites

Ubuntu **24.04**, amd64/arm64, Python 3, OpenSSH, an existing **nonroot** login
account, and an available systemd user manager. A dedicated nonroot account is the
trust domain: OpenCode tools run as this account and can access its files. The installer does
not create users, install OS packages, change firewalls/sshd, run sudo, or prompt
for an administrator password. The SSH target must already be on the owner's Tailscale tailnet; the app refuses
public SSH addresses. It does not join a tailnet or change firewalls. It encrypts
the SSH forward and never opens public HTTP/agent ports. The SSH endpoint must
use a Tailscale address and ordinary OpenSSH authentication (Tailscale SSH account
authentication is a separate unsupported flow).

The existing sshd policy must permit only local TCP forwarding, deny Unix socket
forwarding and tunnels, and use `.ssh/authorized_keys`. An example **owner-managed**
configuration (replace `ocuser` with the actual dedicated account) is:

```text
# Global context, outside Match blocks:
UseDNS no

Match User ocuser
    AllowTcpForwarding local
    AllowStreamLocalForwarding no
    PermitTunnel no
    AuthorizedKeysFile .ssh/authorized_keys
Match all
```

An existing `PermitListen none` plus local TCP forwarding permission also works.
`DisableForwarding yes` does not work, because the app needs its one permitted
local forward. `restrict,port-forwarding,permitopen=...` in `authorized_keys`
alone is insufficient: it re-enables reverse forwarding and Unix socket access.
`permitlisten="none"` is **not** a valid authorized-key substitute on OpenSSH
9.6; see the [upstream option parser](https://github.com/openssh/openssh-portable/blob/V_9_6_P1/auth-options.c#L239)
and [sshd configuration manual](https://man.openbsd.org/sshd_config#PermitListen).

The owner validates their edited configuration with `sshd -t`, reloads their SSH
service under their normal administrative procedure, and retains a working rescue
SSH session until a new login succeeds. These are owner actions, never automated
by this bundle. Verify with the actual device key that reverse TCP forwarding,
Unix socket forwarding, command execution, PTY allocation, agent forwarding and
other TCP destinations are refused. The intended forward to
`127.0.0.1:4096` must still work.

The installer computes the policy via `sshd -T -C` using the actual
`SSH_CONNECTION` and current username. A temporary throwaway host key supplied
with `-h` lets the nonroot parser work without reading the real private host keys;
it never starts sshd and is immediately deleted. Missing context, unreadable
configuration, reverse/Unix forwarding, tunnels, or an unprovable Match Host
context (`UseDNS yes`) fails with `sshPolicyRequired` (exit 73) **before pairing**.
This checks configuration on disk; it cannot establish that the running daemon
was reloaded. The owner's negative-channel proof remains required.

The installer verifies lingering and returns `lingerRequired` when absent. The
owner enables lingering once by hand; the app never runs loginctl enable-linger
or sudo. A missing
user bus returns `userServiceUnavailable`; a root or unsupported OS account
returns `unsupportedHost`.

## Packaging and SSH install contract

The maintainer supplies the **already verified** extracted Linux OpenCode 1.18.32
ELF binary and its reviewed SHA-256. The script verifies that digest and ELF
architecture; release provenance/version must already have been verified by the
maintainer (it does not run a potentially foreign-architecture binary).

```sh
python3 scripts/byo-host/package.py \
  --opencode /absolute/path/to/verified/opencode \
  --opencode-sha256 REVIEWED_BINARY_SHA256 \
  --architecture amd64 \
  --output /absolute/path/to/oc-byo-host-1.1.0-amd64.tar.gz
```

Use `arm64` for that verified architecture. Output is JSON with the manifest,
`archiveSha256` and `manifestSha256`. The gzip/tar metadata is deterministic;
archive members are exactly `manifest.json`, `install.sh`, `supervisor.py` and
`opencode`. Manifest version 1 records bundle/OpenCode versions, architecture,
port **4096** and each executable file's digest. Publication and choosing an
artifact URL are separate owner-approved steps.

Over an independently host-key-pinned admin SSH connection, the app obtains the
archive from an approved HTTPS URL and verifies the **frozen archive digest before
extracting or executing any installer**. Then:

```text
sh /private/staging/install.sh --archive /private/staging/bundle.tar.gz --sha256 FROZEN_SHA256
stdin JSON: {"deviceId":"PROFILE_ID","token":"PHONE_GENERATED_TOKEN","publicKey":"ecdsa-sha2-nistp256 PUBLIC_KEY"}
```

No credential is supplied as an argument or embedded in a shell command. stdin
is bounded to 16KiB. Device IDs use `[A-Za-z0-9_-]{1,128}`; tokens must be generated
randomly by the phone with at least 256 bits of entropy, encoded base64url without
padding (`[A-Za-z0-9_-]{32,256}` accepted). Phone keys use Android Keystore ECDSA P-256; Ed25519 remains accepted for
existing host pairings. Comments are discarded. A device ID/key cannot replace another pairing. Repeating the exact
same live pairing is idempotent; revoked IDs cannot be resurrected.

The installer verifies archive bytes again, rejects traversal, links, duplicate
members, oversized files and digest/architecture/version mismatches. It uses an
exclusive install lock and immutable digest-addressed releases in
`~/.local/share/oc-byo-host/`. It installs/enables `oc-byo-host.service` under the
existing account's systemd user manager. Matching live installs are reused with
no restart; different unit contents, bundle digest, version or port fail with
`hostConflict`. Interrupted installs can be retried with the same pairing and
bundle. Upgrade/replacement is intentionally unavailable in this slice.

Success stdout (and CLI `pair`) is one JSON object:

```json
{"hostId":"STABLE_UUID","bundleVersion":"1.1.0","openCodeVersion":"1.18.32","port":4096,"deviceId":"PROFILE_ID"}
```

Failures return a fixed JSON `error` code and nonzero exit. Never display arbitrary
remote stderr/stdout as diagnostics. The supervisor's `info`, `pair` (JSON stdin)
and `serve` subcommands also accept explicit state/key/binary paths for trusted
administration; the app always uses the verified installer. Its `pair` subcommand
does not replace the installer's host/SSH-policy preflight.

## Runtime protocol and persistence

Supervisor listens on **127.0.0.1:4096 only**. It starts the exact packaged
OpenCode binary at a separate ephemeral loopback port with a fresh internal
password passed only in child environment. Both child output streams and user
unit logs are suppressed. Upstream health must succeed before requests are
served; no phone token is sent to OpenCode. Upstream passwords are not persisted.

Every request, including WebSocket upgrades, requires HTTP Basic with username
`deviceId` (= profile ID), password phone token, over the app's tailnet SSH forward.
The supervisor replaces Authorization with its own internal upstream credential.
Standard REST verbs and `/event` SSE stream transparently; bounded request bodies
use Content-Length, ambiguous/chunked request framing is refused. PTY WebSocket
`GET /pty/{id}/connect` is a verified RFC6455 handshake plus opaque bidirectional
relay; unknown/malformed upgrades are refused. Revocation closes REST/SSE/PTY
connections belonging to that device only.

| Endpoint | Result |
| --- | --- |
| `GET /_oc/host` | Authenticated descriptor: `hostId`, `bundleVersion`, `openCodeVersion`, `port` |
| `POST /_oc/revoke`, empty body or `{}` | Authenticated own-device receipt: `hostId`, `deviceId`, `revoked:true`; cannot specify another device |

Revoke first writes the tombstone, removes only the exact `oc-byo-DEVICE_ID`
authorized-key marker, flushes the receipt, then disconnects that device's proxy
sockets. Its token hash remains solely to authenticate repeated revoke requests;
every other route refuses that revoked token. **No OpenCode session is deleted,
no host process is stopped, and other phones continue working.** Existing SSH
transport connections cannot be killed by a user-space HTTP supervisor; the
revoked phone cannot authenticate new HTTP requests, and existing proxy sockets
close. The restricted key cannot obtain a shell or another destination.

`state.json` stores a stable host UUID and per-device SHA-256 token hashes/public
keys/tombstones, uses an interprocess lock, and is written by fsync + atomic replace
mode 0600. Tokens themselves never reach this file, authorized_keys, logs,
arguments, service units or package metadata. Agent access includes tool execution
as this Unix account. A stolen live phone token can therefore permit modifying
host code/data through agent tools; revoking its transport does not undo malicious
host changes, revoke separately acquired credentials or repair a compromised
account. The dedicated nonroot account is a shared trust domain, not a sandbox
between mutually hostile phones.

## Offline checks and remaining live proof

```sh
python3 -m unittest discover -s test/byo_host -p '*_test.py' -v
```

Tests use temporary directories, fake upstream HTTP/SSE/WebSocket and a fake
OpenCode executable child, plus stubbed systemd. They cover two-phone isolation,
idempotent revocation, preserved sessions/child PID, secret suppression, tamper and
archive safety, reproducible bundles, pairing conflicts, linger/SSH policy gates,
and live-install reuse without restart. They create no cloud resources and make
no internet requests. The real pinned OpenCode process, actual systemd/sshd
configuration, negative SSH-channel tests, app-to-host forwarding, and phone
kill/reconnect/second-phone/revoke journeys remain owner-run throwaway-host proof.


## Reviewed release bundle pipeline (Task B2)

`.github/workflows/byo-host-bundle.yml` runs only on `v*` tag pushes in
`Eslamasabry/opencode-mobile-next`, requires the tag to equal `pubspec.yaml`'s full
`x.y.z+N` app version and the tagged commit to equal current `master`, and uses
Ubuntu 24.04/Python 3.12. Its concurrency group matches `android-release.yml` so
both workflows arrange the same draft sequentially. It never publishes a release
or replaces an existing release asset. Its only write permission is repository
contents in the tag job; checkout does not retain the token in Git config.

The committed `release-pins.json` freezes the upstream OpenCode **1.18.32** Linux
amd64 baseline and arm64 archive digests. These came from the official
[release asset API](https://api.github.com/repos/anomalyco/opencode/releases/tags/v1.18.32)
on **2026-10-02**, release published **2026-09-21**. GitHub documents the asset
[`digest` field](https://docs.github.com/en/rest/releases/assets#get-a-release-asset).
They are reviewed source inputs, never discovered at workflow runtime from a
checksum sidecar. Changing a pin or host source requires a fresh bundle review.
The builder verifies archive SHA-256 before reading its one regular `opencode`
member, rejects other files/links and verifies ELF architecture before packaging.
It never executes the binary, including the foreign-architecture one.

Before any tag, the maintainer builds locally from reviewed source:

```sh
python3 scripts/byo-host/build_release.py build \
  --tag vAPP_VERSION --repository Eslamasabry/opencode-mobile-next \
  --output /tmp/reviewed-byo-host
```

This downloads public pinned archives only. To reuse already verified downloads,
add `--upstream-directory /absolute/cache` containing `amd64.tar.gz` and
`arm64.tar.gz`; the same frozen digest checks still run. Building twice compares
archive bytes. Packaging normalizes order, tar owner/mode/time and gzip metadata;
reproduction requires the same checked-out host scripts and Python/zlib toolchain.
Output contains the two host archives, `BYO_HOST_SHA256SUMS` and
`byo-host-manifest.json` with full app version, fixed bundle/OpenCode versions,
versioned release URLs and SHA-256 for upstream archives, binaries and host bundles.

The maintainer checks the outputs and freezes both bundle SHA-256 values under
`release-pins.json` → `appVersions` → exact full app version → `amd64`/`arm64`, and
in the app's compiled bundle manifest for that app version. Review and commit
those pins before tagging. The app must never adopt a downloaded manifest as
trust. Until this row exists, the workflow fails `bundleReviewRequired` before
release attachment. The workflow rebuilds and requires exact equality to that
row, catching unreviewed source changes. A draft-only release URL is not publicly
available until a separate owner-authorized publication; building this pipeline
is not publication.

The `attach` command is reserved for the authorized tag workflow. It distinguishes
an API 404 from authorization/network failure, creates missing releases with
`--draft --verify-tag`, refuses published releases, and rechecks draft state
immediately before each upload. Identical already-uploaded assets are skipped by
GitHub's recorded digest; an unknown/different digest is blocked, with no clobber.
Repository administrators must not publish the draft while this workflow is
running: GitHub has no atomic “upload only if still draft” API condition. Android
release notes/APK setup remain owned by `android-release.yml`. Publication and
any release/tag/CI action still require the owner's separate authorization.
