"""Seed a settled OC1 upgrade fixture in the app-managed project mount.

The caller owns the emulator lock and exact baseline identity. Never start a
server, enroll a provider, copy a credential, or write into the hidden rootfs
project directory. Save the private receipt before posting the no-reply message
so an interrupted prompt leaves an exact session handle for manual recovery.
"""

import re
from .common import DriverFailure


def seed_history_fixture(ports, save_receipt):
    ports.require_idle_setup()
    ports._connect("opencode")
    states = ports.protocol("GET", "/session/status")
    if type(states) is not dict or any(
        type(s) is not dict or s.get("type") != "idle" for s in states.values()
    ):
        raise DriverFailure("another_live_turn")
    directory = ports.prepare_fixture_project()
    title = ports.run_id + "-retained"
    session = ports.protocol(
        "POST", "/session", query={"directory": directory}, body={"title": title}
    )
    if (
        type(session) is not dict
        or not isinstance(session.get("id"), str)
        or not re.fullmatch(r"[A-Za-z0-9_-]{1,100}", session["id"])
        or session.get("title") != title
        or session.get("directory") != directory
    ):
        raise DriverFailure("fixture_seed_invalid")
    receipt = {
        "engine": "opencode",
        "directory": directory,
        "sessions": [{"id": session["id"], "title": title}],
    }
    save_receipt(receipt)
    ports.history = receipt
    message = ports.protocol(
        "POST",
        "/session/" + session["id"] + "/message",
        query={"directory": directory},
        body={
            "noReply": True,
            "parts": [
                {
                    "type": "text",
                    "text": "FQ9_RETAINED_HISTORY: local upgrade fixture; do not execute anything.",
                }
            ],
        },
    )
    info = message.get("info") if type(message) is dict else None
    if (
        type(info) is not dict
        or info.get("sessionID") != session["id"]
        or info.get("role") != "user"
        or not isinstance(info.get("id"), str)
        or not re.fullmatch(r"[A-Za-z0-9_-]{1,100}", info["id"])
    ):
        raise DriverFailure("fixture_seed_invalid")
    return receipt
