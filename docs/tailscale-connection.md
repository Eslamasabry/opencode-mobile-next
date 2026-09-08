# Tailscale connection journey

Finish line: open or install the official Android app, make a user-controlled
VPN handoff, review an HTTPS server origin, then authenticate, test, save and
connect through the existing profile editor with actionable retry guidance.

Non-goals: embedded VPN, tailnet/account login automation, server setup
execution, public Funnel, account brokerage, or SaaS.

## Current callable mechanism

The owned `oc/tailscale` channel accepts only `check` and `open`. Android
package visibility names `com.tailscale.ipn`; package lookup reports installed,
missing or unavailable, and its launch intent opens the official package.
No package or intent comes from Dart arguments. No VPN state, account data,
credentials, IP list or network status is read. Unknown/failed responses and
five-second bridge timeouts remain unavailable. The UI rechecks package
presence on resume and offers explicit retry. Presence never means connected.

The install action uses the official Play listing through `openExternalLink`.
All guide links use that same host-review policy. No install, login or VPN
permission is performed automatically.

## Address, authentication and recovery

The guided path accepts HTTPS origins only, including a reviewed bare
MagicDNS name normalized to HTTPS. Explicit HTTP, credentials in the URL,
paths, query/fragment data and invalid ports are rejected before any probe or
save. A hostname is not evidence that a service is private. The user reviews
the address before entering server credentials in the existing editor.

The editor retains its name, address and credentials while setup help or the
external app is open. Help invalidates an in-flight probe and returns only a
reviewed address; it does not clear credentials or automatically retry. Failed
probes keep the form editable, with guidance for app state, DNS, HTTPS ports,
Serve/access rules and server authentication. New saved profiles use the
existing save/connect path; failures remain in that editor.

Profiles remain ordinary server profiles. `oc.tailscale.<profileId>` stores
only a guidance flag so later Edit reopens the HTTPS-guided experience. The
existing profile-scoped deletion sweep removes it. Tailscale account or VPN
configuration is external and is not removed when an OpenCode profile is
deleted. Other connection modes retain their existing URL and probe policy.

## Official source check — 2026-09-08

- [Android installation](https://tailscale.com/docs/install/android): official
  Play distribution and user-controlled sign-in/VPN setup.
- [Tailscale Serve](https://tailscale.com/docs/features/tailscale-serve): private
  tailnet access to local services, HTTPS prerequisites and access rules. Use
  the full HTTPS origin printed by Serve, including a non-default port when
  present. The app neither enables Serve nor runs its commands.
- [HTTPS certificates](https://tailscale.com/docs/how-to/set-up-https-certificates):
  the full device/tailnet certificate name appears in certificate transparency
  logs even though access stays private. This is disclosed before linking to
  setup guidance.
- [Android package visibility](https://developer.android.com/training/package-visibility/declaring):
  the manifest declares the one package needed for lookup and launch.

## Verification checkpoint

Implemented and locally verified: localization generation, formatting, 66
focused/related checks, eight production capture cases (ten inspected PNGs),
and a clean analyzer. See the [local evidence](qa/tailscale/README.md).
Independent review and integration remain with the coordinator, who also owns
fresh-device install/handoff/network checks. No real-tailnet connectivity,
native compilation or device verification is claimed at this checkpoint.

## Coordinator native handoff checklist

Run on the fresh QA device; these checks remain pending until recorded by
the coordinator. Use synthetic profile data. Do not log in to a real tailnet
or grant account/VPN access on a user's behalf.

1. Before installation, open Servers → Connect with Tailscale. Confirm the
   missing-app state, then use Get official Android app. The host review must
   identify `play.google.com`; cancellation must stay in the setup screen.
2. Install the official `com.tailscale.ipn` app using the approved QA process.
   Return to OpenCode and confirm installed/unverified copy. Open Tailscale
   must launch that package and leave its sign-in/VPN decisions to the user.
3. Type a synthetic HTTPS address, make the app handoff, then return. Confirm
   the draft address survives and only package presence is refreshed. There
   must be no connected-VPN claim or automatic server probe.
4. Continue to authentication, type synthetic server credentials, open setup
   help, switch apps and return. The editor must retain both address and
   credentials. No credentials belong in intents, URLs or logs.
5. Enter HTTP, an invalid port, then an HTTPS address that is unavailable on
   the QA network. Invalid origins must be blocked before transport; failed
   probes must keep editable fields and recovery guidance, with explicit
   retry. Verify the missing/disabled-app recovery path if the QA package is
   subsequently unavailable.
6. With an independently authorized private HTTPS test server, verify Test
   connection → Save/connect → reopen saved Edit → remove profile, including
   `oc.tailscale.<id>` deletion. If no real tailnet is authorized, leave this
   network proof explicitly unverified; synthetic checks are not a substitute.
7. Inspect touch targets, keyboard avoidance, back/cancel, scrolling and
   readable errors on the actual phone, including 320 px/large-text layouts.

Record device/app version, actual cases performed and limitations. Native
package launch proves a handoff only; it cannot prove VPN or server access.
