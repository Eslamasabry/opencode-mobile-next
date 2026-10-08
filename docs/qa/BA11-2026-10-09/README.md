# BA11 device proof on normal APK2198

PASS: on emulator-5554, Settings → Agents shows Claude Code signed in. Opening its account sheet offers Sign out and no Remove action. The check was observational; no account action was invoked. [Closed evidence](device-proof.json) stores only booleans, APK/source identity and signer results. No account value, screenshot of the account or raw UI dump is retained.

APK2198 source064a43a3687773626dc5ce37853446c29be41e0c, SHA2562069cc0ca62554e3fd798f8ba8bfa46f44ae84be4ff82142e87b495cdc8b89a3. Local artifact and installed APK hashes match; signer1DE5BF08… matches. Session ran under `flock -w1800 /home/eslam/Storage/tmp/oc-emulator.lock`; normal2198 remains installed. No app reinstall/uninstall, data clear, project mutation or Claude login/logout.

The first read-only attempt stopped before navigation because the refreshed emulator's adb shell could not read private setup state. Repeating the read with explicit root access succeeded. This is a QA adapter privilege change, not an app auth failure. No APK build or new source change was needed for this device proof.
