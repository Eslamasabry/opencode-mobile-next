#!/usr/bin/env python3
"""fx_oc2_b.py - FX-OC2-B: a second FX-OC2 with its own data root, ports and sessions.

Same launcher as fx_oc2.py (see its docstring for the layout, control actions and
ready.json fields), with:

* ports from ports.json ``FX-OC2-B``; data root ``<run-dir>/FX-OC2-B/``;
* password from FX_OC2_B_PASSWORD; env names use the FX_OC2_B_ prefix
  (FX_OC2_B_BIN falls back to FX_OC2_BIN);
* the shared session ids of FX-OC2 (``fx_oc2.SHARED_IDS``) with this server's own
  titles, so the same id on two servers is two rows;
* its own placeholder-ISO-titled and leaked-markup-titled sessions, and a session in
  a third project ``web`` (E2E-36: a different project).
"""

from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
for _p in (str(HERE), str(HERE.parent)):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import fx_oc2  # noqa: E402
from fx_oc2 import ISO_TITLE_B, MARKUP_TITLE, SHARED_IDS, Profile, SeedSession, sid  # noqa: E402

B_SESSIONS = [
    SeedSession("shared-login", SHARED_IDS["shared-login"], "Fix the login bug (second server)"),
    SeedSession("shared-notes", SHARED_IDS["shared-notes"], "Write the release notes (second server)"),
    SeedSession("iso-title", sid("b", 1), ISO_TITLE_B),
    SeedSession("markup-title", sid("b", 2), MARKUP_TITLE),
    SeedSession("web-page", sid("b", 3), "Tidy the web page", "web"),
    SeedSession("other-review", sid("b", 4), "Review the other project", "other"),
]

OC2_B = Profile("FX-OC2-B", "FX_OC2_B", B_SESSIONS, projects=("app", "other", "web"), label="FX-OC2-B")


def main(argv: list[str] | None = None) -> int:
    return fx_oc2.main(OC2_B, argv)


if __name__ == "__main__":
    sys.exit(main())
