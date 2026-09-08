# Simple first run

The first screen leads with **Connect to a server** and **Try demo**. Tailscale, on-device Termux, the setup guide and external agents remain available under **More setup options**, with their existing platform gates. Saved profiles retain the demo entry and a single Add server action.

The editor opens without an unsolicited address keyboard. Credential recovery still focuses the missing password/token. Its bottom action says **Save & connect** for new, active and Codex profiles, and **Save changes** for inactive OpenCode profiles. The existing save failure, successful-save/failed-connect and dirty-close behavior remains in the existing controller/editor flow.

These are synthetic screenshots from the real widgets and app theme, using no real profile or network connection. Capture geometry is 1080×2400 at 2.625 DPR, with 1× and 2× text in light and dark themes. The first-run page has a 24dp content rail, 12dp title-to-body gap, 32dp section separation, a 56dp primary target and a 48dp secondary target. Advanced rows are unboxed and preserve standard Material interaction feedback.

Capture command (pinned Shorebird Flutter3.47.2):

```sh
flutter test --concurrency=1 tool/capture/simple_first_run_test.dart
```

Focused checks cover first-run routing and saved-profile demo return; active/inactive save labels; manual editor keyboard behavior, dirty-close and save/connect failures; credential recovery; pairing; protocol connection paths; and desktop platform gates. Verification results are appended once run, not inferred from the implementation.

Scope limits: these captures are not an installed replacement APK. Real QR pairing, TLS/auth failures, Tailscale/Termux installation and device frame timing require separate runtime verification. The nested demo chat header/empty-state and host-specific connection recovery are tracked by their respective journey owners.

Verification: scoped analyzer clean; formatter clean. Final form/navigation/captures38 tests and remaining server/platform/security49 tests pass in serial on the pinned toolchain. Recovery diagnostic/card24 tests pass. The12 PNGs were regenerated from the final source. Initial failures were fixed before these final results: the editor now builds its finite field list eagerly so validation can focus fields after viewport changes; demo navigation testing advances the route animation before inspecting it.

Loopback connection recovery now leads with Retry. A supported local OpenCode endpoint retains an explicitly secondary Termux setup shortcut; remote and Codex endpoints do not get that shortcut. Conditional troubleshooting copy does not assert that Android always stops Termux when it closes.

Remaining density issue: the editor still shows protocol choice and pairing above manual input. The current repair makes those visible with authentication before keyboard focus; it does not claim to have validated a new pairing wizard or every setup variant.
