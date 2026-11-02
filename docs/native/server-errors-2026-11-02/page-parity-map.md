# Page parity map — native preview Servers / Conversations / Chat+Composer (2026-11-02)

An actionable checklist of what the currently routed native preview pages must eventually prove
against the Flutter-derived catalog, what exists today, and what evidence is still missing. Written
in private worker snapshot `page-parity-map` (private baseline `672e599`, derived from native commit `7ff73263`) from read-only inspection; no Gradle,
builds, tests or devices were run.

**Rules of interpretation (binding on this document):**

- Native test *source presence* is not a pass and not runtime enablement. Counts below are static
  `@Test` scans and catalog-id citations in test names/comments; none were executed here.
- No catalog row is marked done and no catalog status is changed. `scope.jsonl`/`behaviors.jsonl`
  were read only.
- The prior drafts verification's **287 focused checks** (143 `:engine:composer` + 68
  `:feature:chat` + 32 kit + 44 quality) remain exactly what their report says: one text-draft
  slice. They are **not** full-page parity and are not relabeled here.
- E2E-11's flow is unchanged; drafts-with-attachments, stash/saved-prompts and the full visual
  (golden/screenshot) phase remain unqualified.
- "Wired" means reachable on a routed preview screen in this snapshot. "Engine-only" means
  production code exists in a module but no routed surface invokes it.

Machine-readable companion: [page-parity-map.csv](page-parity-map.csv) (one row per cluster, same
snapshot).

## 1. What is actually routed in the preview today

Start destination is `ChatsRoute.Home` (`native/ui/shell/.../App.kt:32`). Installed entries:

| Route | Screen | Owner file |
|---|---|---|
| `ChatsRoute.Home` (Root) | Conversations feed (search field, needs-you/running/project filters, return brief, day groups, Servers + New actions, Show all) | `native/feature/chats/.../ChatsEntries.kt:28`, `ui/ChatsScreen.kt` |
| `ChatsRoute.All(filter)` | **Stub**: filter label only (`ui/ChatsScreen.kt:280`) | `ChatsEntries.kt:33` |
| `ChatsRoute.New(profile, draftKey)` | Server setup/management screen | `native/feature/servers/.../ServersEntries.kt:23` |
| `ServersRoute.Home` | Same server management screen | `ServersEntries.kt:29` (opened by `PreviewSettingsDoor`, `PreviewRuntime.kt:335`) |
| `ChatRoute(ref, focusComposer)` | Chat screen + `BasicComposerHost` + request slot | `native/feature/chat/.../ChatEntries.kt:30` |

App classpath (`native/androidApp/build.gradle.kts:11-34`): `core:*`, `engine:connection`,
`engine:transcript`, `engine:profiles`, `engine:composer`, `engine:agents`, `engine:delivery`,
`engine:requests`, `protocol:oc2`, `ui:*`, `feature:chats|chat|servers`.
**Not** on the classpath: `:feature:chat-composer`, `:feature:settings`, `:feature:onboarding`,
`:engine:media`. `PreviewRuntime` (`androidApp/.../PreviewRuntime.kt`) implements
`ServerConnections` (save/connect/createConversation/remove), the merged per-profile feed, event
subscription, and the removal fences (queue → delivery → journals → drafts → `ProfileDeletion`).

## 2. Servers page (`ServersRoute.Home`, `ChatsRoute.New`)

Full cluster detail in the CSV. Summary of the gap structure:

| Cluster | Catalog rows (Flutter tests) | Native today | Still needed |
|---|---|---|---|
| Cleartext consent + private-address rules | B-SEC-119/120/122 (`private_network_cleartext_test.dart`) | `:core:security` AddressRulesTest cites them; save path wired; ViewModel tests cite B-SEC-124, B-SEC-153, B-STATE-065, B-STATE-098 (7 tests) | Native rows for B-SEC-121/125 (origin-scoped consent persistence/invalidation) |
| Connection failure copy | B-NAV-007/008/009, B-NAV-045 (`connection_failure_test.dart`, `recent_errors_plain_test.dart`) | **Absent here**; the server-errors contract (`ConnectionStage`/`connectionFailure`, `ServerFailureKind`) is being built in other snapshots; this snapshot's tree has none of it | The contract slice landing, its tests, then E2E-03 on the emulator with FX-TLS variants |
| No-raw-text rules | B-SEC-104/130/131/132 (`no_raw_error_text_test.dart`, `product_error_text_test.dart`) | Same in-flight contract; generic issue copy is the only fallback today | Tests that details stay collapsed and credential-free |
| Server probe verdicts | B-PROTO-094/095 (`server_probe_test.dart`) | No probe stage: the preview opens the gateway directly | Full verdict selection/integration + tests, then E2E-03 steps 1–2 |
| Profile store/deletion | B-STATE-060/063/064/065/098 (`profile_deletion_test.dart`, `profile_store_test.dart`, …) | `:engine:profiles` tests cite 063/064/065/098 (+ B-STATE-013/020/058/061/145); frozen 188-row preference ledger is a test resource; removal wired with fences | B-STATE-060 sweep row cited only via `core:data` ledger tests; phone-only failure diagnosis pending (that is this branch's parent task) |
| Queued prompts kept on removal | B-STATE-066/067 | Not wired: removal refuses (`DispatchUncertain`) instead of offering Keep | Composer keep-move cluster + dialog; E2E-11 step 6 |
| Certificate-pin UI | B-SEC-153/154 | Engine-level tests only | Pin management UI; E2E-04 |
| Out of current slices by scope | B-NAV-060 (scenes, `after-switch` D11), B-L10N-018 (`:feature:settings`), B-NAV-006 (slice 5), B-AGENT-241 (slice 5) | — | None now; listed so nobody re-audits them as gaps |

## 3. Conversations page (`ChatsRoute.Home`, `ChatsRoute.All`)

| Cluster | Catalog rows (Flutter tests) | Native today | Still needed |
|---|---|---|---|
| Merged feed sort/filters/brief/titles | B-NAV-030/031, B-CHAT-012/013/014, B-CHAT-093/094/095, B-STATE-029/082/083, B-NAV-066/067 (`chat_feed_test.dart`, `merged_chat_feed_test.dart`, `connection_attention_feed_test.dart`, …) | Wired; `:engine:agents` feed tests + 23 `:feature:chats` tests cite the rows | Explicit two-server E2E-21 device run (sort/titles/status), not just the repair journey |
| Pins | B-NAV-065 | **Absent** (no key, action, or sort) | Whole cluster: `oc.<what>.<profileId>` keys, sort, row action, teaching tip (hidden gesture) |
| Elsewhere (other projects) | B-NAV-010 (`conversations_elsewhere_test.dart`) | Absent; `acrossProjects` capability flag is already plumbed into `ListingState` | Screen + filter, gated on `globalSessionSearch` |
| All-conversations browsing/pagination | B-STATE-103–107 | `ChatsAllScreen` is a stub; `PreviewRuntime.refreshOne` fetches **only page 1** and records `nextCursor == null` as completeness but never fetches page two | The proposed next slice (§6); tests from the B-STATE-106 subset |
| Read-state receipts | B-STATE-115/116, B-SEC-163 | `ConnectionReadState` is in-memory only (`engine/connection/.../ConnectionReadState.kt`) | Persistence + watermark retry cluster |
| Model fallback / offline metadata | B-STATE-117/118 | Absent (no model chip on the basic composer) | Blocked on the full-composer routing decision |
| Chooser/ordering/entry filters | B-NAV-011/025/032, B-CHAT-020, B-PROTO-096 | `:engine:entry` tests cite B-NAV-025 only; New-conversation goes straight to server setup (OC2-only preview) | Needs multi-backend support — later slices |
| Folder facts line | B-L10N-003 | Absent | Words + state + Arabic plurals |

## 4. Chat + Composer page (`ChatRoute`)

| Cluster | Catalog rows (Flutter tests) | Native today | Still needed |
|---|---|---|---|
| Text drafts | B-STATE-100/101/102 (`session_draft_test.dart`; frozen copy in `reference/flutter/test/session_draft_test.dart`) | Wired (BasicComposerHost + ConversationDrafts); the 287 focused checks live here | Those checks stay slice-scoped; the inline save-failure notice/retry and save-on-leave confirmation coexist; the native confirmation lacks a dedicated Copy action; E2E-11 step 1's attachment half unqualified |
| Composer attachments | B-STATE-016/017/018, B-CHAT-009/010, B-STATE-161/162 (`draft_attachments_test.dart`, `chat_attachment_reader_test.dart`) | Engine-only: vault tests exist; `:engine:media` (intake) is not on the app classpath; the basic composer sends text only and the facade preserves/refuses | Attachment UX + E2E-12 |
| Stash / saved prompts | B-CHAT-088, B-STATE-134–141, B-STATE-085/086 (`stash_attachments_test.dart`) | Engine-only: StashTest cites 136/138/140/141 and 085/086; no stash UI; saved-prompts UI is absent from current production `:feature:settings` | Tests for 134/135/137/139 + all UI + E2E-11 steps 3–4 |
| Phrase chips + moments | B-CHAT-077–086, B-STATE-059 (`phrase_chips_test.dart`) | Engine-only (PhraseChips/PhraseMoment/Starters tests) | Learned phrase-chip integration remains separate from full-composer model/approval/Stop chips; then device proof |
| Full composer | B-CHAT-089, B-CHAT-087, B-CHAT-006, B-KIT-057, B-AGENT-069 | **Built but not routed**: `:feature:chat-composer` has 45 tests + goldens; `FullComposerHost` is an object, "not yet contributed to Metro" (`FullComposerHost.kt:26`); `BasicComposerHost` holds the binding | Contract/adapters/ViewModel-factory integration, app dependency and binding changes; then affected chat-host tests |
| Streaming/stop/steer/retry | B-CHAT-056/060/062/064, B-CHAT-016, B-CHAT-071/072, B-SEC-064/094, B-CHAT-123, B-PROTO-044/046 | Wired: kit-chat renderer tests + `ReplyStreamFrameBenchmark` (5,439 frames/shape, JVM p95 3.82 ms) | Android frame-budget pass; retry-banner countdown copy (B-CHAT-016) on device; full visual/golden phase |
| In-conversation search | B-CHAT-115 | Wired loaded-transcript search; ChatScreen find bar and ChatPresenter/ChatRules search are present | Engine-handle optimization remains planned; full E2E-05 search/stream/prepend/delete device proof is owed |
| Offline queue / reconnect | B-PROTO-088, B-STATE-008/038/039/040, B-PROTO-045 | Delivery engine wired for sends (78 tests); network-switch matrix not run on the preview | E2E-10 full matrix |
| Photo recovery / picker | B-STATE-088, B-MEDIA-007/008 | Engine tests only; deferred by owner decision (M10) | Deferred; not a current gap |

## 5. Device-flow status for these pages

| Flow | Title | Status in this snapshot |
|---|---|---|
| E2E-01 | First launch/welcome/first server | Not applicable: `:feature:onboarding` unrouted; preview always starts on Conversations |
| E2E-03 | Connection failures in plain words | Open; blocked on the in-flight server-errors slice, then FX-TLS emulator matrix |
| E2E-04 | Transport trust/pins | Open; consent half proven once on emulator (SERVERS-PREVIEW); pins unproven |
| E2E-05 | Send/stream/stop/search/links | Partially exercised informally (repair journey, `NATIVE_APP_OK`); not run as the flow; loaded-transcript search is wired; full search-flow evidence is owed |
| E2E-10 | Network loss/reconnect/never twice | Engine-tested only; no device matrix |
| E2E-11 | Drafts/saved prompts/queued prompts survive | Text-draft steps verified once on emulator (drafts report); attachments/stash/keep steps unqualified; flow unchanged |
| E2E-12 | Attach photos/files/mentions | Unqualified |
| E2E-21 | Conversations list merged/pins/titles | Not run as a flow; sort/titles have engine+UI tests only |
| E2E-35 | Remove a server and everything stored | Exercised once on emulator (repair report: secret entry 1→0, history retained); not run as the flow with pins/phrases/trust pins present |

## 6. Proposed next small usable slice (after the phone connection is diagnosed)

**"Conversations, page two"** — the smallest user-visible step that closes a named preview limit
("full history browsing/search pagination … not delivered", REPAIR-INTEGRATION §Preview limits)
using only existing callable code:

- Callable today: `Oc2Gateway.sessionPage(cursor, limit)` returns `ServerPage(items, nextCursor)`
  (`protocol/oc2/.../Oc2Gateway.kt:281`); `ChatFeedSource.ListingState` already carries
  `complete/failed/loading` (`engine/agents/.../ChatFeedSource.kt`); `PreviewRuntime.refreshOne`
  already computes `complete = nextCursor == null` (`PreviewRuntime.kt:214`); `ChatsRoute.All`
  already round-trips in `RouteSavingTest`.
- Work: a `loadOlder` path that follows the cursor (engine/agents + a `refresh` thunk through
  `PreviewRuntime`), `ChatsAllScreen` becomes a real paged list with a Show-older action and
  per-source failure retry, tests first from the B-STATE-106 subset (partial refresh keeps cached
  rows; transient failure retries the same cursor).
- Ownership cautions: `PreviewRuntime` is androidApp wiring (coordinate with the server-errors
  builders' staging annotations before touching it); `:feature:chats` and `:engine:agents` are
  single-owner modules — propose, don't edit. The kit-only and plain-words rules apply to the new
  rows/action.

Deliberately **not** proposed now: routing `FullComposerHost` (contract/adapters/factory integration plus dependency and binding changes — a coordinator merge decision), pins (new storage + teaching tip), and read-state
persistence (new keys + consent review). Each is listed below as an independent step.

## 7. Independent remaining steps (no order implied between rows)

1. **Land the server-errors contract slice** (other snapshots): stage/kind mapping, localized copy,
   KitErrorNotice details; adds targeted B-PROTO-095 and B-SEC-014/130/133 coverage on the Servers page. Then rerun the emulator connect journey with deliberate failure
   injections (wrong password, refused port, TLS variant) — this is also the diagnosis output the
   phone-only failure needs.
2. **Conversations page-two slice** (§6).
3. **Conversations pins** (B-NAV-065): profile-scoped keys, sort group, row action, teaching tip,
   deletion sweep registration.
4. **Full-composer integration** (plan 09 §6.3): adapt contracts and ViewModel factory, bring `:feature:chat-composer` onto the app classpath, change binding, and rerun chat-host tests. Learned phrase chips remain a separate integration.
5. **Read-state persistence** (B-STATE-115/116, B-SEC-163) on top of `ConnectionReadState`.
6. **Attachment UX** (E2E-12 + draft attachments) once the composer routing is settled; requires
   `:engine:media` on the classpath.
7. **Stash/saved-prompts UI + missing engine rows** (B-STATE-134/135/137/139) and the Saved-prompts
   screen.
8. **Probe stage** (B-PROTO-094/095) so connect failures can say "not an OpenCode server" before a
   gateway is opened — after the server-errors slice, not before.
9. **Device-flow runs** as flows: E2E-21 and E2E-35 with the FX-OC2 pair on the emulator, then the
   E2E-05 loop once the search device flow is qualified.
10. **Visual phase**: golden/screenshot rows for the three pages (catalog `screenshot`-level rows)
    remain unqualified as a full same-page comparison; source goldens and bounded checks do exist. Schedule complete qualification after structural slices settle.

## 8. Method, verification and limitations

- Evidence sources: `docs/design/native-rewrite-2026-11-01/catalog/{BEHAVIORS.md,behaviors.jsonl,
  e2e-flows.md}` (Flutter-derived; each row's `sources` name the frozen Flutter test files),
  `native/catalog/scope.jsonl` (module/slice/decision mapping), read-only inspection of
  `native/feature/*`, `native/engine/*`, `native/ui/*`, `native/androidApp` at parent native commit `7ff73263` (private worker baseline `672e599`), and the
  prior reports (`units/REPAIR-INTEGRATION-2026-11-02.md`, `units/SERVERS-PREVIEW-2026-11-02.md`,
  `drafts-2026-11-02/verification.md`).
- The Flutter suite itself is not in this snapshot (only `reference/flutter/test/session_draft_test.dart`
  is); Flutter test-file names here come from the catalog's `sources` fields, which plan 02 keeps
  authoritative until each row's native test replaces them.
- Static `@Test` counts (not executions): feature/servers 7, feature/chats 23, feature/chat 68,
  feature/chat-composer 45, engine/connection 5, engine/composer 143, engine/profiles 59,
  engine/agents feed 3 test files, engine/delivery 78, engine/requests 90, engine/transcript 27.
- Catalog-id citations were located by scanning test sources for `B-…` tokens; a row cited only in
  production-code comments (e.g. B-CHAT-115 in `ChatRules.kt`) is *not* counted as a test.
- Not done here, by instruction: no implementation, no catalog edits, no test runs, no builds, no
  device or live-server operations, no credential inspection. Nothing here qualifies any APK.

## Coordinator review corrections

This is a source inventory, not an exhaustive parity count or qualification. Independent review corrected the GLM draft: B-CHAT-115 search is already wired (ChatScreen206–215/333–386, ChatPresenter103–108, ChatRules97–112); its catalog sources are test/transcript_search_test.dart184/223. Draft inline notice and Back confirmation coexist, with a narrower missing Copy action. Per-conversation older-message loading already passes cursors (GraphChatWorld491–504/532–535), whereas all-conversations pagination is absent. The full composer needs adapters/factory/dependency integration, not just a binding swap; its current chips do not implement learned phrase chips. A saved-prompts production screen was not found. Source/test method counts in this inventory remain static inventory, not pass receipts.
