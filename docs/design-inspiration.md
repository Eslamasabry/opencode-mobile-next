# Design inspiration notes (Mobbin research)

Research date: 2026-08-28. Patterns pulled from Mobbin's iOS library and
mapped onto this app's surfaces. Citations are Mobbin screen links.

## Home / Workspace — composer-first

Every leading AI assistant opens ready to type: the input is docked on the
home screen itself, with a greeting or suggestions above and recents below.

- [Google Gemini home](https://mobbin.com/screens/89c43e4c-dd61-4a73-94b7-40eda3b73542) —
  suggestion cards, compact Recent list, bottom "Type, talk, or share" pill.
- [Claude home](https://mobbin.com/screens/c703ff9a-3e8e-4332-8987-8831b723fc6a) —
  centered greeting, model selector in the header, composer with attach/mic.
- [ChatGPT drawer](https://mobbin.com/screens/a99dfac5-4d3c-4c7e-8e50-1db1d9ef9afd) —
  dense recents, one prominent Chat action.
- [Gemini greeting variant](https://mobbin.com/screens/b1af71d2-f3f3-46ac-98f6-b5635fd1f15b) —
  "Where should we start?" + action chips.

**Applied here:** Workspace keeps its project/coding context but gains a
bottom-docked quick-ask pill ("Ask OpenCode…", mic and attach affordances)
that opens a fresh session in the active project, plus suggestion chips.
The New-session FAB's job moves into the pill. Chat itself stays one tap
deep for existing sessions via the recents list.

## Settings — hub-and-spoke

Mature apps keep top-level Settings to a short list of category rows with
icons; every detail lives one level deeper.

- [Instagram settings](https://mobbin.com/screens/83c9dde8-a66e-4860-bfd4-dfc1c070827d)
- [Deepstash settings](https://mobbin.com/screens/1402efff-5fe3-487c-8137-a3a96cc81f1a)
- [Quo settings](https://mobbin.com/screens/7bbea1a9-c1aa-4f9d-9ff7-d1ca74550074)
- [Base display sub-page](https://mobbin.com/screens/3f573b95-4bbb-4978-957e-56178543e8e7)

**Applied here:** Settings becomes a category hub — Server & connection,
Coding defaults, Notifications & background, Appearance, Privacy &
permissions, Diagnostics, About & guide — each a focused sub-page carrying
today's rows unchanged.

## More hub — visual destinations

Icon-forward card grids scan faster than subtitle-heavy list walls; done in
the More redesign (live setup card + card grid + tabbed Commands & tools).

## Still to mine (next passes)

- File browser patterns (GitHub mobile, Working Copy) for Files.
- Diff/review presentation (GitHub mobile PR review) for Review.
- Terminal apps for the Terminal tab's chrome.
- Onboarding personalization flows once first-run feedback lands.

## Chat — agent activity presentation

- [ChatGPT Codex session](https://mobbin.com/screens/8ebafe46-9984-453e-a420-4c286164f015) —
  "18 previous messages ›" collapses old history; files as inline links;
  agent/model chips inside the composer.
- [Manus task run](https://mobbin.com/screens/470227ce-9b5d-4f5c-a14d-b6ba0ea434d5) —
  plan steps as collapsible checklist rows; a live "Creating file …" ticker.
- [GitHub agent session](https://mobbin.com/screens/13e22b46-b056-463d-b90f-5dc0b39e1613) —
  step rows with elapsed time and a pulsing Working row.
- [Vibecode updates](https://mobbin.com/screens/8a1f042c-da34-4ccb-821f-f995fb220266) —
  compact activity rows with update counts.

**Applied here:** the running tool-group header becomes a live ticker naming
the tool actually executing ("Shell · flutter test…") instead of a static
summary; long transcripts get an "N earlier messages" pill that opens the
existing timeline. Already aligned: grouped tool timeline, composer context
chips, context meter.
