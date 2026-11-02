# STREAM-FIX integration — 2026-11-02

Integrated the final scoped diff of `recovered/streamfix` (`57d3b9fb285e5207d3b2f7cae76bbfe2bbe96d5d`, base `7db496b2`) onto the recovered repair candidate. W3 is not a prerequisite: the change deliberately targets the P3 splitter already present here, with no Markdown module or dependency changes.

The incremental newest-turn list/position cache, prompt-row cache, 16-line chunks, row flags and deterministic counters are carried forward. The current repair additions remain intact: version-fenced history adoption, notice admission, event/message versions, searchable reasoning/tool/file text and the five-field `TranscriptState` return. The feature's `ChatTranscript` forwards both chunk seam flags to `KitReplyBlock`.

The historical unit report `STREAM-FIX.md` describes the original branch's measurements. The combined candidate passed the focused engine/chunk checks on the VPS and the full 6,000-word UI frame workload on the PC. Fixture publication schedules are prepared before the UI harness: every original frame, character, publication, counter and assertion remains; no timeout was increased. The PC's first combined run measured whole p95 13.81 ms versus chunked p95 8.11 ms; final candidate JSON is archived with qualification evidence. VPS software rendering exceeded the unchanged one-minute harness watchdog, so this benchmark and the existing P3 live-edge renderer are explicitly assigned to the PC while the VPS runs the remaining fresh suite. The final PC run measured chunked p95 3.82 ms, max 448 laid-out characters per frame, with all 5,439 frames in each shape retained.

Focused VPS checks:

```bash
./gradlew --no-daemon -Pkotlin.incremental=false --max-workers=2 \
  :engine:transcript:desktopTest --tests '*StreamCostTest*' \
  :ui:kit-chat:desktopTest --tests '*ChunkSeamTest*' \
  :engine:transcript:compileAndroidMain :ui:kit-chat:compileAndroidMain \
  :feature:chat:compileKotlinDesktop
./gradlew :quality:test --tests '*TranscriptStreamUiTest*'
```

Then run the complete transcript and kit-chat suites, including the included JVM benchmarks. Device Macrobenchmarks and physical-device qualification remain unrun. No timeout or performance allowance was relaxed. Search-text extraction is preserved and still scans relevant entries; bounded row-write/layout counters are not a claim that every part of transcript derivation is constant work.

## Search-cost boundary

The retained correctness repair builds searchable reasoning/tool/file metadata in `derive()`. That still scans all message parts and copies searchable text, with additional row lookup for some files. The new row-write/tail-character counters do not measure that work. The existing stream-cost fixture and P3 fixture are text-only; their result must not be generalized to large completed tool outputs or growing reasoning. Incremental search-index caching remains a separate follow-up. Search correctness was not removed to improve a benchmark.
