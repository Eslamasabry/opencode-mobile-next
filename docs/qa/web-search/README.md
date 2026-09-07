# Reviewed web search

Finish line: search-capable connections let the user explicitly query a provider,
review results, and return chosen public sources to an editable local draft.
Non-goal: automatic browsing, fetching selected URLs, or changing server history
before the user sends the draft.

The adapter uses the captured `contracts/opencode2-openapi-beta-18600.json`
`v2.websearch.providers` and `v2.websearch.query` contract, with the Basic header
and location scoping documented in `docs/opencode2-protocol-notes.md`. Discovery
returns registered `{id,name}` providers. Search sends `{query,providerID}` and
receives `{providerID,results}`. There is no cursor. These are server APIs; the
app does not obtain provider credentials or call provider accounts directly.
This is pinned-contract implementation evidence, not a new live-server proof.

Only the optional domain `WebSearchGateway` plus the `webSearch` capability
exposes search. Manual source entry remains available on other connections.
Multiple providers require an explicit choice. Empty/404/503 results show setup
guidance, 401/403 shows connection-auth guidance, and malformed responses show
fixed app copy. Remote error bodies never enter those messages.

Both responses must match the request's pinned location and still-current
client location. Scope change/profile deletion invalidates the entire review;
provider invalidation/reconnect refreshes discovery and supersedes pending
search responses without reissuing a query. Event bursts coalesce while provider
discovery is pending. Explicitly reviewed sources remain local untrusted text
when a provider disappears; no provider reroute occurs.

Queries are capped at 1,000 characters. Provider IDs/names and list sizes are
bounded; malformed or oversized protocol responses fail closed. Existing source
validation restricts review to 10 public HTTP(S) DNS links without URL credentials,
with 200-character titles, 2,048-character URLs and 2,000-character excerpts.
Unsafe/oversized sources are omitted with a visible notice. No favicons or remote
images are fetched. Link opening uses `openExternalLink`.

Confirmation returns ordinary editable prompt context through the existing chat
source flow. Browsing, searching and cancellation never call synthetic-history
or prompt endpoints. The existing draft save/restore/delete path owns returned
text; no new preference format or migration is introduced. Search queries and
unconfirmed review are transient and are not persisted.

The UI uses flat results and review rows, ordinary buttons/fields, visible host
labels, full selectable excerpts in review, and live-region error announcements.
Search and discovery have immediate progress indicators. The capture fixture
covers synthetic light/dark search and review screens; it makes no external calls.

Focused checks prepared for the root verification owner:

- `flutter test --concurrency=1 test/web_search_test.dart`
- `flutter test --concurrency=1 tool/capture/web_search_test.dart`

Workers do not run tests in parallel. Results and screenshots are evidence only
after the root runs these commands; this document does not claim a test pass.
