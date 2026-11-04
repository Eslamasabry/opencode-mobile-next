VERIFIED: green

# P12-BUILD-INTEGRATIONS-B — 48+1 unproven Flutter integration behaviours, ported natively

Branch `claude/native-parity-integration-u68fjo`, based on `native/p12-integrate-2026-11-03` (`71f74de8`).

## Rows proven: 49 of 49

A row counts only when at least one passing test is named after it and no test named after it failed, skipped or
errored. That was checked from the JUnit XML of the runs below with a small script, because
`native/tool/parity/ledger.py` **does not exist** on the base branch (`python3 -I native/tool/parity/ledger.py` →
no such file). The integrator should rerun the ledger once it lands.

| Cluster | Module (new) | Rows | Tests |
|---|---|---|---|
| Usage subscriptions, widget, budgets | `:engine:integrations-usage` | B-NAV-080, B-PROTO-123, B-SEC-116, B-SEC-178, B-SEC-179, B-STATE-068, B-STATE-146…154 | 68 |
| Provider quota | `:engine:integrations-quota` | B-PROTO-058, B-PROTO-084…087, B-SEC-138…141 | 51 |
| Bring-your-own host | `:engine:integrations-host` | B-SEC-024, B-SEC-027…037 | 47 |
| Connectors, plugins, MCP, skills, providers | `:engine:integrations-catalog` | B-PROTO-049, 078, 079, 080, 081, 103, 108, B-SEC-095, 100, 102, 137, 173, B-STATE-120 | 39 |
| Usage › Plans screen | `:feature:plans` | B-NAV-080, B-STATE-153, B-STATE-154 (screen level) | 6 |

Per-row test counts: B-NAV-080 3, B-PROTO-049 2, -058 4, -078 3, -079 2, -080 1, -081 3, -084 5, -085 7, -086 6,
-087 5, -103 2, -108 4, -123 3, B-SEC-024 4, -027 6, -028 2, -029 6, -030 3, -031 3, -032 3, -033 4, -034 2, -035 2,
-036 4, -037 8, -095 3, -100 5, -102 4, -116 2, -137 5, -138 5, -139 5, -140 7, -141 7, -173 2, -178 2, -179 6,
B-STATE-068 2, -120 3, -146 4, -147 3, -148 3, -149 4, -150 4, -151 6, -152 11, -153 8, -154 13.

The module names are `integrations-*`, not `:engine:integrations`, so they can't collide with the sibling
integration sessions that may create `:engine:integrations`. All four are registered in `MODULE_OWNERS` with owner
`build-integrations-b`.

## Journeys now working in the app

- **Usage › Plans** (`PlansRoute`, `:feature:plans`). How to get there: open **Servers**, then tap **Plans**. The
  button sits below Agents and uses the existing `usageTabPlans` word, in English and Arabic.
  - The screen lists every plan across the connected OpenCode servers. For each window it shows how much is left
    and when it resets, then the state in plain words, the fix in plain words, and the connections where the plan
    is signed in.
  - It also lists API-key accounts, including the Claude-through-OpenCode note, and the providers not signed in
    anywhere.
  - Refresh is a visible button. A reader that broke shows a visible **Try again** row. This phone appears as
    "This phone" ("هذا الهاتف").
  - Built from kit parts only, with RTL through the layout direction.
- **Real data** (`androidApp/.../graph/PlansWiring.kt`):
  - `UsageSubscriptionsEngine` is fed by `OpenCodeSignInSource` over each live OpenCode connection's
    `ServerGateway.providers()`.
  - Cached numbers, alert levels and the widget feed are stored under the Flutter keys (`oc.usageLast.<id>`,
    `oc.usageAlerts.v1`, `oc.widgetUsageSubs`), and the native Usage widget is redrawn after a write.
  - Retired `oc.budgets.*` keys are dropped on first load (B-STATE-068).
  - `UsagePlansDeletionCleaner` (a `ProfileResourceCleaner`) makes a removed server take its numbers, overrides
    and widget rows along (B-SEC-178).

## Not wired yet

These are proven in tests at engine level; the Android adapters or screens are still missing:

- **In-app Ubuntu quota collector numbers** (`:engine:integrations-quota`). This needs an OkHttp `QuotaTransport`
  that never follows redirects, a SecureRandom `randomBytes`, and the `QuotaLinuxHost`/`QuotaRootfs` bindings. A
  device is needed to prove it end to end.
- **Paseo and phone usage lists** (`PaseoUsageMonitor`). This needs the Paseo `provider.usage.list` call, plus a
  `UsageSource` adapter feeding its readings into the engine.
- **Alert delivery.** `UsageAlertBridge` has no Android implementation, so `alertsOn` stays false. Alert levels are
  stored and shown.
- **Budgets on the Usage and cost page.** `UsageBudgets` is ready, but `:feature:usage` belongs to
  P12-SEARCH-USAGE.
- **BYO host** (B-SEC-024…037). Flutter also keeps this behind the `OC_BYO_HOST` flag, and there is no screen. It
  needs the OpenSSH-in-Ubuntu shell, the `oc/byo_host_signer` channel, DNS and loopback-HTTP adapters, a real
  tailnet host and a device.
- **Catalog** (MCP, plugins, skills, provider logos). Engines only. The screens belong to other units or come after
  the switch. Wiring needs `CatalogTransport`/`ProviderLogoFiles` implementations and protocol events routed to
  `pluginEventHint`/`skillEventHint`.

## Deviations

- Engines take no timers; callers drive `refresh()`. Results are values (`Outcome`, `HostResult`, `QuotaRead`),
  not exceptions.
- B-SEC-179: `UsageWidgetFeed.record` takes `QuotaWidgetInput` (display numbers only), so account refs, plan names
  and URLs can't reach it by type. The quota-snapshot→input mapping belongs in the wiring.
- B-SEC-141: the Flutter test removes the token through `ProfileStore.remove`, which this module may not use
  (`:engine:profiles`). The native test proves `ownedData` lists the secret, `sweep` removes the switch, and
  `turnOff` deletes the token.
- B-SEC-102: the native test checks the header-row form model and diagnostics redaction. The masked field itself is
  not tested.
- B-SEC-100: OAuth always uses the pasted code or URL, because there is no loopback listener. The Flutter
  "owned callback" and "busy port" cases are not ported.
- The host device token uses `Uuid.random()` bytes: 244 random bits out of 256.
- Copy for Plans and the host setup steps lives in `PlansWords` and the engine (TODO(words)) until the l10n owner
  moves it into `:ui:words`.
- Host-test fixture addresses are built at run time (`ip4(...)`, `ip6(...)`, `tsNet(...)`) so no address literal
  is committed. `scan_push.sh` is clean.

## Commands and results (this container, JDK 21, Android SDK 37.1 / build-tools 37.0.0)

- `./gradlew :androidApp:assemblePreviewDebug :feature:servers:desktopTest :feature:plans:desktopTest` →
  BUILD SUCCESSFUL. This needed `native/tools/vendor/fetch.sh` first, for the hash-pinned sherpa-onnx AAR.
- `./gradlew :quality:test :engine:integrations-usage:desktopTest :engine:integrations-quota:desktopTest
  :engine:integrations-host:desktopTest :engine:integrations-catalog:desktopTest :feature:plans:desktopTest
  :feature:servers:desktopTest --continue` → BUILD SUCCESSFUL. All of them had 0 failures, 0 errors and 0
  skipped:
  - quality: 64 tests
  - integrations-usage: 68
  - integrations-quota: 51
  - integrations-host: 47
  - integrations-catalog: 39
  - feature:plans: 6
  - feature:servers: 71
- No `testDebugUnitTest` applies. The new modules are AGP-KMP libraries without host tests (their tests are
  `desktopTest`).
- Local only, not committed: `~/.gradle/init.d/mirror.gradle` adds Google's Maven Central mirror, because Central
  returned 429 to this container.

## For the integrator

- Union-merge `MODULE_OWNERS` (4 engine rows plus `:feature:plans`) and the two `androidApp/build.gradle.kts`
  lines.
- The Servers page edit is three small additions: `ServersActions.openPlans`, the ViewModel override, and one
  button.
- Rerun `native/tool/parity/ledger.py` when it exists.
