# BC1 — interrupted package repair

`SetupRunner.status()` / existing setup job polling keep the same wire shape.
No new frontend or gateway method is required.

- A failed `dpkg --configure -a` announces `Repairing interrupted package installation` before apt dependency repair and a second configure.
- A failed repair leaves the component and job `failed`. The final error is plain retry guidance; the raw package report remains in setup log / Details.
- Details contain the named command `dpkg --audit` and its actual findings. Nonempty findings fail even when that command exits zero.
- Package installation passes only after each requested package's `dpkg -s` says `Status: install ok installed`.
- Retry uses the existing setup resume flow; failed packages are never marked done. No new persisted format, credentials or profile data.

Plain words include “Package repair could not finish. Retry setup to repair the interrupted installation.” and “A required package is not fully installed. Retry setup to finish installing it.” Localize through the frontend lane; BC does not edit UI or localization output.

Evidence: `docs/qa/BC1-2026-10-07/README.md`.
