# Full suite — 2026-10-09

1. `cd5b97c4` (all backlog merged): 1,210 files in 16 chunks; 70 failures in 26 files from the day's last merges
   (quiet reconnect, cold start, partial-install Remove, idle pause/restore, step timeline, connector card).
   Fixed: BA 45 (sol/ba-suite-fix-2), BB 11 (sol/bb-suite-fix), frontend 14 (fe/suite-fix-2).
2. `be071d0d` (fixes merged): 1,210 files; 20 failures in 6 files, all from the composer region layout
   (status strip and permission card splitting the free height; one G16 scroll construct).
   Fixed in fe/suite-fix-3 (`chat_composer_region.dart` only).
3. `9ae84266`: the only changed file since run 2 is `lib/ui/screens/chat/chat_composer_region.dart`; all 80 chat,
   composer, voice, demo, nudge, kit-ratchet and golden-harness test files rerun: **1,066 passed, 8 skipped, 0 failed**.
   Every other file passed unchanged in run 2.

Commands: `tool/qa/machine_lock.sh test -- flutter test --concurrency 2 <chunk>` with pinned Flutter 3.47.1,
foreground chunks under 10 minutes. Analyzer clean (`flutter analyze lib test`).
