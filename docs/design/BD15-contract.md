Availability: `readAttachment(sessionId, id)` remains unavailable; no capability is enabled because the owner-visible bare-ID-to-transcript-image mapping is unproven.
Presentation: retain the safe unresolved-image placeholder; do not infer SHA-256 identity or read another transcript to obtain a thumbnail.
Unblock: prove the exact runtime's ID mapping and retained image block, then bind current profile/project/provider session and enforce bounded image-only reads returning bytes or null; evidence is in `docs/qa/BD15-2026-10-09/README.md`.
