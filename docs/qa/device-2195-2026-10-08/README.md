# Device check — APK 2195 (2026-10-08)

Build: release 1.2.0+2195 from `feat/genui-fe` e2b3fe16, local signer 1DE5BF08…, emulator-5554 (x64), installed with `adb install -r -d` (data kept).

| Check | Result | Evidence |
|---|---|---|
| App-exit notice clears itself once the phone's OpenCode is back (fix/exit-notice-fades) | Pass — notice gone ~40 s after launch, list shown | 00-notice-cleared.jpg |
| OpenCode 2: Claude Code Ready, fx keeps its phone check ("Sign in needed") | Pass | 01-agents-oc2.jpg |
| Cards line names only the connected runtime (was "OpenCode" twice) | Pass — "Claude Code, OpenCode 2 …" on OC2, "OpenCode 1" on OC1 | 01-agents-oc2.jpg, 07-cards-oc1.jpg |
| Switch OC2 → OC1: Claude chats stay listed, Claude stays Ready, fx keeps check | Pass (switch ~25 s) | 05-chats-oc1.jpg |
| OpenCode 1 cards verification | **Fail** — "Cards were installed for OpenCode 1 but didn't pass the check", also after "Check this phone" | 07-cards-oc1.jpg → BA (sol/ba-fixes-3) |
| Check this phone keeps finished agents' results | **Fail** — Claude row shows "Phone check needed" while fx is checked | → BA |
| Switch copy | **Fail** — header "Reconnecting to … OpenCode 2…", banner "OpenCode on this phone isn't answering", transient "Server password changed — reconnect.", "Still waiting after 8 s" frozen | 03-switch-4.jpg → fe/switch-header-picker |
| Cards line grammar on OC2 | Minor — "Claude Code, OpenCode 2 hasn't been checked" (plural) | 01-agents-oc2.jpg |
