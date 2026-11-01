# Linux vitals S2a: verification, 2026-11-01

Branch `feat/linux-vitals-2026-11-01`, commit `04611f7b9` (APK 4373).

## What is read, and from where
| Reading | Source (patchable) | Shown as |
|---|---|---|
| Heat | `oc/thermal` `current` (PowerManager thermal status) | Cool / Warm / Hot / Too hot. Left out before Android 10. |
| Phone memory free | `MemAvailable` from the live `/proc/meminfo`, read by the app on Android's side (not the frozen stand-ins inside Ubuntu) | "2.1 GB" over "Memory free" |
| Linux CPU | native `processes` (about 1 s measured), every process but the app's own, per-core % ÷ cores | "12%" over "Linux CPU" |
| Linux memory | the same list, summed RSS | in the sentence |
| Battery level, charging, temperature | needs BatteryManager: **S2b (release)** | not shown, never faked |

**Sampling:**
- every 5 s, and only while the Linux page is open and on top;
- one read at a time;
- it stops when the page goes.

## Tests (VPS)
| File | Result |
|---|---|
| `test/linux_vitals_test.dart` (sources, sentence, polling, page) | pass |
| `test/kit/kit_vitals_strip_test.dart` (cells, cap assert, loading, 1.3× frame cap, 2×2, width cap, semantics, tap) | pass |
| Kit gates: `kit_capacity_test`, capacity shards 0–7, `text_scale_overflow_test`, `kit_ratchet_test` | pass |
| `linux_manager_screens_test`, `linux_services_ui_test` | pass |
| `flutter analyze` on the changed files | no issues |

## Each test fails without its fix (mutations)
| Mutation | Test that then fails |
|---|---|
| A covered page still reads | a covered page skips its ticks |
| The page does not stop the watch | reads every 5 s while open, never once it is gone |
| The app's own processes count as Linux | Linux is every process but the app |
| A heat claim without a heat reading | no heat reading: no claim about heat |
| No 1.3× cap on frame text | frame text stops at 1.3x; the sentence does not |
| An unread value shown as 0 | an unread value is left out, not shown as 0 |
| Two reads at once | a slow read is never doubled |
| A 1 s period | every 5 s while started, never after stop |

## Kept from the plan, changed while building
- **No amber cell for heat.** Heat is a degraded state, not "needs you" (LOOK-4, gate G17); the word carries it.
- **At most four readings is an assert,** as the capacity contract needs.

## Device proof (emulator-5584, APK 4373, no debug hooks)
The live recording was edited with Remotion: `/home/eslam/Storage2/wt/shots-2026-10-31/linux-s2-device/vitals-demo.mp4` (1080×1920, 36 s, no sound). The raw recording `vit-1.mp4` is next to it.

| Step | Seen |
|---|---|
| Open Linux | "—" for one read, then Cool · 3.2 GB · 1% and "Linux uses 1.0 GB of memory. Nothing is heating the phone." |
| A service `burn` (`python3 -c "while True: pass"`) | Linux CPU rises to 17% (one core of the phone's six) |
| Android's heat raised with `cmd thermalservice override-status 3` (a simulation, said in the caption) | **Hot**, and the line "Hot. python3 is using 99% CPU." becomes a link |
| Tap the line | Running in Linux |
| `cmd thermalservice reset` | Cool again |

Afterwards `burn` was removed through the app and the thermal override was reset.

**Found, not fixed here:** Running in Linux lists the supervisor's processes as "oc-svd · Other". Grouping them under each service is S8.
