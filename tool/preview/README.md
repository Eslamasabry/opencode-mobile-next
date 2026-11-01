# Live Flutter web development

Run the application's real `lib/main.dart` with the pinned Flutter SDK:

```sh
python3 tool/preview/run_web.py --host 127.0.0.1 --port 8789
```

For another device on the private tailnet, use this machine's current Tailscale
IPv4 address as `--host`. The development server must remain running. This does
not configure public hosting, TLS, a server proxy, or backend authentication.

Saved Dart files under `lib/` and the SDK package trigger a debounced hot reload.
Flutter's `r` (reload), `R` (restart), and `q` (quit) commands remain available in
an interactive terminal. Changes to dependencies, web bootstrap assets or
generated localization require the usual dependency/generation step and restart.
Use reload for widget and method-body changes; restart when Flutter reports a
change that cannot be hot reloaded. Web-server debugging can additionally use the
Dart Debug browser extension; serving and hot reload do not require public
debugger access.

`--no-web-resources-cdn` serves the matching CanvasKit assets from the pinned SDK.
The Shorebird engine revision is not guaranteed to exist on Flutter's public
renderer CDN. This keeps the actual renderer version matched and works offline
after SDK dependencies are cached.

This is a development command, not the production web deployment command.
Browser storage and authenticated connections must meet the application's
secure-context, CORS and server compatibility requirements. Never disable browser
security, put persistent credentials in URLs, or substitute a screenshot gallery
for the running app.
