# Teaching: built-in tips and mini tutorials

Owner rule (2026-10-21), in the owner's words: "I like the concept of teaching and mini tutorials, make it built in for our app and a rule to follow." AGENTS.md carries the short form.

## What gets taught
- **Hidden gestures:** long-press, hold, swipe, double-tap, drag. Example: long-press Stop → "Stop after this step".
- **Power paths** that aren't visible on first look. Examples: Send later, phrase chips, the Go-to menu in Search.
- **New capabilities** after an update, taught once, in context, not as a changelog.
- **Bigger features**, through a mini tutorial. Examples: Appearance and "Make it yours", the AI Team, Git in the chat.

## Two parts, both in the kit
1. **Tip** (`KitTip`): one sentence of 3–8 words, anchored to the thing it teaches ("Hold to stop after this step"), with an optional "Got it". It shows when the thing becomes useful, not at app start, and fades after ~4 s or on any touch. It never blocks input, never sits over the field being typed into, and never uses amber (amber means "needs you").
2. **Mini tutorial** (`KitTour`): 2–4 steps, each highlighting one real control with one sentence, plus Next / Skip. It is offered at first use of the feature ("Show me how · 30 s"), never forced, and can be left at any step.

## The rules every tip follows
- **Shown at most twice per tip**, and never again once the person has used the thing.
- **One at a time:** never two tips on screen, at most one new tip per screen visit, and a quiet gap between tips (the controller enforces a cooldown).
- **Contextual:** the trigger is the moment of use (e.g. a reply running >10 s for the Stop tip), never a timer at launch.
- **Respectful:** no tips during onboarding steps, while a question card waits, or while the keyboard is up over the anchor.
- **Accessible:** the tip text is announced politely to screen readers. Reduced motion means a plain fade, no movement. Tips read correctly in RTL and at 2× text.
- **Replayable:** Help › Tips lists every tip and tutorial with a "Show me" button that replays it in place. Help › Tips › "Show tips again" resets all counts.
- **Stored app-wide:** `oc.tips` (counts, last shown, done). Listed in the preferences ledger; not per server.
- **Registered:** every tip and tutorial is declared once in the tip registry with an id, copy keys, an anchor, a trigger, a max count and a priority. A test fails if a registered tip has no Help › Tips entry or no copy in en and ar.
- **A hidden path is never the only path:** the visible alternative exists first, and the tip only points at the shortcut.

## Checklist for any new hidden gesture or feature
- [ ] A visible way to do the same thing exists.
- [ ] A tip or tutorial is registered, with en and ar copy.
- [ ] Its trigger is the moment of use.
- [ ] It appears in Help › Tips.
- [ ] Tests: shows once in context, max count respected, no tip while a question card waits, and the screen-reader announcement.
