# OAuth feasibility — 2026-09-29

Job `oauth`, branch `codex/oauth`, base `bcf45eb7`.

**Finish line:** establish whether a supported Anthropic/Google browser sign-in
can load on the pinned in-app server; verify the loading contract with fake
credentials and supply the UI owner an actionable alternative if it cannot.
**Non-goals:** real credentials, inference/latency claims, new OAuth client
registration, owner-phone access, UI changes, publishing, or server upgrades.

## Decision

**Do not bundle or enable either subscription OAuth plugin.** OC1 technically
supports OAuth loaders, but the inspected Anthropic and Google subscription
flows fail the permitted-credential-use prerequisite. A checksum cannot repair
that. No production setup/config/auth code changes are needed to preserve the
existing API-key alternative; this slice adds a regression test, a repeatable
isolated runtime probe, and this UI handoff.

The earlier statement that nothing on OC1 1.18 can load these providers is too
broad: an external auth plugin *can* implement a loader. Stock **1.18.32** does
not bundle one for Anthropic or Google. Repeated instance disposal cannot add
one. Explicit `provider` config can also make a row appear loaded without
providing a working OAuth transport; that is not a fix.

**Offer API-key sign-in. Do not advertise switching to OC2 2.0.10 as an OAuth
repair.** OC2 has no built-in browser method for these two providers either.
The probe below verifies loading only; neither fake tokens nor an available
provider row establish valid credentials, successful inference, or faster replies.

## Pinned upstream contracts

Inspected exact tags, resolved upstream on this run:

| Runtime | Commit |
| --- | --- |
| OC1 `v1.18.32` | `545f51d26cc39a907d2867492d498d9607ea5fa4` |
| OC2 `v2.0.10` | `b8cedc1a7a5e2916bbb65dc1d4b620729c261638` |

### OC1 loading and plugins

The [provider implementation](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/provider/provider.ts#L1595-L1660)
loads `type: api` entries directly. OAuth needs a matching `plugin.auth.provider`
and `plugin.auth.loader`; the loader supplies provider options, including custom
request/refresh handling. Config provider options are applied later, without
converting OAuth credentials into an API key or implementing refresh.

The [built-in plugin list](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/plugin/index.ts#L66-L85)
contains OpenAI/Codex, among others, but neither Anthropic nor Google. The
[auth-method endpoint and callback](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/provider/auth.ts#L113-L222)
discover plugin methods and persist callback results. Credential storage,
method availability, runtime loading, and successful inference are separate facts.

The `plugin` entry accepts a package spec or `[spec, options]`, with no trusted
checksum field. A version such as `package@x.y.z` pins only that package;
dependencies can still have ranges. [Config schema](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/core/src/v1/config/plugin.ts#L5-L8),
[resolver](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/plugin/shared.ts#L207-L212),
and [npm installation](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/core/src/npm.ts#L90-L108)
show that lifecycle scripts are disabled during dependency installation. A future
permitted adapter could be shipped as audited local files with every executable
dependency pinned and checksum-verified before import. Automatic package
resolution alone does not meet this job's artifact-integrity requirement.

### Anthropic prerequisite fails

Upstream [removed the Anthropic auth plugin in PR 18186](https://github.com/anomalyco/opencode/pull/18186)
(merged March 19, 2026). The former official plugin repository returned 404 on
this run. [npm metadata](https://registry.npmjs.org/opencode-anthropic-auth/0.0.13)
still exposes `opencode-anthropic-auth@0.0.13`, marked unsupported;
its metadata includes `@openauthjs/openauth: ^0.4.3`. It was not installed/run.

Anthropic's [authentication and credential-use rules](https://code.claude.com/docs/en/legal-and-compliance#authentication-and-credential-use)
disallow third-party applications offering Claude.ai login or routing users'
subscription credentials. Their documented developer path is an API key or a
supported cloud provider. This is the specific prerequisite failure for this
app; neither an old package nor changing request headers resolves it.

### Google prerequisite fails for the inspected subscription plugin

Inspected [opencode-gemini-auth@2.0.1](https://registry.npmjs.org/opencode-gemini-auth/2.0.1), npm `gitHead`
`0836e2024c42142804e6aa9ee0a007d9693670a1`. Its archive was verified against
the registry's SHA-512 integrity before source inspection; SHA-256:
`cd226d0a4944e12baefcc14e6de9878eae6559fd96ca4ea86781264c144d1096`.
It was downloaded under `/tmp`, never installed, imported, or executed.

Its [OAuth flow](https://github.com/jenslys/opencode-gemini-auth/blob/0836e2024c42142804e6aa9ee0a007d9693670a1/src/gemini/oauth.ts#L61-L119)
uses the Gemini CLI client identity; its [request adapter](https://github.com/jenslys/opencode-gemini-auth/blob/0836e2024c42142804e6aa9ee0a007d9693670a1/src/plugin/request/prepare.ts#L44-L91)
rewrites requests to `cloudcode-pa.googleapis.com/v1internal` and identifies as
GeminiCLI. Google's [official maintainer announcement](https://github.com/google-gemini/gemini-cli/discussions/22970)
explicitly identifies Gemini CLI OAuth use by third-party software as
policy-violating. A pinned download does not authorize that flow.

The package also depends on `@openauthjs/openauth: ^0.4.3` and
`@opencode/plugin: 2.0.14`; its absence of install/postinstall scripts is not a
complete dependency or compatibility audit. No speculative vendoring was added.

Google's [supported Gemini API OAuth](https://ai.google.dev/gemini-api/docs/oauth)
is a different integration: an application-owned Cloud project, consent screen,
OAuth client and appropriate API authorization. Those prerequisites are not
present in this app. Do not describe *all* Google OAuth as impossible; a future
properly registered integration needs its own scope and verified refresh/API
contract. For this job, offer a Google AI Studio API key.

### What OC2 2.0.10 changes

OC2's [catalog integrations](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/plugin/models-dev.ts#L34-L49)
advertise key/environment methods, and its [provider plugins](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/plugin/provider.ts)
provide no Anthropic/Google browser adapter. [Availability](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/provider.ts#L361-L380)
uses integration connection presence, so an imported OAuth credential can appear
available without an OC1-style loader.

[Legacy migration](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/database/migration/20260805200742_import_legacy_credentials.ts#L70)
can import OAuth records. [Credential resolution](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/integration.ts#L684-L699)
returns such material unchanged when no refresh implementation exists.
[Model resolution](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/model-resolver.ts#L301-L311)
maps native Anthropic OAuth to `authToken`, but Google OAuth to `apiKey`;
the [Google transport](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/ai/src/providers/google.ts#L29)
uses `x-goog-api-key`. These source paths do not establish successful or
refreshable subscription inference. OC2 differences are source-verified here,
not a real-token experiment or an OC2 runtime-pass claim.

## Fake-only runtime proof

[Probe](../../../tool/qa/oauth_runtime_probe.py) uses the app's pinned Linux
x64 baseline release archive, SHA-256
`763af386ef88a8cab18df00fcf055690e5a55e31a7088beabe02307142a6adce`.
It verifies the archive before executing the binary and checks `--version`.
Each invocation creates temporary HOME/XDG/config/cache/project roots and an
allowlisted environment, and requires a fresh network namespace with only
loopback. External plugins/model fetches are disabled; built-in plugins remain
enabled for the OpenAI control. It terminates only its captured server PID and
deletes the temporary credentials/logs. No browser exchange or prompt is sent.

Repeat on this PC:

```bash
curl -fL --retry 2 \
  https://github.com/anomalyco/opencode/releases/download/v1.18.32/opencode-linux-x64-baseline.tar.gz \
  -o /tmp/oc-oauth-1.18.32.tar.gz
tool/qa/machine_lock.sh test -- unshare -Urn \
  python3 tool/qa/oauth_runtime_probe.py \
  --archive /tmp/oc-oauth-1.18.32.tar.gz
```

The `/config/providers` response can contain credentials: the probe keeps it in
memory and emits only fixed provider IDs and booleans. [Recorded result](runtime-result.json).

| Fake auth state | Anthropic connected / loaded | Google connected / loaded | OpenAI connected / loaded |
| --- | --- | --- | --- |
| OAuth at startup | yes / no | yes / no | yes / yes |
| Same OAuth after idle dispose | yes / no | yes / no | yes / yes |
| Replace Anthropic/Google with API keys, before dispose | yes / no | yes / no | yes / yes |
| API keys after idle dispose | yes / yes | yes / yes | yes / yes |

Only OpenAI advertises a legacy OAuth method. The proof covers OC1's actual
provider loader and legacy key write, not browser completion or the Flutter UI.
The app's dual-store API-key sequence is covered by `product_repository_test`.
An exploratory probe of `/api/integration` returned no matching methods in the
offline sandbox; a subsequent key endpoint returned an empty successful body
that the exploratory JSON reader rejected. That probe is **not** claimed as a
full app-key workflow pass. The final probe deliberately asserts the loader
contract directly, which is the failure under investigation.

## UI hook-up contract for Claude

No `lib/ui/**` or localization files were changed.

1. Use `ServerOperationsGateway.listIntegrations()` and server-advertised
   `IntegrationInfo.methods`; offer `type == 'key'` as **Use an API key**.
   OC1's repository already removes integration OAuth methods without a matching
   `/provider/auth` method and rejects an unsupported method again at launch.
   Do not hardcode a `browser` method or synthesize a numeric method index.
2. Existing `connectIntegrationKey(id, key, label: ...)` is the action contract.
   OC1 writes the integration store and legacy `/auth/{id}`, registers the secret
   with `KitRedact`, and requests an idle-safe runtime refresh. OC2 uses its
   advertised integration endpoint. Keep these operations behind the gateway.
3. `IntegrationInfo.connectionCount` means a stored/server connection exists;
   `IntegrationAuthState.complete` means the callback was accepted. Neither means
   a model is ready. After completion, await `controller.refreshCatalog()`.
4. For an integration in `unloadedProviderIDs`, apply this precedence:
   `providerReloadWaitingOn > 0` means **Sign-in saved; waiting for running replies
   before refreshing**; otherwise `unloadedProvidersUnusable` means **Sign-in saved,
   but this server could not load it after a refresh**, with the API-key action;
   otherwise show **Sign-in saved; provider not loaded yet**. Do not label a
   deferred or unchecked sign-in permanently unsupported or repeatedly reload an
   already unhealable set.
5. An empty `unloadedProviderIDs` alone is **not** positive evidence: the controller
   also returns an empty set when the runtime query fails. For a **provider reported
   available/loaded** label,
   require a successful domain `ServerGateway.configuredProviders()` result
   containing this provider; on failure show **Sign-in saved; availability could
   not be checked**. Never display/cache/log the raw credential-bearing response.
   Apply an asynchronous result only while the same server/profile/location is
   still selected. OC2's `configuredProviders()` delegates to its availability
   inventory, including the imported-OAuth caveat above. Even a loaded provider
   is not an inference-success, valid-credential, or latency guarantee.
6. Suggested provider-specific help: **Use an Anthropic API key** / **Use a Google
   AI Studio API key**. These are separately billed API credentials; do not promise
   they consume an existing chat subscription. If offering account-console links,
   use `openExternalLink` and the normal localized kit components.
7. `_finishOAuth` in `lib/ui/screens/library/integrations_screen.dart` currently
   announces connected unconditionally after refreshing (around lines 2002–2022);
   key completion does similarly (around 1834–1837). Distinguish saved, waiting,
   unavailable and loaded states there. Let the user explicitly choose another
   available model; do not suggest an unrelated free model satisfies their intended
   Anthropic/Google selection.
8. Respect `ServerCapabilities.providerRuntimeRefresh`; never flavor-switch in
   UI or dispose an OC2 runtime. An OC2 switch may be offered for its other
   capabilities, with no claim that it repairs these browser sign-ins. Do not
   copy subscription tokens into a new runtime or silently delete existing auth.

No storage-format migration is introduced. Existing disconnect removes both OC1
credential stores; existing profile deletion rules are unchanged.

## Ownership and verification

Three independent read-only slices covered OC1/plugin feasibility, OC2 behavior,
and the app gateway/UI contract. They owned no repo writes and ran no tests.
The coordinator alone owns this README, the probe and the repository regression;
all runtime/Flutter checks use `tool/qa/machine_lock.sh`.

The added regression exercises externally observable behavior: even with stored
Anthropic/Google connections and a v2-only browser method, OC1 exposes the API-key
method and refuses authorization before any OAuth request. Existing code requires
no change. Formatting uses `dart format --language-version=3.10`.

Validated candidate: `763b7372` (test + probe), based on `bcf45eb7`. Only this
evidence directory was added after the code checks. Source fingerprints:

| File | SHA-256 |
| --- | --- |
| `test/product_repository_test.dart` | `d8477def5f3abd98031e53a1fea79c8597d290541ed19239e15ffe048fb0ba9f` |
| `tool/qa/oauth_runtime_probe.py` | `9d54959bd8a8525e3bb20a4be0ccbd26d4bdae729487fb20ea9cf765fe3ae95a` |

Toolchain: the specified Shorebird path reported Flutter **3.47.1**, Dart
**3.13.1**; the changed Dart file was formatted with language version **3.10**.
Commands actually run (all heavy checks used the machine lock):

```bash
FLUTTER=/home/owner/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
DART=/home/owner/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart
tool/qa/machine_lock.sh analyze -- "$FLUTTER" pub get
"$DART" format --language-version=3.10 test/product_repository_test.dart
tool/qa/machine_lock.sh test -- "$FLUTTER" test --no-pub --concurrency=1 \
  test/product_repository_test.dart
tool/qa/machine_lock.sh analyze -- "$FLUTTER" analyze --no-pub
tool/qa/machine_lock.sh test -- "$FLUTTER" test --no-pub --concurrency=1 \
  test/kit_ratchet_test.dart \
  test/redaction_test.dart \
  test/credential_ingress_redaction_test.dart \
  test/ui_glossary_test.dart \
  test/no_raw_error_text_test.dart \
  test/architecture_boundaries_test.dart \
  test/provider_runtime_heal_test.dart \
  test/provider_reload_running_reply_test.dart \
  test/api2_provider_workflows_test.dart
"$DART" format --language-version=3.10 --output=none --set-exit-if-changed \
  test/product_repository_test.dart
git diff --check
```

Results: dependency resolution passed; **48/48** repository tests passed;
whole-project analysis reported **No issues found**; the nine gate/related files
passed **116/116**, no skips or failures. Format recheck changed zero files.
The final isolated probe exited 0 and asserted all four rows in the recorded
matrix; a separate invocation on the host network correctly refused to run.
Diff and relative-link checks passed. Machine-lock waits were retained rather
than bypassed; the analyzer took 284.8 seconds after acquiring its slot.

The entire repository suite was **not run**: this is a feasibility/test/tooling
slice with no production Dart changes, validated against the job's specified
gates plus affected provider behavior. No full-suite, Android build, emulator,
real-token browser exchange, inference, or latency claim is made.

Shipping state: feasibility complete; existing backend restrictions verified;
new plugin enablement deliberately unavailable; UI implementation handed off to
Claude above. Local commits only, each with `[skip ci]` and the requested
co-author/session trailers. No pushes, deployment, signing, release, or owner
phone access.
