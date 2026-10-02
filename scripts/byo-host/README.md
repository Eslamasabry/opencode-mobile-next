# Private BYO host bundle

Original stdlib Python supervisor and deterministic packaging for bundle **1.0.0**,
OpenCode **1.18.32**. This is the host half of the Ubuntu SSH adoption backend.
No infrastructure API credential is stored here. No service is published, no
artifact is downloaded by these files, and no real host has been provisioned by
the offline tests.

## Supported host and owner prerequisites

Ubuntu **24.04**, amd64/arm64, Python 3, OpenSSH, an existing **nonroot** login
account, and an available systemd user manager. A dedicated nonroot account is the
trust domain: OpenCode tools run as this account and can access its files. The installer does
not create users, install OS packages, change firewalls/sshd, run sudo, or prompt
for an administrator password. Prefer already-tailnet SSH; the owner must ensure
the SSH endpoint follows their access policy. The runner accepts an explicit
`user@host` and does not enforce a tailnet-hostname restriction, join a tailnet or
change firewalls. It encrypts the SSH forward and never opens public HTTP/agent
ports.

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

The installer verifies lingering. If absent, it attempts only the noninteractive
`loginctl --no-ask-password enable-linger USER`; if policy denies it, it returns
`lingerRequired`. The owner can enable lingering separately, then retry. A missing
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
  --output /absolute/path/to/oc-byo-host-1.0.0-amd64.tar.gz
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
stdin JSON: {"deviceId":"PROFILE_ID","token":"PHONE_GENERATED_TOKEN","publicKey":"ssh-ed25519 PUBLIC_KEY"}
```

No credential is supplied as an argument or embedded in a shell command. stdin
is bounded to 16KiB. Device IDs use `[A-Za-z0-9_-]{1,128}`; tokens must be generated
randomly by the phone with at least 256 bits of entropy, encoded base64url without
padding (`[A-Za-z0-9_-]{32,256}` accepted). Keys must be Ed25519; comments are
discarded. A device ID/key cannot replace another pairing. Repeating the exact
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
{"hostId":"STABLE_UUID","bundleVersion":"1.0.0","openCodeVersion":"1.18.32","port":4096,"deviceId":"PROFILE_ID"}
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
