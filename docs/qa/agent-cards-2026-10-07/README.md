# Agent cards — device evidence (2026-10-07)

Feature: agents show native app cards (choices, forms, confirm, charts, tables, key-value, photo ask)
through the `oc-ui` MCP tool. Plan, contract and review:
[plan](../../design/genui-plan-2026-10-07.md) ·
[contract](../../design/genui-contract-2026-10-07.md) ·
[Astra review](../../design/genui-review-astra-2026-10-07.md). Backend verification:
[backend](../../verification/genui-backend-2026-10-07.md),
[runtime capture](../../verification/genui-runtime-2026-10-07.md),
[review fixes](../../verification/genui-review-fixes-2026-10-07.md),
[show without asking](../../verification/genui-show-without-asking-2026-10-07.md),
[list refresh](../../verification/genui-list-refresh-2026-10-07.md).

## Setup

- Device: Android emulator `OC_API35` (emulator-5554), not the owner's phone.
- App: release APK `1.2.0+2171` from branch `feat/genui-fe` at `12afdb3d`, signed with the local test key.
- Server: in-app Ubuntu; Claude Code 2.1.283 through Paseo 0.9.2; approval mode "Asks first".
- Settings → Agents → "Cards from agents": on; status line "On for Claude Code".

## Scenarios and results

| # | Scenario | Result |
|---|---|---|
| 1 | Ask Claude to pick a database through the tool | Choice card with three options and details in the chat; composer hint "Or type your answer" — pass |
| 2 | Back to the list | Row "Needs you", card under the row (also after a cold app restart) — pass |
| 3 | Tap SQLite in the list | Held line, then sent; Claude replied "SQLite it is"; card receipt "✓ SQLite"; answer bubble shows only "SQLite" — pass |
| 4 | Card with bar chart + key-value + danger confirm | Rendered in the chat; no tool approval prompt in "Asks first" — pass |
| 5 | Confirm from the list | Danger sheet "Send "Confirm"?", then "Confirmed · Undo"; row turned "Done" after Claude replied — pass |
| 6 | Choose Red, Undo within 3 s | Card back with Red still chosen; still "Needs you"; nothing sent after 6 s — pass |

Image: [proof-2171.png](proof-2171.png).

## Found and fixed during the device run

- List slot asked the row's agent backend, which does not exist before the chat is opened → cards are routed by the list's connection (`chats_host.dart`).
- Claude asked to approve `mcp__oc-ui__show` in "Asks first" → the tool is pre-allowed for the profile and auto-answered once (backend).
- The answer bubble showed the raw envelope → transcript shows the summary only.
- The held line said "Sent" → it names the answer ("SQLite · Undo").
- The list row did not refresh after a list answer → the owning feed refreshes after delivery and run end.

## Not covered here

- OpenCode 1 / OpenCode 2 cards: kept unavailable (not qualified on this emulator: no root-safe `/usr/bin/node`).
- Photo ask on a device, form ask on a device (widget-tested only), remote servers (slice 3), file/voice asks (slice 2).
