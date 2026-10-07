# BC3 — Paseo integrity and startup failure

No new gateway or channel methods. Setup remains the existing agent component job; host launch remains `PhoneAgentHost.start(profile, password, 4099, config)`.

Setup checks actual executable links and shipped package code before running any Paseo code. The pin covers the launcher, entry payload and all 287 regular files in `@getpaseo/cli/bin` and `dist`, not merely the lock marker. Tarball source is `https://registry.npmjs.org/@getpaseo/cli/-/cli-0.9.2.tgz`; its SHA512 SRI was verified against the shipped package lock before deriving these SHA256 pins. Dependency downloads retain npm SRI checks and disabled install scripts.

The private component stage allowlist now preserves these exact fixed names through native job polling:

| Stage | Plain failure / way forward |
| --- | --- |
| Checking Paseo checksum | Paseo did not pass its checksum check. Run setup again. |
| Checking Paseo launch command | Paseo did not pass its launch command check. Run setup again. |
| Checking Paseo version | Paseo did not pass its version check. Run setup again. |

Other agent stage/output text remains private. Help/version probes use empty temporary HOME, clean environment and 30-second timeout; raw output is never returned as an error. The checked launch command is `paseo daemon run --home <path>`; removed flags remain absent. A candidate failure leaves the previous tree in place.

Host start keeps a starting reservation and waits 1.5 seconds before registering the child. A child exiting in this interval returns “The agent host stopped as soon as it started. Run its setup check and try again.” It is cleaned up and never tracked as running. A second start while one is pending returns “The agent host is still starting. Wait a moment and try again.” Stop/delete generation fencing remains in effect. Configuration and transport exceptions are projected to fixed retry guidance, never raw output.

Frontend: keep the existing starting state while awaiting start; show the fixed localized failure and Setup check / Retry actions. No UI or localization files are edited here. No new persisted shape or migration.

These are stability checks within the documented shared Ubuntu trust zone, not a kernel isolation or ongoing runtime-integrity guarantee. They do not certify agent sign-in, chat or physical ARM64 behavior.

Evidence: `docs/qa/BC3-2026-10-07/README.md`; shell replacement proof and real Android native-host execution, no installed APK claim.
