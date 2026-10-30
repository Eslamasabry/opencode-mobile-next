# JSON decode off the UI isolate: measurements (2026-10-28)

UI audit backlog item: "JSON decode off the UI isolate (measure first)".
The question was whether decoding large server responses on the UI isolate
drops frames when a big chat or list opens.

**Answer: yes for Paseo (Claude Code / Pi) histories, no for OpenCode 2.**
Paseo now decodes long frames and maps long timelines on a background isolate.
OpenCode 2 is unchanged.

## Hot paths

| Path | Where | Payload | Decoded on |
|---|---|---|---|
| OpenCode 2 messages page | `lib/api2/client.dart` `messages()` → `Api2Page.fromJson` → `mapApi2Messages` (`lib/api2/gateway.dart`) | `GET /session/{id}/message?limit=100`: 0.3–2.5 MB for a page of 100 messages with tool outputs | **Already off the UI isolate.** Dio 5.11's default `FusedTransformer(contentLengthIsolateThreshold: 50 KB)` decodes any JSON body of 50 KB or more in `compute`. Only the model mapping runs on the UI isolate. |
| OpenCode 2 session list | `client.sessions()` → `mapApi2Session` | about 94 KB for 100 sessions | Same as above: Dio does the decode, mapping is inline |
| OpenCode 2 events | `lib/api2/sse2.dart` `jsonDecode(frame.data)` | one event per frame, usually under a few KB | UI isolate (small) |
| **Paseo full timeline** | `lib/paseo/gateway.dart` `messages()`: `fetch_agent_timeline_request` with `limit: 0` (the whole history) → `paseoTimelineMessages` | **one WebSocket text frame of up to 8 MB** (`maxFrameBytes`) | **UI isolate**: `jsonDecode(frame)` in `PaseoTransport._receive`, then mapping in the gateway |
| Paseo agent list | `fetch_agents_request` (up to 200 rows) → `paseoSession` | about 340 KB at 200 rows | UI isolate, same `_receive` |
| Disk caches | `session_inventory_cache`, `session_tail_cache` | capped at 64 KB | UI isolate (small) |

The OpenCode 1 client (`lib/api/`) has been removed (40ba4fa32), so it has no path left to measure.

## Benchmark

`test/perf/json_decode_bench.dart` is not part of the suite, because the file name has no `_test` suffix. Run it with:

```bash
TZ=UTC flutter test test/perf/json_decode_bench.dart
```

The payloads reuse the real wire shapes from `test/fixtures/coverage`:

- OpenCode 2: the `tool_bash`, `tool_read`, `tool_edit`, `tool_grep`, `tool_glob` and `tool_write` messages, plus the assistant and prompt samples, in turns of one prompt and 8 assistant steps.
- Paseo: prompt, reasoning, shell, read, edit and search steps, and the answer, as the projected timeline sends them.
- Lists: the session and agent samples.

The tool outputs are synthetic code and log text with newlines, quotes, escapes and Arabic, inflated to big-chat sizes. Each row is the median and the worst of 10 runs after 3 warm-up runs.

Test machine: Intel i7-9700 at 3.0 GHz, pinned Flutter 3.47.1, `flutter test` VM (JIT, asserts on).

### Decode and mapping, PC

| Path | Payload | Items | Decode med / max ms | Map med / max ms | Total med ms | Phone ×4 |
|---|---|---|---|---|---|---|
| OC2 messages page | 291 KB | 100 | 2.9 / 3.5 *(Dio isolate)* | 2.2 / 3.0 | 5.1 | map ≈ 9 ms |
| OC2 messages page | 904 KB | 100 | 7.1 / 8.1 *(Dio isolate)* | 1.9 / 3.0 | 9.1 | map ≈ 8 ms |
| OC2 messages page | 2.5 MB | 100 | 21.6 / 25.7 *(Dio isolate)* | 1.7 / 2.5 | 23.4 | map ≈ 7 ms |
| **Paseo full timeline** | 2.1 MB | 651 | 20.9 / 25.8 | 14.5 / 19.1 | **35.4** | **≈ 140 ms** |
| **Paseo full timeline** | 4.1 MB | 1,295 | 44.0 / 54.0 | 23.3 / 24.8 | **67.3** | **≈ 270 ms** |
| **Paseo full timeline** | 8.2 MB | 2,583 | 85.7 / 90.1 | 48.3 / 51.7 | **134.0** | **≈ 535 ms** |
| OC2 session list | 94 KB | 100 | 0.6 / 1.9 *(Dio isolate)* | 0.5 / 0.5 | 1.1 | map ≈ 2 ms |
| Paseo agent list | 338 KB | 200 | 4.0 / 6.3 | 0.9 / 1.9 | 4.9 | ≈ 20 ms |

### Isolate cost, PC

| Measure | Med ms | Max ms |
|---|---|---|
| `Isolate.run` round trip, 60 B | 0.1 | 0.2 |
| inline decode, 6.4 KB | 0.0 | 0.0 |
| `Isolate.run` decode, 6.4 KB (wall time) | 1.3 | 3.8 |
| longest UI-isolate stall, 8 MB decode inline | 83.2 | 90.0 |
| longest UI-isolate stall, 8 MB decode in `Isolate.run` | 2.2 | 5.3 |
| longest UI-isolate stall, 8 MB decode + map inline | 133.0 | 137.5 |
| longest UI-isolate stall, 8 MB decode + map in one `Isolate.run` | 2.6 | 4.6 |
| longest UI-isolate stall, map only, decoded 8 MB copied into `Isolate.run` | 4.9 | 9.9 |
| wall time, 8 MB decode in `Isolate.run` (the UI stays free) | 91.5 | 95.6 |

"Longest stall" is the largest gap between two turns of the UI isolate's event loop while the work runs. It is the time a frame would have waited.

### Phone factor: ×4

A mid-range phone's big core runs single-threaded Dart about 1.7–2× slower than this i7-9700. The UI thread is not always on a big core, it throttles when hot, and AOT code has no JIT warm-up. I used **×4**, the top of the 3–4× range. That is the pessimistic choice: it can only make the "keep inline" verdicts below look worse. The Paseo verdict does not depend on the factor, because 134 ms on the PC alone already covers 8 frames.

## Decision

The budget is 16.7 ms per frame, and the threshold was more than about 8 ms on the UI isolate for a realistic big chat.

- **Paseo full timeline: over budget at every size. Fixed.** A 2 MB history froze the UI for about 140 ms on a phone, and an 8 MB one for about half a second.
  - `PaseoTransport` decodes text frames of `backgroundDecodeChars` (64 KB) or more in `Isolate.run`. Frames that arrive while one is decoding, and the socket's close, wait in an inbox, so they are still handled in arrival order. Malformed JSON still drops the connection. If no isolate can run, the frame is decoded inline.
  - `paseoTimelineReply` maps timelines of `paseoBackgroundTimelineEntries` (100) entries or more in `Isolate.run`. If the payload fails to map, it is mapped again inline, so the failure is the same `PaseoFailure` that `paseoReply` reported before.
  - The gateway runs the mapping and the hydration inside `PaseoTransport.holdingMessages`, so stream events that arrive meanwhile are handled after the history is in place, as they were when mapping was inline.
  - The worst UI stall for an 8 MB history drops from 133 ms to about 2–5 ms on the PC (about 10–20 ms on a phone).
- **Thresholds.** Inline decode costs about 10.5 µs per KB on the PC. At 64 KB that is about 0.7 ms, or about 3 ms on a phone. That is about what an isolate hop costs: 1.3 ms median and 3.8 ms worst for its wall time. Smaller frames therefore stay inline. Mapping costs about 19 µs per entry, so 100 entries is about 1.9 ms, or about 7.5 ms on a phone. Shorter timelines stay inline.
- **Paseo agent list (200 rows, 338 KB):** about 20 ms on a phone. It is above 64 KB, so the transport change now decodes it in the background too. Mapping it costs about 4 ms on a phone and stays inline.
- **OpenCode 2: no change.** Dio already decodes every body of 50 KB or more off the UI isolate. What stays inline is mapping a page of 100 messages: 1.7–2.2 ms median, about 7–9 ms at ×4. That is at the threshold, not clearly over it. Moving it would cost a copy into the isolate of similar size, plus a hop, for no clear gain.
- **Small payloads** (events, caches of 64 KB or less, sessions) stay inline.

## Not changed (follow-ups if a phone trace shows them)

- Sub-agent timelines (`agent.provider_subagents.timeline.get`) and the bounded gen-UI history (frames of 1 MB or less) are mapped inline. Their frames of 64 KB or more are already decoded in the background by the transport change.
- A Paseo history larger than 8 MB is still refused by the frame cap, as before.

## Verification

- `test/paseo_background_decode_test.dart` checks these against the inline path:
  - A 240-entry timeline mapped in the background gives the same messages and tool calls as inline.
  - A timeline that does not map fails with the same `PaseoFailure` kind and reason.
  - A long answer comes back with the same payload, and long and short frames are handled in arrival order.
  - A long malformed frame drops the connection.
  - Messages wait while `holdingMessages` runs.
  - `PaseoGateway.messages` opens a long history with the same messages.
- I temporarily added logging to confirm that these tests take the isolate paths, not the inline fallback, and then removed it.
- 63 affected test files (every test that imports the Paseo transport, gateway, mappers or the `FakeDaemon`), 970 tests: all passed (`flutter test -j 3`).
- `flutter analyze`: no issues.
