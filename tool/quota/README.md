# Optional provider quota collector (Node >= 20)

**Claude collection is disabled pending a supported, permitted integration.**
Current OpenCode does not bundle Claude Pro/Max subscription sign-in. Historical
OAuth-file parsing or synthetic usage mapping is not proof of support or
permission to reuse subscription tokens. The legacy Claude route returns an
unsupported snapshot without reading an auth source or contacting a provider.
Retired `OCMN_CLAUDE_AUTH_FILE` / `OCMN_CLAUDE_AUTH_FORMAT` options are ignored
with the fixed startup warning `QUOTA_CLAUDE_COLLECTION_UNAVAILABLE`; valid
Codex configuration continues to work. Remove those obsolete options. No
workaround plugin is installed or recommended.

Sources checked September 6, 2026:
[OpenCode Anthropic support](https://opencode.ai/docs/providers/#anthropic) and
[Anthropic credential policy](https://code.claude.com/docs/en/legal-and-compliance#authentication-and-credential-use).

This is an **optional, independently deployed extension**, not an upstream
OpenCode API, plugin installation, or bundled mobile/background service. It
implements the frozen `lib/domain/provider_quota.dart` contract at exactly:

```text
GET /ocmn/quota/v1
GET /ocmn/quota/v1/claude
GET /ocmn/quota/v1/minimax
```

The first route reports **core Codex windows on a ChatGPT OAuth account**;
the second is retained only to return **unsupported** safely. The MiniMax route
reports explicit percentages for its shared `general` Token Plan pool; see the
[verified source contract and limitations](#minimax-token-plan). These are not OpenCode
project consumption, all product/model allowances, an API-key
budget, or a promise that a model request will succeed. No provider access or
deployment was performed during implementation; tests use synthetic inputs.

## Trust and deployment topology

```text
Flutter -- HTTPS + existing OpenCode Basic credentials --> trusted operator proxy
        -- loopback + dedicated collector Bearer token --> this collector
        -- HTTPS + explicit provider credential --> fixed provider quota endpoint
```

The operator explicitly grants this service read access to a chosen Codex
credential file and/or MiniMax Subscription Key file. Authorized proxy users
can see the configured sources' normalized windows. The operator must be trusted with both the OpenCode authentication
boundary and that account. The reverse proxy is part of that security boundary,
not a transparent, untrusted relay.

The operator's **HTTPS reverse proxy must**:

1. Host the fixed paths above at the **same origin as the OpenCode server**,
   separate from its route table. Route only these exact paths, without
   query parameters, to the collector. Do not rewrite arbitrary paths into it.
2. Explicitly authenticate the client's HTTP Basic credentials using the
   existing OpenCode server credential policy. An otherwise public proxy path,
   or trusting an arbitrary supplied `Authorization` header, is not sufficient.
3. Strip the client's Authorization header and inject the **dedicated
   collector-only read Bearer token** from the operator's protected secret
   mechanism. Do not forward Basic, provider OAuth tokens, or a client-chosen
   Bearer value. Do not expose the collector token to Flutter.
4. Reach the collector only at `127.0.0.1:4195` (or the configured port), disable
    caching and auth/header/body logging on these routes, and apply request-rate
   limits. Apply backoff when the snapshot reports `rateLimited` or unavailable.
5. Use real TLS validation; an IP that looks private is not an encrypted route.

**There is no unauthenticated loopback fallback.** Binding loopback does not
authorize other local processes. The collector requires its Bearer token even
from the proxy. Another container's loopback is not this host's loopback; this
CLI deliberately offers no public/non-loopback bind option. There is no generic
proxy/target URL argument and no capabilities endpoint: callable support is the
authenticated versioned snapshot route itself, not a claim about OpenCode.

## Explicit operator configuration

Use the operator's secret manager to provision a high-entropy, dedicated read
token file (at least 32 characters; do not derive it from the OpenCode password).
The token must use Bearer-safe ASCII characters, at most 4096 before optional
`=` padding. One trailing newline is accepted. Restrict file/directory access
to the operator and service account; a short string repeated to 32 characters
does **not** provide adequate entropy. No raw secret belongs in a command line,
environment value, persistent URI, process log, or proxy access log.

Configuration consists of file paths and non-secret settings only:

| Variable | Behavior |
|---|---|
| `OCMN_QUOTA_READ_TOKEN_FILE` | Required absolute path to the collector read-token file. Loaded only by CLI startup. No default. |
| `OCMN_QUOTA_AUTH_FILE` | Optional absolute path to the explicitly authorized provider credential file. Without it, return `unconfigured` without provider/file discovery. |
| `OCMN_QUOTA_AUTH_FORMAT` | `codex` (default) or `opencode`; never inferred from a path. |
| `OCMN_CLAUDE_AUTH_FILE` / `OCMN_CLAUDE_AUTH_FORMAT` | Retired; ignored with a fixed startup warning. No Claude credential source is retained or read. |
| `OCMN_MINIMAX_KEY_FILE` | Optional absolute path to an explicitly provisioned MiniMax Subscription Key text file. One trailing newline accepted. No default, discovery, OAuth reuse, or PAYG balance requests. |
| `OCMN_QUOTA_PORT` | Default `4195`; integer in `1024..65535`. Host is always `127.0.0.1`. |

For example, after configuring those variables through the service manager:

```sh
node tool/quota/collector.mjs
```

No package installation is needed. There are no CLI arguments. Importing the
module does not read environment configuration, load auth files, fetch, or
listen. The CLI emits only fixed startup/configuration error codes to stderr;
it also emits the fixed warning above for retired Claude options. It has no
request logging or raw exception output. Do not enable external HTTP
debug instrumentation that records credentials. The operator owns service
supervision, filesystem permissions, TLS/proxy configuration, updates and
rollback. Nothing installs or starts this service from Flutter.

### Supported read-only OAuth files

- **`codex`:** selected `tokens.access_token` and `tokens.account_id` in the
  explicit Codex auth document. An absent `auth_mode` with this token bundle or
  `auth_mode: "chatgpt"` is accepted; API-key/PAT/other auth material is not.
  Optional `tokens.id_token` supplies the selected user identity; access-token
  JWT claims provide a fallback and expiry. There is no keyring, account-session
  inventory, default-home-path, or filesystem-scanning fallback.
- **`opencode`:** the explicitly selected OpenCode **v1** file contains
  `openai: {type: "oauth", access, accountId, expires, ...}`. `expires` is Unix
  milliseconds. This format is source-verified below, not inferred from v2's
  integration/credential API. An API-key entry is unsupported; missing account
  ID, malformed file or expired token requires auth. No provider keys/config
  are requested from an OpenCode HTTP endpoint.

The collector **never refreshes or writes credentials**. The existing OAuth
owner must refresh/login independently. Explicitly granting read access does
not transfer refresh ownership. JWT parsing supplies identity hints, not local
signature verification or proof of provider authorization. Unknown/opaque
access tokens cannot provide an expiry/user claim; WHAM remains authoritative.
FedRAMP-marked identities are unsupported rather than guessed onto another edge.

All input files are capped at 64 KiB and read through read-only regular-file
handles, with a second streaming size check against growth. Symlink/nonblocking
open safeguards are used where supported by the OS; Linux rejects final-path
symlinks. No raw file contents or native file errors reach a response.

## Network, mapping, failure and cache contract

The Codex provider operation is `GET
https://chatgpt.com/backend-api/wham/usage`, with no body/query, Bearer OAuth,
`ChatGPT-Account-Id`, JSON Accept, and an identifying `ocmn-quota/1` User-Agent.
Redirects are disabled, and response URL/redirect metadata is checked. Both
fetch and response streaming share a 10-second timeout. Declared and actual
decoded response bytes are capped at 64 KiB. HTML, invalid UTF-8/JSON and known
malformed fields produce `invalidResponse`; raw provider errors are discarded.

| Collector HTTP response | Meaning |
|---|---|
| 200 | Domain snapshot, including provider failures and unconfigured state. |
| 401 | Missing/incorrect/duplicate collector Bearer authorization. Not provider login failure. |
| 403 | Non-loopback peer or a request body on the read route. |
| 404 / 405 | Unknown request target (including queries) / method. |
| 503 | Unexpected internal collector failure, safe fixed envelope only. |

WHAM 401 maps to snapshot `authRequired`; 429 to `rateLimited`; 404/405/501 to
`unsupported`; 403, redirects, network/timeouts and other failures to
`unavailable`. A 204 or malformed 200 is `invalidResponse`. No secondary vendor
endpoint, token refresh, arbitrary URL, or synthetic allowance is attempted.

- `schemaVersion: 1`, `provider: "codex"`, `source: "codex.wham"` are fixed.
- `account.ref` is lowercase 64-hex HMAC-SHA256 of the **selected account ID**,
  keyed with the dedicated read token. It is not an email, label or raw ID.
  Rotating this token changes references; update proxy and collector together.
- `account_id` must be present and match the selected account. A known selected
  user also requires matching `user_id`. Missing evidence is `unverified`,
  contradictory evidence `mismatch`; neither is cached or exposes windows/plan.
  Those otherwise-valid responses have `status: ok`, `freshness: none` and no
  measurements. Invalid/auth/error snapshots also have empty windows and null
  `ordinaryUsageAllowed`.
- Account-only evidence can match and show windows, but
  `ordinaryUsageAllowed` remains null without matching selected **account and
  user**. Otherwise it preserves `rate_limit.allowed`, never derives from
  percentages, `limit_reached`, reset timestamps, or credit balances.
- Only domain-allowlisted plan names are emitted. Unknown plans are omitted.
- Windows have IDs `primary` / `secondary`, not hardcoded five-hour/week labels.
  Missing/null windows produce `status: missing` without measurements. Present
  windows require a finite percentage in `[0,100]`; fractions are preserved.
  Non-null duration must be a positive integer within the domain bound;
  non-null absolute reset seconds are converted to milliseconds. Missing/null
  duration/reset is omitted. `reset_after_seconds`, if present, is validated
  but never used to fabricate an absolute reset. Passing reset time never
  replenishes the reported percentage.
- A successful, matched snapshot is cached in memory for 60 seconds; these
  timestamps describe collector freshness, not a provider guarantee. Credential
  source reads occur **before every cache lookup** and again before publishing a
  new fetch. Serialized auth reads, identity/token/expiry fingerprinting,
  cancellation, generation checks and singleflight prevent old-account races.
  Token/user changes invalidate even when the public account reference is equal.
- Errors invalidate expired data and return no measurements, not old quotas with
  a new error/account label. The frozen contract forbids error snapshots carrying
  stale windows; this collector never emits `freshness: stale`. The mobile owner
  can retain a separately identified earlier success as stale. Failed results
  are not cached, so the proxy/client must rate-limit retries. Reconnect calls
  refetch the snapshot route, subject to the same 60-second cache.

Intentional scope/losses: additional metered buckets, credits/balances, spend
controls, banners and earned-reset data are not interpreted or copied to the
output. Unknown input remains only in the bounded, transient parse, never logs
or retained raw state. This is **core window reporting**, not total purchasable
capacity or model eligibility. There are no writes,
reset-credit consumption, account switching, or model operations.

## Pinned public evidence

Evidence pins are source assumptions, not verification of an installed binary
or successful authenticated provider requests.

- Codex commit **`9587c9ef366bd678ea5e9310f59ec33fdc44df7e`**:
  [WHAM method/path and non-exposing read](https://github.com/openai/codex/blob/9587c9ef366bd678ea5e9310f59ec33fdc44df7e/codex-rs/backend-client/src/client/rate_limit_resets.rs),
  [headers and window mapper](https://github.com/openai/codex/blob/9587c9ef366bd678ea5e9310f59ec33fdc44df7e/codex-rs/backend-client/src/client.rs),
  [payload/window models](https://github.com/openai/codex/tree/9587c9ef366bd678ea5e9310f59ec33fdc44df7e/codex-rs/codex-backend-openapi-models/src/models),
  [identity validation](https://github.com/openai/codex/blob/9587c9ef366bd678ea5e9310f59ec33fdc44df7e/codex-rs/app-server/src/request_processors/account_processor.rs#L1129-L1230),
  [auth file](https://github.com/openai/codex/blob/9587c9ef366bd678ea5e9310f59ec33fdc44df7e/codex-rs/login/src/auth/storage.rs),
  [JWT identity/expiry fields](https://github.com/openai/codex/blob/9587c9ef366bd678ea5e9310f59ec33fdc44df7e/codex-rs/login/src/token_data.rs).
- OpenCode v1 commit **`f12e14cf1640cbf0dfb6b1ff425b2daaef459eec`**:
  [provider-keyed OAuth storage schema](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/auth/index.ts),
  [Codex OAuth access/account ID and millisecond expiry](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/plugin/openai/codex.ts).

WHAM is an **internal, undocumented provider HTTP endpoint** despite its public
first-party client code; schema/access policy can change. The public Codex
`account/rateLimits/read` RPC is a documented semantic counterpart, **not an
OpenCode HTTP route**. After initialization it returns `result.rateLimits` /
`rateLimitsByLimitId`, with reset Unix seconds and window durations rounded UP
to minutes. It loses relative reset seconds and can fetch earned-reset details.
This collector does not start/connect an app-server or enable Reserve exposure.
Its raw HTTP mapper preserves second durations and allows finite fractional
percentages/missing reset metadata as explicitly permitted by the frozen domain
contract (the pinned generated WHAM window model uses required integer fields).

## Archived Claude mapping research — not live collection

Pure payload/auth-schema parsers and synthetic fixtures are retained for future
permitted integration work. They do not authorize live collection. There is no
Claude file-reader adapter or provider destination in the normal collector;
the app also rejects Claude collection before creating a request.

The historical payload has no independent account ID, so research snapshots use
`sourceBound`, never `matched`. The caller-supplied opaque reference is not
proof of account identity. No account email, plan inferred from local metadata,
or ordinary-use permission is manufactured.

Research snapshots use `provider: "claude"`, `source: "claude.oauth"` and the
same version-one percentage-window envelope. The pure mapper recognizes:

- Legacy `five_hour` / `seven_day`: finite `utilization` in `[0,100]`, optional
  RFC3339 `resets_at`; durations follow the explicit legacy window names.
- Structured `limits[]`: `kind: "session"` / `"weekly_all"` with finite
  `percent` and optional reset; no duration is guessed from a generic kind.
  When supplied, this schema is authoritative, even if empty. Duplicate core
  limits or malformed present fields fail visibly rather than merging old data.

Missing/null windows remain missing; additional model-scoped windows and
extra-usage billing are explicitly omitted from this slice. No percentage is
clamped into range and no reset passage refills locally. These historical shapes
were investigated through public-source review, not an authenticated account
query. Synthetic compatibility fixtures do not establish a supported API or
permission to reuse subscription credentials. Re-enabling collection requires
a separately reviewed, supported and permitted integration, not a file path
or an undocumented-endpoint workaround.

GLM and Gemini collectors are not implemented. Their units/auth/reset
semantics must be verified separately, rather than guessed from these adapters.

## Focused verification

```sh
node --test tool/quota/collector.test.mjs tool/quota/minimax.test.mjs
```

Tests use injected fetch/auth/clock, fake request/response objects (no listener),
and explicitly created synthetic files in the OS temporary directory. Set
`OCMN_QUOTA_TEST_TMPDIR` to an existing directory to constrain that scratch
location (this workspace uses `/tmp/opencode`). They cover
exact domain JSON, 0/100/fraction percentages, missing/invalid fields, identity
evidence, error separation, strict URL/method/auth guards, bounded streaming and
timeout, cache/singleflight/account races, import safety, configuration, both
Codex file formats and absence of credential-refresh writes. Claude coverage
checks pure historical mapping, unsupported responses with zero auth/network
calls, and continued Codex operation when retired Claude settings remain.

Remaining deployment risks require operator review: live-provider approval,
endpoint/credential-version drift, sole OAuth refresh ownership, account-file
selection versus other clients' active accounts, actual proxy Basic verification,
secret provisioning/rotation, restart/update policy and signed mobile integration.
No SDK generation/provenance change is involved. Hand app/repository integration
and the serial repository gate to the lead; do not run release tooling here.


## MiniMax Token Plan

**Implemented with synthetic fixtures; live access and deployment unverified.**
Source review on September 7, 2026 established an official public quota call:
`GET https://www.minimax.io/v1/token_plan/remains` with
`Authorization: Bearer <Subscription Key>`. The current
[MiniMax FAQ](https://platform.minimax.io/docs/token-plan/faq#how-to-check-token-plan-usage)
documents this request and distinguishes subscription keys from pay-as-you-go
keys. The latter are not subscription quota; keys beginning `sk-api-` return
`unsupported` before any request. No browser credentials or CLI OAuth files
are read. Operator approval and provisioning are required for live collection.

Schema source is the official MiniMax CLI at commit
[`bfbb4cb75ec343149eaccfd668c5011aa27bcf2b`](https://github.com/MiniMax-AI/cli/tree/bfbb4cb75ec343149eaccfd668c5011aa27bcf2b):
[types](https://github.com/MiniMax-AI/cli/blob/bfbb4cb75ec343149eaccfd668c5011aa27bcf2b/src/types/api.ts),
[quota rendering](https://github.com/MiniMax-AI/cli/blob/bfbb4cb75ec343149eaccfd668c5011aa27bcf2b/src/output/quota-table.ts), and
[count ambiguity handling](https://github.com/MiniMax-AI/cli/blob/bfbb4cb75ec343149eaccfd668c5011aa27bcf2b/src/utils/quota.ts).
The adapter deliberately uses explicit remaining percentages only; legacy
`*_usage_count` fields changed meaning and are not sufficient evidence.

| Provider field | Collector meaning |
|---|---|
| `model_remains[]` item named `general` | Shared general subscription pool only; no aggregation of model rows. Missing pool is unsupported; duplicate pool is invalid. |
| `current_interval_remaining_percent` | Primary used percentage is exactly `100 - reported remaining`. |
| `current_weekly_remaining_percent` | Secondary used percentage with the same units. |
| `start_time`, `end_time` | Primary duration and reset in epoch milliseconds. |
| `weekly_start_time`, `weekly_end_time` | Secondary duration and reset in epoch milliseconds. |
| Window status `1` / `2` | Limited / exhausted; exhausted must agree with zero remaining. |
| Window status `3` | Unrepresentable by the current mobile percentage contract; show missing, never fabricated unlimited capacity. |
| `weekly_boost_permille` other than `1000` | Weekly window missing: boosted capacity can exceed the mobile contract's percentage range. |

The upstream TypeScript interface requires both timestamp boundaries for each
reported window. Missing boundaries return `invalidResponse` as schema drift,
as do present nulls or malformed timestamps. No reset or window duration is
reconstructed from countdowns or known plan names.

Percentages must be finite numbers within 0–100; unknown statuses, invalid
reset ranges, or contradictory exhaustion are `invalidResponse`. Missing
percentages remain missing even when counts exist. A passed reset timestamp
never fabricates replenishment. Snapshot source is `minimax.tokenPlan`, provider
is `minimax`, and ordinary request availability stays unknown (`null`).

The API supplies no independently matching account ID. Account status is
`sourceBound`, tied to an opaque HMAC of the configured key. It must not be
presented as an account identity match. Key rotation changes the reference,
invalidates cached data, and cancels old in-flight work. Successful responses
cache for 60 seconds; key state is rechecked before publishing. Existing
size/time bounds, exact-host no-redirect requests, authenticated loopback route
and fixed errors apply. Provider payloads, model labels, keys and error strings
never pass through to the app or logs.

[The fixture](fixtures/minimax-general.json) is synthetic, including deliberately
contradictory count fields to prove they cannot influence percentages.
`minimax.test.mjs` covers mapping, missing/unlimited/boosted windows, invalid
payloads, key loading, fixed-host requests, rotation/cache expiry, HTTP errors,
authenticated routing and CLI configuration. The lead runs these tests serially.

GLM remains unavailable: the official
[documentation index](https://docs.z.ai/llms.txt) and
[Coding Plan FAQ](https://docs.z.ai/devpack/faq) did not establish a supported
third-party subscription-quota request/schema in this September 7 review.
The commonly cited `/api/monitor/usage/quota/limit` dashboard endpoint is not
sufficient by itself. A public supported contract or explicit vendor permission
with current schema/units/identity evidence is the prerequisite. Gemini was not
reopened after MiniMax met this slice's feasibility gate.
