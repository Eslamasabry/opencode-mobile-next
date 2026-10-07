# BA5 + BA6 on the device (2026-10-07, late)

Emulator `OC_API35`, release APK 2190 = `feat/genui-fe` 281356fb (BA up to ecb11948). Not the owner's phone.

**BA5 — phone agents survive switching OpenCode 2 → 1 → 2.** Claude Code stayed Ready and its chats stayed listed on
both connections ([ba5-oc1-agents.jpg](ba5-oc1-agents.jpg), [ba5-oc2-after-roundtrip.jpg](ba5-oc2-after-roundtrip.jpg)).
Bugs found and handed to the BA lead (fixed in fa80b449, 3c578016; device re-check pending): fx showed "Phone check
needed" on both connections after the migration; on OpenCode 1 the Cards line read "On for Claude Code, OpenCode.
OpenCode hasn't been checked…". One switch back to OpenCode 2 stopped at "Start OpenCode" while the PC was almost out
of memory; the retry connected in 27 s.

**BA6 — OpenCode 1 agent cards.** In a quiz-app chat on OpenCode 1 (GLM-5.3): the agent's first `oc-ui_show` was
rejected as invalid, its retry showed "Pick a color"; answered Red in the chat → "You picked red."
([ba6-oc1-card.jpg](ba6-oc1-card.jpg), [ba6-oc1-chat-answered.jpg](ba6-oc1-chat-answered.jpg)). A second card
("Pick a size") showed under the list row with Needs you; answered Large from the list → "Sending your answer…" →
"You picked large." ([ba6-oc1-list-card.jpg](ba6-oc1-list-card.jpg), [ba6-oc1-list-answered.jpg](ba6-oc1-list-answered.jpg)).
Small copy bug: the failed attempt's tool row shows the raw name `oc-ui_show`.
