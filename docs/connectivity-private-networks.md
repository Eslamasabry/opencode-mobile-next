# Private-network connectivity — secure-path decision

Status: **design, not implemented**. Lead review withdraws the earlier
CGNAT HTTP exception. Keep the current HTTPS/loopback security invariant;
F9 should reduce setup friction without guessing that a route is encrypted.
Related [execution plan](backlog/roadmap-2026-09-06.md) and
[F9 inventory](backlog/innovation-2026-09-06.md).

## Current rule (fact)

`lib/state/profiles.dart` enforces: plain HTTP is allowed **only** to the
phone's own loopback (`localhost`, `127.0.0.1`, `::1`); every other host
must be HTTPS; userinfo, query/fragment, and non-root paths are rejected.
The same policy governs pairing (`selectPairingUrl` re-validates). The
Dart-side rule must stay in sync with
`android/app/src/main/res/xml/network_security_config.xml`.

## Why the previous range exception is withdrawn

Tailscale encrypts traffic carried by its tunnel, but `100.64.0.0/10` is
shared address space, not a certificate of tunnel membership. Carrier CGNAT,
VPN-off conditions and route changes matter. App presence, a hostname suffix,
or a remembered confirmation cannot prove which path a request takes.
Sending server Basic credentials before proving the transport is unsafe.

The Android network-security configuration is host/domain-based. Do not
assume adding a CIDR string expresses a narrow range exception; a broad
base cleartext opt-in would weaken other paths. No `oc.cgnatOk` preference
or cleartext exception is part of the selected design.

## Connection options to prove

| Path | Intended scope | Design requirement |
|---|---|---|
| Private HTTPS endpoint over a tailnet (for example Serve on the computer) | Only authorized tailnet peers | Preserve TLS validation and server auth; verify ACLs, origin routing, SSE and reconnect |
| SSH or equivalent forwarding terminating on phone loopback | User-controlled private transport | Keep existing loopback URL policy; explain that loopback is the phone, not the computer |
| Named HTTPS tunnel or reverse proxy | Exposure depends on provider configuration | Verify auth middleware, stream buffering, idle timeouts, URL lifetime and operator data access |
| Funnel/public tunnel | Public internet endpoint | Separate explicit exposure decision; HTTPS alone does not make the service private |
| Direct HTTP to LAN/CGNAT IP | Not authenticated as private transport | Remains rejected; provide recovery guidance, not an override |

Service-specific claims need pinned documentation and actual stream tests.
Cloudflare Quick Tunnel has been reported to disallow SSE; do not generalize
that limitation to named tunnels or label another service SSE-compatible
without testing it. An OIDC/browser challenge can block the native client
even when HTTPS is correct; never silently bypass it.

Reference starting points (not integration certification):
[Serve](https://tailscale.com/kb/1242/tailscale-serve),
[Funnel](https://tailscale.com/kb/1223/funnel), and
[Quick Tunnel limits](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/).

## Optional app assistance

- A package-presence hint may offer documented setup/help, but never a secure
  status badge. Verify public API stability before using VPN broadcasts or
  deep links; request permission before adding Android manifest declarations.
- Connection guidance should distinguish unreachable, TLS, server-auth and
  stream-stale failures without logging hosts, credentials or response bodies.
- Profile changes invalidate probes immediately; a completed old probe cannot
  bless a different host. Discovery/pairing inputs use the same URL policy.
- The phone-hosted managed server keeps binding loopback. Forwarding or exposing
  it on a tailnet is a separate F9-S4 threat-model decision, not an automatic
  switch to `0.0.0.0`.

## Acceptance before claiming support

1. Demonstrate authenticated connect → SSE activity → Wi-Fi/cellular or
   VPN-off/on interruption → explicit recovery with credentials redacted.
2. VPN loss must not cause fallback to plaintext or a different host. Verify
   HTTPS checks and loopback/CGNAT rejection through manual setup and pairing.
3. Test retained drafts, stale probe responses and correct origin scope.
4. Record each tested service/version, private/public exposure, operator trust
   and failure behavior. Public docs must not advertise an unimplemented path.
