# Common-file preview verification

Candidate: `feature/common-file-previews`, based on integrated `05e69e6`. Implemented and locally Flutter-verified for a reviewable feature commit. Native compilation/device acceptance and integration remain coordinator-owned and pending. No build, ADB, signing or global Windows setting change was performed in this worktree.

## Completed focused ladder

Pinned Flutter 3.47.2 / Java 17; this worktree's own `build/traycer/env.ps1` and package configuration. Commands ran serially under the explicit lease. `flutter pub get` resolved the five new hosted packages and wrote package configuration/lockfile, then exited 1 for the host's Windows plugin symlink prerequisite. Subsequent checks used `--no-pub`; no global setting was changed. Localization was generated initially and once more for the review-required outside-preview line message. Changed Dart was formatted; analyzer style/import findings and the proof target's nullable generic inference were corrected without suppressions.

All test commands used `flutter test --no-pub --concurrency=1`. **108 distinct focused checks passed**, excluding repeated checks and capture cases; this is not a full repository/native gate.

| Test files / command suffix | Result |
| --- | --- |
| `test/delimited_text_test.dart test/static_svg_test.dart test/common_file_preview_test.dart test/local_pdf_test.dart` | 40 passed after review corrections: CSV bounds/literal data/original bytes, SVG gates, copy refusal/retry, focused truncation, PDF transport validation and held lifecycle/scope/disposal replies. The common-preview 10 were rerun after the final SVG direction wrapper. |
| `test/product_ui_regression_test.dart` | 27 passed; the four affected binary/image/CSV/symbol cases were rerun after the focused-source change. |
| `test/chat_live_events_test.dart --name 'generated CSV\|opens non-image tool files'` | 2 passed; generated CSV opens the shared table without sending a prompt. |
| `test/markdown_reading_test.dart test/markdown_agent_blocks_test.dart test/markdown_path_link_test.dart test/code_highlight_test.dart test/l10n_coverage_test.dart` | 39 passed; the two localization checks were rerun after final generated copy. |
| `flutter analyze --no-pub` | Clean on the final source, 21.3 seconds. Includes the separate Dart native proof entrypoint; does not compile Kotlin. |
| `tool/capture/common_file_previews_test.dart` | 12 capture cases passed; 28 PNGs. Four SVG cases were rerun after the RTL correction. |
| `git diff --check` and exact new dependency notices | Passed; bundled license files match the actual resolved package cache. |

Independent source review found three issues, now addressed: SVG dash expansion is rejected everywhere including inherited/style attributes; focused source announces truncation and does not highlight a clamped substitute line; inactive PDF mounts/replacements cannot restart rendering. Malicious dash input is tested at the rejection gate only. A real clipboard regression also showed a snackbar hidden beneath the modal; a sheet-owned feedback surface now makes Retry tappable. Visual review found ambient RTL could erase SVG text, so only the authored document viewport uses LTR; controls remain RTL.

## Captured views

Production `FilePreviewBody`, theme, localization and fonts render local fixtures. The table's second view follows an actual header drag with an asserted nonzero scroll offset. PDF pages are decoded before capture. SVG fixtures declare the loaded Roboto font rather than inheriting the widget-test box font. Initial capture-only gesture/font/decoding mistakes were corrected and affected evidence regenerated. Inspected narrow, light/dark and RTL results show growing rows, wrapped source controls, readable document text and intact page controls.

| Mode | CSV start / end / Source | SVG image / Source | PDF first / next (mock transport) |
| --- | --- | --- | --- |
| Light 390 px | [Start](light-csv.png) / [End](light-csv-end.png) / [Source](light-csv-source.png) | [Image](light-svg.png) / [Source](light-svg-source.png) | [First](light-pdf.png) / [Next](light-pdf-next.png) |
| Dark 390 px | [Start](dark-csv.png) / [End](dark-csv-end.png) / [Source](dark-csv-source.png) | [Image](dark-svg.png) / [Source](dark-svg-source.png) | [First](dark-pdf.png) / [Next](dark-pdf-next.png) |
| 320 px / 2x text | [Start](narrow-csv.png) / [End](narrow-csv-end.png) / [Source](narrow-csv-source.png) | [Image](narrow-svg.png) / [Source](narrow-svg-source.png) | [First](narrow-pdf.png) / [Next](narrow-pdf-next.png) |
| RTL 390 px | [Start](rtl-csv.png) / [End](rtl-csv-end.png) / [Source](rtl-csv-source.png) | [Image](rtl-svg.png) / [Source](rtl-svg-source.png) | [First](rtl-pdf.png) / [Next](rtl-pdf-next.png) |

PDF controls use an explicitly synthetic raster transport fixture: these are layout evidence, not Android PdfRenderer output. Native proof remains explicitly pending and does not hold the source commit open.

## Native PDF proof — separate explicit lease

`tool/native_pdf_proof.dart` is an alternate, explicit Android proof entrypoint. It connects only to the production `oc/local_pdf` bridge. It has no app account setup, provider calls, network, user documents or automatic actions. The coordinator owns the final same-signer Android quality build and fresh-device acceptance, and chooses the precise isolated proof mechanism under the pinned release-engine constraints. This source does not grant this branch owner signing or ADB permission.

The fixture generator creates a deterministic two-page PDF locally: one vector/text page and one 1,280-square high-entropy image. Press **Run normal proof** and require `PDF_NATIVE_PROOF` with `passed:true`, two pages, a second-page PNG larger than 1 MiB, successful cancellation, successful rendering immediately after cancellation and rejection of malformed input. The large PNG specifically exercises descriptor transport beyond a safe Binder byte-array response size. Keep the JSON and actual displayed first-page screenshot as evidence.

Press **Open service-death window** for up to 30 serial heavy-page requests. During the window, the native QA owner must observe the exact isolated renderer instance PID and terminate that PID only using an authorized emulator/debug mechanism. Record the PID/process ownership and operation alongside the report. Never kill the app, unrelated services or a process-name pattern. The fixture accepts only `service_died` as an injected interruption and must render a fresh first page afterward. A passing report without a recorded external termination is insufficient proof of the intended death case.

For cleanup, observe the owned isolated process before and after success, Cancel, route dismissal/backgrounding and injected death. Confirm no owned renderer survives the deadline, the app remains responsive, no preview temp pathname remains and a new render succeeds. Exercise actual production PDF Previous/Next, pinch zoom and Back; encrypted/malformed/over-limit files must leave the original Save action useful. Widget transport mocks are not substitutes for any native result.

Native proof results, encrypted-file/device checks, process/descriptor cleanup observations and final Android smoothness are **pending**. No completed verification or shipping claim is made by this plan.
