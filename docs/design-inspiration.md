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

## Files — developer file browsers

- [GitHub repo browser](https://mobbin.com/screens/d9daf506-763b-497c-a9f4-af14e23ea690) —
  folders first with distinct folder color; chevrons only where drilling in.
- [Manus file management](https://mobbin.com/screens/81f2a963-b951-4d48-9a6e-724fcbf88298) —
  tinted rounded-square type icons per file kind; type filter chips.
- [Mimo code browser](https://mobbin.com/screens/993b5bd7-f3c2-47cc-842f-0a1a3af5c4a7) —
  per-language glyphs (JS badge, braces for JSON).

**Applied here:** file rows get type-aware tinted icons (code, image,
document, config, archive) in the app's _TileIcon style; a breadcrumb path
bar replaces back-only navigation in deep directories.

## Review — pull-request review

- [GitHub Files Changed](https://mobbin.com/screens/fd243541-6028-43b5-b70d-e5664601d2b7) —
  aggregate +/− counts in the header and a per-file "viewed" checkmark —
  the exact reviewed-progress pattern.

**Applied here:** Review tracks which files' diffs were opened this session
and shows "N of M viewed" beside the scope controls; the active file tab
gains a filled treatment for legibility. Diff renderer and phone toolbar
untouched (owner-verified live).

## Terminal — session rows

- [GitHub agent sessions](https://mobbin.com/screens/6c220cd6-4d7f-405e-8d55-0db5379ef6ce) —
  colored status glyph + status chip + mono identity per session row.
- [Telegram devices](https://mobbin.com/screens/d5905262-96e9-4793-b9c9-7d55b06c1379) —
  active/past grouping for live vs ended sessions.

**Applied here:** terminal rows show a status-colored glyph and
Running/Exited chip (success green from AppTheme), with shell identity in
AppMono. The xterm surface, WebSocket, and cursor resume stay untouched.
