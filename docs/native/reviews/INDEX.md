# Codex second-eye reviews (native)

Every landed unit gets a read-only Codex review. Counts are mentions of the words in each report. Every real finding needs a fix and a test that fails before it.

| Unit | BLOCKER | MAJOR | Verdict |
|---|---|---|---|
| agenthost | 0 | 8 | Verdict: REQUEST CHANGES — 7 MAJOR, 1 MINOR. |
| backoff | 0 | 7 | Verdict: **REQUEST CHANGES — 6 MAJOR, 1 MINOR.** |
| chat | 0 | 10 | Verdict: **REQUEST CHANGES** — the range contains data-loss, cancellation, reconciliation, and catalog-coverag |
| chats | 0 | 6 | Verdict: **REQUEST CHANGES** — acknowledgement loss, incorrect routing, and stale destructive confirmation rem |
| compe | 2 | 8 | Verdict: **BLOCKER — this range does not compile against its core contracts and contains draft-loss and creden |
| compmotion | 0 | 1 | Verdict: **REQUEST CHANGES** — static, read-only review of `ba8f649e..7ba297a1`; no builds or test processes r |
| compui | 0 | 9 | Verdict: **REQUEST CHANGES — 8 MAJOR, 1 MINOR.** |
| dlv | 2 | 6 | Verdict: **REQUEST CHANGES** — duplicate dispatch and durable queue-loss paths remain in `d049231f..45a3b0bb`. |
| eng | 1 | 7 | **Verdict: BLOCKED — the range does not compile against its core contracts and contains deletion, consent, and |
| entry | 0 | 6 | Verdict: CHANGES REQUESTED — 5 MAJOR, 1 MINOR. |
| field | 0 | 7 | **Verdict: REQUEST CHANGES — 6 MAJOR, 2 MINOR findings.** |
| gates | 2 | 10 | Verdict: **BLOCKER — the push gate can publish unscanned commits, and the ladder can qualify the wrong tree.** |
| kita1 | 1 | 5 | **Verdict: BLOCKED — one compile blocker and seven behavioral findings.** |
| kita2 | 2 | 8 | **Verdict: BLOCKER — the range contains incompatible tests, credential-handling defects, and modal/action beha |
| kitb | 0 | 6 | Verdict: REQUEST CHANGES — 5 MAJOR, 2 MINOR in `49cc7fcc..08189c83`. Static, read-only review; no builds or te |
| kitchat | 0 | 5 | REQUEST CHANGES — static review of `bad3a328..96b8f66f`. No builds or tests executed. |
| mediaatt | 0 | 8 | Verdict: REQUEST CHANGES — 7 MAJOR, 3 MINOR. |
| notify | 1 | 5 | Verdict: **NO-GO** — credential exposure, request-replacement races, and incorrect reply-watch lifecycle behav |
| phoneui | 0 | 5 | Verdict: **REQUEST CHANGES** — 4 MAJOR, 2 MINOR findings. |
| plat0 | 0 | 4 | **Verdict: CHANGES REQUIRED — privacy, lifecycle, and network-contract defects remain.** |
| qrcam | 0 | 5 | **Verdict: REQUEST CHANGES — four MAJOR defects and two MINOR findings.** |
| req | 0 | 6 | Verdict: **REQUEST CHANGES — 5 MAJOR, 1 MINOR.** Cancellation and concurrent settlement can break the claimed  |
| reqforms | 1 | 10 | Verdict: **REQUEST CHANGES** for `af824d5d..ba8f649e`. |
| rteng | 0 | 10 | Verdict: **REQUEST CHANGES** — lifecycle, rollback, and admission guarantees are not met. Static, read-only re |
| secgates | 0 | 7 | **VERDICT: CHANGES REQUIRED** — `ff699a10..c5654cf7` has security-gate bypasses and incomplete enforcement. |
| settings | 0 | 6 | Verdict: REQUEST CHANGES — 5 MAJOR, 2 MINOR. |
| termmin | 0 | 8 | Verdict: **REQUEST CHANGES** — lifecycle races can leave orphaned or unprotected sign-in terminals. |
| voice | 1 | 11 | Verdict: **REQUEST CHANGES**. Read-only static review of `97f5db42..f5facaab`; no builds, tests, or mutations  |
| w1-core-net | 3 | 8 | **Verdict: BLOCKED — the merged range does not compile against its core contracts and contains memory, deliver |
| w1-data-oc2 | 1 | 5 | **Verdict: REQUEST CHANGES — integration, persistence, and cancellation defects remain in `5ec8616d..f885d05f` |
| widgets | 1 | 5 | Verdict: **REJECT** — unsafe approval rendering, cancellation and persistence defects remain in `f5facaab..066 |
