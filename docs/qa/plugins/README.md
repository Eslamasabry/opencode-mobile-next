# Plugin and task captures

These are synthetic-data captures rendered by production screens, not photos
of an installed APK or proof of a live plugin executing.

- `personal-light.png` and `personal-dark.png`: `PluginsScreen`, showing a
  fictional plugin inventory and user-reviewed command associations.
- `tasks-light.png` and `tasks-dark.png`: `ChatScreen`, after opening the
  **Tasks** tool card for a synthetic `todowrite` tool message. There is no
  separate "Task list" page behind these images. The earlier standalone task
  component preview must not be presented as an app page.

Regenerate with `tool/capture/plugin_mobile_test.dart`. The task recipe checks
that task content appears after tapping the production tool card. It does not
send a prompt, execute a tool, or contact a server.

Phone-gallery caption: **Synthetic data in production Plugins / Chat screens**.
