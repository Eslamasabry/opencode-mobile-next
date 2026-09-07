# Saved-server attention captures

`light.png` and `dark.png` use synthetic server responses inside the production
`HomeScreen` with its **Activity** destination selected. The navigation bar and
Activity content are the actual parent of `ProfileMonitorInbox`; the earlier
standalone inbox wrapper is not a separate app page.

Regenerate with `tool/capture/profile_monitor_test.dart`. The two profiles and
permission request are fixtures. No live server was polled, request approved,
or native notification delivered by this capture recipe.

Phone-gallery caption: **Synthetic server data in the production Activity tab**.
