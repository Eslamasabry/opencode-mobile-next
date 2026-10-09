Availability: Paseo 0.9.2 has no bare attachment-ID reader; no `readAttachment(id)` API or capability is enabled (see `docs/qa/BD14-2026-10-09/README.md`).
Presentation: retain the existing unresolved-image placeholder for bare 64-hex targets; do not infer a file path, URL, directory or extension from the hash.
Prerequisite: thumbnail wiring needs a scoped daemon ID resolver or trusted full file reference; its future capability-gated reader must cap bytes, accept supported images only, and return null when unavailable.
