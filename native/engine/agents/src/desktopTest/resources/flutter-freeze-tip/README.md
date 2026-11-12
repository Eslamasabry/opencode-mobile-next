Flutter freeze-tip twins: the native release candidate's Flutter tree at 52f3898a6779c70f92417a6d4a5d52858f6dd926,
the last commit before `master` was merged in (4a0eac1cf4, 2026-11-10). Copied verbatim with a `.txt` suffix so Dart
tooling never analyzes them (the same pattern as `flutter-2.1.4`).

The native agent pins, the tested Paseo and Claude Code versions, the agent inventory and the Claude agent root script
were ported from these files. The master merge made `lib/` and `test/` byte-identical to the released Flutter app
(2.1.5), which has older Gemini, Qwen, Kilo and Cline pins, fewer tested versions and no Claude agent link, so the
parity tests read these copies instead of the working tree. Each file equals
`git show 52f3898a6779c70f92417a6d4a5d52858f6dd926:<path>` byte for byte; they never change, since Flutter takes no
new work. A pin that moves on native after the freeze is recorded where the test says (`postFreeze` and the literal
checks in `AgentPinsParityTest`, the `postFreezeMoves` list in `CatalogInventoryTest`).
