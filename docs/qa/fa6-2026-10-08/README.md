# FA6: Remove agent on the agent's sheet (2026-10-08)

Branch `fe/remove-agent`, based on `feat/genui-fe` at `ac8ddc92e` (the BA10
backend merge). Contract: `docs/design/BA10-contract.md`.

**Not device proof.** Every image below is a widget golden rendered against a
fake source (`FakeRemovableAgentsSource` in `test/support/agents_fakes.dart`)
that plays the controller's part: `canRemoveAgent`, `removingAgentId` and
`removeAgent` return what the test scripts. No agent was removed on a phone or
an emulator. The BA10 device proof (APK 2197, shared emulator lock) is the
backend lane's.

## Finish line

The agent's sheet offers "Remove <Agent>" as a quiet destructive action at the
bottom (after Sign out) only where the source's `canRemoveAgent` is true and
never for Claude Code. It asks first, shows "Removing <Agent>..." in place,
then what was freed, and the Settings row returns to its Install chip. A source
without `PhoneAgentRemovalSource` shows no Remove. A signed-in agent at its
plan limit now opens its sheet, so Sign out and Remove are reachable.

## Goldens

Folder `test/goldens/`, 412x915, each as `_light`, `_dark` and Arabic right to
left (`_ar_light`, `_ar_dark`). Test: `agents_remove_golden_test.dart`. The
agent is fx (it can sign out and be removed).

| File stem | What it shows |
| --- | --- |
| `agents_remove_sheet` | fx's sheet, signed in: "Use fx", "Sign in again", "Sign out of fx", and the quiet destructive "Remove fx" last. Behind it Settings > Agents. |
| `agents_remove_confirm` | "Remove fx?", "This removes the installed agent from this phone. Your accounts and conversations stay, and you can install it again.", danger-filled "Remove fx", neutral "Cancel". The sheet is still visible behind the question (the kit's confirm opens over a framed sheet). |
| `agents_remove_pending` | The sheet in place: "Removing fx..." with a waiting bar, no actions and no close button. Behind it the fx row says "Removing fx..." and offers no chip; other rows are unchanged. |
| `agents_remove_done` | "fx removed. Freed 98 MB." with Done. The row behind has already returned to "Not installed" with its Install chip. In Arabic the size reads "98 MB" in order (left-to-right isolate). |

## Behaviour tests

`test/agents_remove_ui_test.dart` (28 tests): Remove only where
`canRemoveAgent` and never for Claude Code (also when a source claims it can);
it sits after Sign out; offered from the install step of a failed or
interrupted install and from a failed phone check, not for a not-installed
agent; the question names the agent and says accounts and conversations stay,
Cancel removes nothing; while pending the sheet and the Settings row say
"Removing", that agent has no act, and another agent's Install is disabled
with the busy reason; success shows the freed size (formatted and isolated,
also in Arabic) and the row goes back to Install; `alreadyAbsent` says it is
already removed; each of the three fixed failures, a raw error and a host
failure all show one of them (no path or native text anywhere), and a retry
works; a plan-limit row opens its sheet (Codex: Remove; Claude Code: Sign out,
no Remove) and the limit is not offered as "Use".

## Notes

- **Copy differs from the contract in three places, to pass the copy gates**
  (`test/ui_glossary_test.dart` G11 and G28, which fail on new violations):
  the question's title is "Remove <Agent>?" (contract: "Remove <Agent> from
  this phone?", over the four-word limit), its body is two sentences with the
  same facts (contract: three), and the confirm button reads "Remove <Agent>"
  (contract: "Remove"; COPY-9 needs the act and the thing). The Cancel
  button, the pending, done, already-removed and three failure sentences are
  the contract's exactly.

- The controller refuses any install while a removal runs, so Install is
  disabled for every agent meanwhile; Sign in and Remove only for the agent
  being removed.
- A row that offers only a Resume chip (stopped in the background) still does
  not open the sheet, so Remove is reachable once it is running again.
