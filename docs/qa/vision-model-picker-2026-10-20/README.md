# Model picker: three plain choices (vision slice, 2026-10-20)

Branch `feat/vision-model-picker` (from int `71916b7c5`). Concept: `b2-model-picker/concept-c.png`
plus the vision rule "three recommended rows plus **All models (N)**".

## 1. Data audit (written before building)

The question per row: does a server send something that makes the claim true?

### What each catalog carries per model

| Field | OpenCode 1 (`/config/providers`, models.dev) | OpenCode 2 (`/api/model`, `/api/model/default`) | Paseo phone agents (Claude Code, Codex) |
|---|---|---|---|
| Price (USD per 1M tokens, in / out / cache) | yes, `cost` map (models.dev) | yes, `cost[]` (base entry + context tiers) | **no** |
| Context and output limits | yes | yes | context only (`contextWindowMaxTokens`) |
| Capabilities (tools, attachments, reasoning) | yes | tools + input kinds | no |
| Release date, status | yes | yes | no |
| Server default | `default` map: per provider, OpenCode's own priority sort; the app takes the first connected provider's | `GET /api/model/default`: the effective default (config `model`, else the server's own pick) | `isDefault` on one model per agent |
| "Recommended" flag | no | no | no |
| Speed (latency, tokens per second, tier) | no | no | no |
| What you chose before | the app's own per-server `ModelLibrary.recent` (each successful choice) | same | same |

Where it reaches the picker: `ConnectionController.catalog` (`CatalogModel.cost`, `.enabled`),
`ConnectionController.providers.defaultProviderID/defaultModelID` (OC1, OC2, Paseo default agent),
`ConnectionController.providers.providers[*].modelData[id]['isDefault']` (Paseo, every agent), and
`ConnectionController.modelLibrary.recent`. No new server call and no gateway change is needed.

### Verdict per row

| Concept row | Verdict | What the picker shows instead |
|---|---|---|
| **Best for most work** | **Not claimable as "best".** No server ranks models. What exists is the server's default, which is what the server itself starts with. | **"Default"**: the server's default model (OC2 `/model/default`, OC1 `default`, Paseo `isDefault` for the conversation's agent). Shown only when that model is in the list and enabled. |
| **Fastest** | **Dropped.** No catalog carries a speed signal. `-fast` model ids and `fast` variants are a faster *mode of one model* (priority output), not a comparison between models; "flash", "haiku", "mini" are names, not data. A curated list would go stale with every release (Opus 5.5, GPT-6 Sol). | Nothing. The row does not appear. |
| **Cheapest** | **Honest where prices exist** (OC1, OC2). Lowest input + output price among enabled models that carry a price. A model without a price is never counted as cheap. | **"Cheapest"**, with "Free" on its line when the price is zero. Shown only when prices differ: when every priced model costs the same (only OpenCode's free models), no model is cheaper than another, so the row is left out and each row says "Free" instead. Never on Paseo (no prices). |
| (extra, honest) | The app knows what the person chose before on this server. | **"Recently used"**: the latest earlier choice that is not already a row and not the one in use. |
| (state) | The model in use must always be visible, or the person cannot tell what is chosen. | **"In use"**: when the current model is not one of the rows above, it takes the third place. Its row carries the check. |

Rules:

- At most three recommended rows; fewer when fewer are true. Never padded.
- One model is never two rows. When the default is also the cheapest, the Default row says
  "Cheapest" on its line instead of a second row.
- Ties for cheapest prefer the model in use, then a recent one, then a favourite, then the
  catalog's own order. A zero price is said as "Free" on every row that has it.
- Model names come from the catalog's display name (`Claude Sonnet 5.5`), never the id.
- When nothing honest is left to recommend besides the model in use, or the catalog has no more
  models than rows, the sheet opens on the full list directly (a first view with no alternative
  is a step for nothing).
- OpenCode 1 nuance: its `default` is OpenCode's own pick for the first connected provider, not the
  `model` line of the config file. It is still the server's reported default, so the row is called
  "Default" and nothing more.
- Phone agents (Paseo): the conversation's own agent list (`catalogForSession`), its own
  `isDefault`, and recent choices. No price row, because Paseo sends no prices.

## 2. What was built

- `lib/domain/model_recommendations.dart`: `recommendModels()`, the pure policy above
  (`ModelReason.inUse / serverDefault / recent / cheapest`; there is no fastest).
- `lib/ui/kit/kit_choice_tiles.dart` (new kit part, spec `docs/ux-system/kit-api/KitChoiceTiles.md`):
  big tiles that lead with the reason, check on an accent disc plus an accent ring for the one in use
  (never a solid mint row), a busy spinner while a tap is applied, one quiet "All models (N) ›" line.
- `KitSheetStep` + `showKitSheet(stepListenable:)` (spec addition in `KitSheet.md`): a sheet whose
  content comes in steps. The frame cross-fades in place, a step can rename the header, add a back
  chevron, change the height and drop the close button on a bottom sheet. No sheet on a sheet.
- `lib/ui/widgets/pickers.dart` + new part `pickers_choices.dart`: `showModelPicker` opens on the
  choices step (title "Model", "For this conversation" / "For new conversations", no close button,
  no pinned Apply). A tap applies the model (thinking level back to its default, agent untouched)
  and closes; the model in use just closes. "All models (N)" swaps in today's full list (search,
  filters, All / Favourites / Recent, stars, details, Thinking and Agent chips, pinned
  "Use for this conversation"), titled "Choose a model" with a back chevron; system back returns to
  the choices (a typed search clears first). The agent door (`focusAgent`) opens on the full list.
- Copy (en + ar): `modelPickerSheetTitle`, `modelPickerAllModels`, `modelPickerReasonDefault`,
  `modelPickerReasonRecent`, `modelPickerReasonCheapest`, `modelPickerFree`,
  `modelPickerScopeSession`, `modelPickerScopeNewSessions`; the now unused `modelSessionScopeNote` removed.

Not changed: chat screen, new-chat screen, `lib/state/connection*`, `lib/paseo/`, the gateway.

## 3. Visual passes (against concept-c, 412x915 dark)

Captures: `tool/capture/vision_model_picker_test.dart` (the real sheet over the real ChatScreen).
Iterations are kept in `/home/eslam/Storage2/launch-footage/vision-model-picker/pass1..pass4/`.

1. **Pass 1 → 2.** The scope line was a long sentence ("Applies to this conversation's next
   turns.") → "For this conversation". Unselected tiles had a hairline the concept does not →
   fill only in dark. The model name line was smaller than the concept's → body size. Tiles sat
   closer than the concept's → 12 dp apart. Light tiles were a murky grey → near-white with a
   hairline. All models repeated "All models" in title and segment → titled "Choose a model".
   Arabic rendered as boxes in the capture → system Arabic face loaded.
2. **Pass 2 → 3.** The check was a thin mint stroke, weaker than the concept's → check on an accent
   disc. Arabic Latin names boxed (wrong fallback) → Roboto as `sans-serif`, Noto Sans Arabic after.
3. **Pass 3 → 4.** At 200 % text the "·" dangled at a line end ("Big Pickle ·" / "Free") → each fact
   on its own line at large text. Rings were drawn at 1 dp density → captured at 2x density so
   hairlines and the selection ring look as thin as on a phone; ring width moved to
   `KitTokens.focusRingWidth` (gate G21).
4. **Kept on purpose:** the sheet title stays the kit's 24 sp sheet title (concept-c draws it near
   32 sp); every other sheet uses the same size. The selected tile is a ring and a check, not the
   concept's solid mint fill (vision rule: mint fill is for the one main action). The height
   change into All models is the frame's cross-fade, not a size animation (G2 forbids
   `AnimatedSize` in the kit outside its allowlist, and allowlists never grow).

## 4. Final renders

`/home/eslam/Storage2/launch-footage/vision-model-picker/`: `oc2-costs-dark.png`,
`oc2-costs-light.png`, `free-only.png`, `phone-agent-claude-code.png`, `all-models.png`,
`narrow-320-2x.png`, `arabic-rtl.png`. All sample data in them is fixture data. The phone-agent
render uses a Paseo-shaped catalog (agent-marked default, no prices) over an OpenCode chat; no
live Paseo daemon or emulator was used.
