# Native screens upgrade

Finish line: Start preserves the prompt and model through a compact creation flow into a calm, native chat with a reversible row transition.
Non-goal: release, signing, publication, live-server qualification, and unrelated settings redesign.

Basis: the full DESIGN-REVIEW.md and all PNG sets in native-qa-2561012c5 were read/opened. Flutter reference is read-only.
The review's NotificationText Android regex crash is already fixed in this branch's baseline.

## U1 — instant first chat

Start swaps immediately to a kit chat frame with the exact prompt and Sending… while creation/delivery runs. Definite failures offer retry on the same attempt; uncertain creation/delivery never replays. The prompt, project and model/agent choices are persisted before creation; a scoped journal fences process-death retries. A created conversation takes its initial title from the first prompt.

Validation: source inspection, formatter parsing and diff whitespace checks only. Compilation, tests and device/screenshot qualification are intentionally reserved for the coordinator. No push, merge, signing or release authorized.

## U2 — short creation screen

Prompt first, followed by one sideways-scrolling row of agent/model/project chips; compact agent rows replace tiles and Add an agent is last. Separate copy and import live in the project sheet. Actual server location/project and actual model defaults prefill the choices. Start becomes available with text when server setup is ready. Static inspection and formatter only; no compile/tests.

## U3 — agents and modes

Only primary/all-mode user agents appear; Build and Plan are a separate, server-backed mode chip. Background helpers are excluded at both adapter and chooser/action boundaries, including stale saved helper selections. Static inspection and formatter only; no compile/tests.

## U4 — one quiet notice

Chat retains one priority status slot, hidden while the keyboard is open. The duplicate notification ask and Free model banner are removed. Catalog-backed free-model state is handed to the composer as a chip tag independent of banner dismissal (composer consumer lands in U9). Source/format inspection only; no compile/tests.

## U5 — server names

Pairing hostname or address DNS name supplies the default; IP-only connections honestly say Computer until renamed. Existing IP-derived names normalize on read. The display-name field is visible and custom names are retained. Current health schema has no hostname field; no endpoint is invented. Added naming/pairing test cases as source only; not run. No compile/tests.
