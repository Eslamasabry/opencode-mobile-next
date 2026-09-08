# Android UI and UX refinement

The primary journey is an interrupted, often one-handed developer returning to work: connect, resume or start a session, instruct the agent, understand progress, review a change or permission, and return later without losing context. This is an inferred working persona, not a claim of completed user research. Simplicity and fluidity govern the choices.

## Evidence and priority

The starting APK is the latest successful dev artifact at `395f374`: version `1.0.37+38`, SHA256 `9f7becb143b39920922bfc19278f36d2c72d27505ec912035ea59e6dc11795df`. It was installed and inspected through ADB. Findings retain page IDs, priority, dimension, observation, impact, change, source evidence, focused verification and implementation commit. See the [page JSON ledger](findings/) for the full record; tested there means the stated page-slice checks, not final Android certification.

The original screenshots, annotated pixel review, searchable index, motion captures and Mobbin reference images are in the durable sibling folder `oc_app-ui-audit-20260909`. Absolute paths in the archived findings identify that local evidence. Canonical external reference links are retained. Raw credentials are excluded.

## Implemented journeys

- [First run and connection](../../qa/simple-first-run/README.md): two clear starting actions, optional advanced setup, reachable save/connect action and useful recovery.
- [Workspace](../../qa/workspace-page-2026-09-09/README.md): consistent content rails, simpler empty state and one start action.
- [Chat and permissions](../../qa/page-reviews/pages/chat.md): permission review before response, preserved draft, quieter composition and guided demo.
- [Model selection](../../qa/simple-model/README.md): transactional choice, explicit apply scope, cancel preservation and truthful partial-save errors.
- [Files](../../qa/clear-files/README.md): quieter browser hierarchy, correct folder retry and deleted-file review access.
- [Activity](../../qa/clear-activity/README.md): unknown status remains unknown, requests lead and detail is progressive.
- [More and settings](../../qa/settings-discovery/README.md): consistent discovery, less repetition and readable current choices.
- [Frosted navigation](../../qa/frosted-navigation/README.md): real clipped content blur, readable foreground, safe list/action insets, and a solid accessibility fallback.
- [Shared typography, surfaces and Material contract](shared-visual-system.md): measured roles and readable supporting text.

The page captures were refreshed from the combined typography/icon candidate. Frosted navigation is integrated and focused checks passed. The final integration and Android gates are in progress. Screenshot dimensions and source/capture commands are recorded in each page evidence folder.

## Shipping boundary

Local integration and focused checks do not mean deployed or released. Final recursive tests, analyzer, native compilation and ADB proof must be recorded for the final candidate. The maintainer APK must use the existing stable CI certificate `2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC`. Push/signing/CI require the maintainer's explicit approval; no public release is implied.
