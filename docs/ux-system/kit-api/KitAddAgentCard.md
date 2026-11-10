# KitAddAgentCard

`KitAddAgentCard` lives in `lib/ui/kit/kit_add_agent_card.dart` and is exported
by `kit.dart`. It is the visible setup action among the agent choices on the
New conversation screen.

```dart
KitAddAgentCard({
  Key? key,
  required String label,
  required VoidCallback onPressed,
  Key? tappableKey,
})
```

The caller supplies localized copy. The card contains a plus icon and its
label in a ground-level outlined `KitSurface`, using `KitTappable` for the
button. It has no agent brand, selected state, or exclusive-choice semantics:
it opens the agent catalog rather than choosing an agent.

The card participates in `KitAgentGrid` sizing and follows the reading
direction. It remains a visible first-row action beside installed agents;
with no installed agents, or at large text, it comes first. Text wraps at
200 percent scale. The callback opens the existing install/sign-in flow;
returning to the original draft preserves its text, project, and model.

States: none — this always-available navigation action has no asynchronous
work of its own. `KitTappable` supplies keyboard activation, focus, button
semantics, and the existing reduced-motion behavior.

Focused coverage is in `test/chats_new_chat_test.dart` and
`test/goldens/vision_start_golden_test.dart`. The real start screen
is catalogued in `widgetbook/lib/chat/new_chat.dart` with installed agents
and with no installed agents. G4 discovers the part from its implementation
and `kit.dart` export; no manifest allowlist entry is needed.
