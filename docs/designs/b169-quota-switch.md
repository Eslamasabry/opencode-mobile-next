# B-169: keep a conversation going when a model's limit runs out

**Status:** v2, APPROVED by the owner on 2026-11-08, after three goldfish reviews (zoom-out, design critic, implementation readiness). The owner chose: free rows say "Free · may use your prompts"; ask before re-sending when the stopped reply ran tool steps. The v1 draft and its review findings are in git history.

**Scope:** native first, then the Flutter twin.

## Goal

When a limit stops a reply in an OpenCode 2 conversation, continuing takes one decision and one tap. The person picks another model, the stopped prompt is sent again with that model, and the history stays.

## Non-goals (v1)

These are deferred until the owner has used v1:
- switching agents (Claude Code, Codex, Antigravity via Paseo);
- switching without asking;
- an "always use B when A is out" setting;
- "back to A when it resets";
- a Switch action on the composer limit strip;
- ranking by family closeness;
- "same model, another account" (the catalog and the prompt path have no account selector);
- a privacy or country table;
- telemetry for this flow.

## What exists today (reuse, don't duplicate)

- **Failed-turn classification:** `engine/transcript/.../turnfacts/AgentRunFailure.kt` (`classifyAgentRunFailure`) feeds `feature/chat/.../presentation/ReplyFailure.kt`.
  - The causes come from `engine/agents/.../errors/AgentErrorCause.kt`: RateLimited, ProviderBusy, UsageLimit, FreeTierLimit, OutOfCredit.
  - `RetryStatus.Cause` duplicates them. Do not add a third table; extend `classifyAgentRunFailure` only.
- **Fixes on a failed reply:** `feature/chat/.../presentation/ErrorRecovery.kt` already offers `ErrorFix.ChooseModel`, `Compact` and `OpenProviders`. They are rendered in ChatTranscript.
- **Model picker:** `ModelSheet` / `KitModelSheet` over `ComposerModel`, which has `contextLimit` and a nullable `ModelCost`.
- **Per-prompt model:** `OutgoingPrompt.model` is posted with each OC2 `/session/{id}/prompt`. The model chosen at send time is `models.value.selected`.
- **Plan readings:** `ComposerLimit.kt` gives `Reached` and `resetsAt`. These exist only for providers with Usage readings: Claude, Codex, Gemini, Antigravity.

## Behaviour

### 1. Which failures offer the switch ("out")

`classifyAgentRunFailure` gains a duration flag, `out`, true for:
- **UsageLimit and FreeTierLimit** (today's `ModelUnavailable`).
- **OutOfCredit** (`NoCredit`): waiting can't fix it.
- **RateLimited**, only when the error says the limit is daily or monthly, or gives a reset more than 2 minutes away, or the server has stopped retrying. A per-minute rate limit stays as today: it is self-healing, the server retries, and no switch card appears.

ProviderBusy (overloaded) is not a limit: no change.

### 2. The failed turn

For an `out` failure in an OC2 conversation, the row reads:
- "{Model} is used up for now";
- plus "Resets {time}" only when a ComposerLimit reading for that provider and model family gives `resetsAt`.

Its fixes become **Switch model** (primary) and the existing **Compact** / **OpenProviders** where they apply. Only one switch control per turn.

### 3. Switch model

It opens the existing model sheet, with these changes:
- **Excluded:** the failed model, and models already marked used up in this conversation.
- **Suggestion first:** one pre-selected suggestion.
  1. First choice: a model from another provider whose plan reading is under 80 %.
  2. Otherwise: the person's recent models, in picker order.
  3. Models with no reading are unranked: picker order, no percentage.
- **"Free" label:** only when the catalog cost is exactly 0, never when it is null.
- **Context guard:** a model whose `contextLimit` (> 0) is below the conversation's current size, from `ContextUsage`, is shown disabled with "Too long for this model". An unknown limit (0) is allowed. The existing Compact fix stays available separately; no automatic compact-then-switch in v1.

### 4. Sending again (the step the reviews found missing)

Tapping a model does this, in order:
1. Select that model for the conversation (`models.value.selected`), as the model chip does.
2. Send the failed prompt again with that model, carrying its text, attachments, mentions and agent, read from the failed prompt in the transcript. Today's `sendAgain` sends text only, so this needs a new carry-over.
3. If an attachment can no longer be read: don't auto-send. Put the prompt in the composer with a notice, "Attach {file} again to send".
4. If the failed turn already ran tool steps (edits, commands) before failing: confirm first, with "The stopped reply already ran {N} steps. Send again with {B}?" and Send / Cancel.

The failed turn stays visible as failed, as today. The new prompt and reply follow it.

### 5. Loop guard

- A model that fails `out` is marked used up for this conversation. The mark lasts until its `resetsAt` when known, otherwise until the app session ends.
- Each stopped prompt can be switched at most 3 times. After that, the row shows only the reset time and **Connect a provider…**.

### 6. Where it is offered

**OC2 only,** gated by a capability flag (`ServerCapabilities.promptModelChoice`: true for the OC2 gateway, false otherwise), following the AGENTS.md rule to never gate on the flavor.
- In Paseo, Claude Code, Codex and OC1 chats, the failed turn keeps today's fixes. The limit strip stays as it is (View usage).
- **Offline:** the reconnect banner comes first. Switch model is disabled while offline.

## Gate before building

Capture one real limit error from an OC2 server, using a free OpenRouter or Zen model, to confirm the error shape reaching `classifyAgentRunFailure`. Save it as a test fixture. Without it, the duration rule is a guess.

## Change sites

All on the native side:
- `engine/transcript/.../turnfacts/AgentRunFailure.kt`: the duration flag;
- `feature/chat/.../presentation/ErrorRecovery.kt` and `ReplyFailure.kt`: the fix and the wording;
- `feature/chat/.../presentation/ChatPresenter.kt` and `world/GraphChatWorld.kt`: the carry-over, plus the used-up marks for this session.
  - These are single-owner units: one builder only.
- `ModelSheet` and `KitModelSheet`: the exclusions, the suggestion, the disabled-with-reason row and the Free label.
- `core/domain` `ServerCapabilities`: `promptModelChoice`.

## Acceptance

Tests with fakes:
- a per-minute 429 shows no switch;
- a daily limit shows Switch model;
- choosing B re-sends text, attachments, mentions and agent with B;
- a missing attachment goes to the composer instead of auto-sending;
- a turn that ran tools asks first;
- a too-long model is disabled;
- the third failed switch stops offering more;
- non-OC2 chats show no Switch model.

## Open questions for the owner

1. **Free models that may use prompts for training.** In v1, every "Free" row also says "Free · may use your prompts" unless the provider's catalog entry says otherwise. Confirm, or hide free models unless a setting allows them.
2. **Tool-step confirm.** Asking before re-sending a prompt whose stopped reply already edited files is the safe default. Confirm, or always send at once.
