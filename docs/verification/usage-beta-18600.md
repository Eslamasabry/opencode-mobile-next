# Aggregate usage verification — beta-18600

Verified on 2026-09-05 against the isolated, authenticated Windows server.
Two fresh Git projects and imported transcripts supplied known usage; no model
or tool was executed. The helper was stopped afterward, preserving the fixtures.
See [captured results](usage-beta-18600.json).

The executable was rechecked against SHA-256
`29443AC011DC37896A650A7267C8FA232533B9410E8185EA099C1D5214E658E0`;
health reported healthy and `0.0.0-beta-18600` before the requests.

| Check | Observed result |
|---|---|
| Both projects in the selected time range | 2 sessions, 2 prompts, 2 steps, USD 1.75 |
| Explicit first-project ID | 1 session, one model, USD 1.25 |
| Tool summary | 3 calls: 1 succeeded, 1 failed, 1 unfinished |
| Same two steps grouped by UTC | 2 activity dates |
| Same two steps grouped by Asia/Dubai | 1 activity date |
| Empty time range | Zero sessions, steps and cost |
| Equal `from` and `to` | 400 |
| Unauthenticated statistics request | 401 |

The pinned route declares `from` and `to` as `NumberFromString`, and the service
compares message creation timestamps with `>= from` and `< to`. They are Unix
milliseconds, not ISO date strings. An omitted `from` starts at the earliest
matching user/assistant message. Project filtering uses the explicit project ID;
the client never supplies its pinned directory/workspace to this global endpoint.
The service uses `Intl.DateTimeFormat` with the requested timezone for daily
grouping. Its reported streak is the longest consecutive active-day streak.

## Client behavior and checks

Settings → Usage and cost offers Today, 30 days, This year and All time, plus
All projects and Current project. Today starts at local midnight; 30 days starts
29 calendar dates earlier; This year starts on January 1. All time omits `from`.
The upper bound includes the current millisecond. The app requests `tools=summary`
and computes success rate from succeeded/failed calls, excluding unfinished calls.

The [flutter_timezone 5.1.0 API](https://pub.dev/packages/flutter_timezone/versions/5.1.0)
provides the device timezone identifier. Its resolved source, desktop registration
and Apache-2.0 license were inspected. Missing/unknown timezone results remain a
recoverable error; the app does not silently report UTC as the device timezone.
Calendar boundaries use the device's local `DateTime` constructors rather than
subtracting multiples of 24 hours. Named zones preserve historical DST grouping.

Focused checks cover response parsing, malformed data, all four ranges, global
and project wire queries, native timezone responses, capability/auth failures,
superseded requests, location changes during wake, stale refresh labeling,
Settings entry visibility, a 411px widget preview and 320px enlarged-text layout.
The existing single-session context screen remains available.

These fixtures verify server aggregation and client behavior, not provider
billing or final signed-device release readiness. Native integration of the new
timezone plugin still needs the platform CI builds and final device smoke. The
displayed cost is OpenCode's reported estimate, not a provider invoice.
