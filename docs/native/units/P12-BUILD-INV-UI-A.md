VERIFIED: green

# P12-BUILD-INV-UI-A — file inventory, lib/ui part A (analysis only)

Unit: INVENTORY-UI-A. Branch `claude/file-inventory-ui-a-parity-cva7u4`, based on `native/p12-integrate-2026-11-03` at `71f74de8`.
No product code changed; only `docs/native/inventory/ui-a.jsonl`, `docs/native/inventory/ui-a.md` and this report were added.

## What "green" means for this unit

- `docs/native/inventory/ui-a.jsonl` has **248 lines, one per listed file** (the brief's 247 count is off by one; every
  listed path is covered). Every line parses as JSON, uses the allowed `status` / `kind` / `portability` values, and
  every native path it names exists in the tree (checked by script). Every `catalog_rows` id exists in
  `catalog/behaviors.jsonl`.
- `lib/ui/kit/chat/kit_mic_hold_detector.dart` never existed in this repo (no git history). The real file is
  `lib/ui/kit/chat/kit_mic_hold.dart`; its line carries `"listed_as": "lib/ui/kit/chat/kit_mic_hold_detector.dart"`.
- Gradle builds and Flutter tests were not run, and the Android SDK was not installed: this unit changes no code, so
  there is nothing to compile. The app's build state is unchanged from the base commit.

## Result

| Status | Files |
|---|---:|
| partial | 110 |
| missing | 76 |
| after-switch | 33 |
| done | 21 |
| not-needed | 8 |

Details, top-20 gap list and the uncatalogued gaps: [`../inventory/ui-a.md`](../inventory/ui-a.md).

Headlines for the integrator:
- Only 21 of 248 files are fully matched natively. Most native kit parts exist in a reduced form (110 partial),
  and some exist but are not yet used by any screen (`KitFrost`, `KitNavRail`, `KitSidebar`, composer glow/edge/classic
  parts, `KitLiveSweep`).
- Biggest fully missing parts: `KitDateTimePicker`, `KitLogPanel`, `KitOptionCard`, `KitSwatch`,
  `KitCapabilityExplainer`, the step-timeline node/light/signal parts, `KitImageGallery`/`KitImageMarkup`, `KitGuide`,
  `KitSessionTabs`, `KitSkeletonTranscript`, `KitSurface` + style resolver.
- 33 files are after-switch: desktop (`lib/ui/desktop/*`, desktop-only kit parts), AI Team/FactsBook (plan 01 D6),
  and every live-background style (plan 01 D11; scope rows B-KIT-020, B-KIT-047).
- 360 catalog row ids trace to these files; **152 files have no catalog row**
  and 27 have no Flutter test at all, so their behaviour is invisible to catalog-based parity checks.
  The uncatalogued gaps are listed per file in `ui-a.md`.

## Catalog row ids covered by this inventory

B-AGENT-012, B-AGENT-013, B-AGENT-020, B-AGENT-021, B-AGENT-023, B-AGENT-030, B-AGENT-055, B-AGENT-056, B-AGENT-057, B-AGENT-058, B-AGENT-059, B-AGENT-108, B-AGENT-109, B-AGENT-118, B-AGENT-204, B-AGENT-208, B-AGENT-210, B-AGENT-211, B-AGENT-212, B-AGENT-213, B-AGENT-220, B-AGENT-232, B-AGENT-241, B-AGENT-242, B-AGENT-245, B-AGENT-248, B-AGENT-260, B-AGENT-261, B-AGENT-262, B-AGENT-263, B-AGENT-267, B-AGENT-278, B-AGENT-279, B-AGENT-280, B-AGENT-286, B-AGENT-292, B-AGENT-293, B-AGENT-294, B-AGENT-296, B-AGENT-297, B-AGENT-298, B-AGENT-304, B-AGENT-308, B-AGENT-310, B-AGENT-311, B-ANDR-004, B-ANDR-005, B-ANDR-006, B-ANDR-007, B-ANDR-008, B-ANDR-018, B-ANDR-068, B-ANDR-069, B-ANDR-108, B-ANDR-109, B-ANDR-111, B-ANDR-112, B-ANDR-113, B-ANDR-114, B-ANDR-115, B-ANDR-116, B-ANDR-117, B-BUILD-003, B-BUILD-004, B-BUILD-006, B-BUILD-010, B-BUILD-011, B-BUILD-012, B-BUILD-023, B-BUILD-024, B-CHAT-004, B-CHAT-006, B-CHAT-007, B-CHAT-011, B-CHAT-015, B-CHAT-020, B-CHAT-054, B-CHAT-055, B-CHAT-056, B-CHAT-057, B-CHAT-058, B-CHAT-059, B-CHAT-060, B-CHAT-061, B-CHAT-062, B-CHAT-063, B-CHAT-064, B-CHAT-065, B-CHAT-068, B-CHAT-073, B-CHAT-088, B-CHAT-089, B-CHAT-096, B-CHAT-097, B-CHAT-098, B-CHAT-099, B-CHAT-102, B-CHAT-105, B-CHAT-106, B-CHAT-108, B-CHAT-109, B-CHAT-110, B-CHAT-111, B-CHAT-115, B-CHAT-121, B-CHAT-124, B-KIT-001, B-KIT-003, B-KIT-004, B-KIT-005, B-KIT-006, B-KIT-007, B-KIT-008, B-KIT-009, B-KIT-010, B-KIT-011, B-KIT-012, B-KIT-013, B-KIT-014, B-KIT-015, B-KIT-016, B-KIT-017, B-KIT-018, B-KIT-019, B-KIT-020, B-KIT-021, B-KIT-022, B-KIT-023, B-KIT-024, B-KIT-025, B-KIT-026, B-KIT-027, B-KIT-028, B-KIT-029, B-KIT-030, B-KIT-031, B-KIT-032, B-KIT-033, B-KIT-034, B-KIT-035, B-KIT-036, B-KIT-037, B-KIT-038, B-KIT-039, B-KIT-040, B-KIT-041, B-KIT-042, B-KIT-043, B-KIT-044, B-KIT-045, B-KIT-046, B-KIT-047, B-KIT-048, B-KIT-049, B-KIT-050, B-KIT-051, B-KIT-052, B-KIT-053, B-KIT-054, B-KIT-055, B-KIT-057, B-KIT-058, B-KIT-059, B-KIT-060, B-KIT-061, B-KIT-062, B-L10N-006, B-L10N-017, B-L10N-018, B-L10N-020, B-L10N-024, B-LINUX-010, B-LINUX-011, B-LINUX-012, B-LINUX-013, B-LINUX-039, B-LINUX-040, B-LINUX-065, B-LINUX-081, B-LINUX-103, B-LINUX-157, B-LINUX-179, B-LINUX-180, B-LINUX-217, B-LINUX-218, B-LINUX-228, B-LINUX-238, B-LINUX-240, B-LINUX-246, B-LINUX-247, B-LINUX-248, B-LINUX-250, B-MEDIA-001, B-MEDIA-002, B-MEDIA-003, B-MEDIA-004, B-MEDIA-010, B-MEDIA-015, B-MEDIA-016, B-MEDIA-017, B-NAV-001, B-NAV-002, B-NAV-003, B-NAV-004, B-NAV-012, B-NAV-015, B-NAV-016, B-NAV-017, B-NAV-018, B-NAV-023, B-NAV-024, B-NAV-025, B-NAV-026, B-NAV-027, B-NAV-028, B-NAV-029, B-NAV-034, B-NAV-037, B-NAV-038, B-NAV-040, B-NAV-041, B-NAV-042, B-NAV-044, B-NAV-045, B-NAV-046, B-NAV-047, B-NAV-048, B-NAV-049, B-NAV-051, B-NAV-052, B-NAV-053, B-NAV-055, B-NAV-056, B-NAV-057, B-NAV-058, B-NAV-059, B-NAV-060, B-NAV-062, B-NAV-068, B-NAV-069, B-NAV-070, B-NAV-071, B-NAV-072, B-NAV-073, B-NAV-074, B-NAV-075, B-NAV-080, B-NAV-081, B-PROTO-049, B-PROTO-075, B-PROTO-076, B-PROTO-077, B-PROTO-096, B-PROTO-097, B-PROTO-098, B-PROTO-099, B-PROTO-100, B-PROTO-101, B-PROTO-103, B-PROTO-104, B-PROTO-105, B-PROTO-106, B-PROTO-107, B-PROTO-124, B-PROTO-125, B-PROTO-126, B-SEC-003, B-SEC-004, B-SEC-008, B-SEC-014, B-SEC-015, B-SEC-016, B-SEC-017, B-SEC-018, B-SEC-039, B-SEC-041, B-SEC-055, B-SEC-056, B-SEC-079, B-SEC-084, B-SEC-085, B-SEC-094, B-SEC-095, B-SEC-102, B-SEC-118, B-SEC-126, B-SEC-127, B-SEC-128, B-SEC-129, B-SEC-130, B-SEC-131, B-SEC-132, B-SEC-133, B-SEC-144, B-SEC-145, B-SEC-146, B-SEC-147, B-SEC-148, B-SEC-149, B-SEC-151, B-SEC-156, B-SEC-157, B-SEC-161, B-SEC-162, B-SEC-167, B-SEC-168, B-SEC-169, B-SEC-170, B-SEC-173, B-SEC-174, B-SEC-176, B-SEC-181, B-SEC-182, B-STATE-001, B-STATE-002, B-STATE-005, B-STATE-006, B-STATE-007, B-STATE-014, B-STATE-021, B-STATE-022, B-STATE-023, B-STATE-024, B-STATE-042, B-STATE-043, B-STATE-044, B-STATE-064, B-STATE-066, B-STATE-069, B-STATE-070, B-STATE-071, B-STATE-072, B-STATE-073, B-STATE-074, B-STATE-075, B-STATE-076, B-STATE-077, B-STATE-078, B-STATE-079, B-STATE-080, B-STATE-085, B-STATE-086, B-STATE-087, B-STATE-088, B-STATE-100, B-STATE-101, B-STATE-102, B-STATE-108, B-STATE-109, B-STATE-110, B-STATE-113, B-STATE-120, B-STATE-121, B-STATE-122, B-STATE-133, B-STATE-142, B-XPLAT-008, B-XPLAT-009, B-XPLAT-010, B-XPLAT-011

## Deviations

- Analysis was split across five parallel read-only workers (about 50 files each) from one shared brief and a
  scripted pre-pass (test linkage, catalog rows); results were merged and validated by script.
- `untested` is a judgement: `part` files exercised through their library's tests count as tested.
- `gaps_uncatalogued` was matched against the catalog by reading, not mechanically.

## For the integrator

- Nothing to wire: no modules, routes or app wiring were added.
- Use `ui-a.jsonl` `status in (missing, partial)` with `portability == agent-portable` as the port queue; the
  `needs-design` lines (49) mostly wait on the native Style resolver (Soft/Brutal/Paper) and the composer layout.
