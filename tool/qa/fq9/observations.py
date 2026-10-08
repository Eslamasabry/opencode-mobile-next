"""Pure, scoped projections; never return raw screen/service/credential text."""

import hashlib
import json
import re
import xml.etree.ElementTree as ET
from .common import PACKAGE, DriverFailure


def service_foreground(output, component):
    # Match a single ServiceRecord block; a foreign service's foreground flag
    # must not qualify our component. No notification/status text is exported.
    package, name = component.split("/", 1)
    short = name.removeprefix(".")
    component_pattern = (
        re.escape(package) + r"/(?:\.|" + re.escape(package) + r"\.)" + re.escape(short)
    )
    blocks = re.split(r"(?=\s*\* ServiceRecord\{)", output)
    for block in blocks:
        header = block.splitlines()[0:2]
        if any(re.search(component_pattern + r"(?:[\s}])", line) for line in header):
            return re.search(r"\bisForeground=true\b", block) is not None
    return False


def ongoing_notification(output):
    for block in re.split(r"(?=\s*NotificationRecord\()", output):
        header = block.splitlines()[0:2]
        if not any(
            re.search(r"\bpkg=" + re.escape(PACKAGE) + r"\b", line)
            and re.search(r"\bid=4747\b", line)
            for line in header
        ):
            continue
        flags = re.search(r"\bflags=0x([0-9a-fA-F]+)\b", block)
        if (
            flags
            and int(flags[1], 16) & 2
            and re.search(r"\bchannel(?:Id)?=opencode_live_connection\b", block)
        ):
            return True
    return False


def process_start(stat):
    end = stat.rfind(") ")
    fields = stat[end + 2 :].split() if end >= 0 else []
    return fields[19] if len(fields) > 19 and fields[19].isdigit() else None


def socket_identity(text, uid):
    matches = []
    for line in text.splitlines():
        fields = line.split()
        if len(fields) < 10 or fields[3] != "0A":
            continue
        address = fields[1].upper()
        if address not in ("0100007F:1001", "00000000000000000000000001000000:1001"):
            continue
        if fields[7] == str(uid) and fields[9].isdigit():
            matches.append(fields[9])
    if len(matches) != 1:
        raise DriverFailure("app_managed_socket_unavailable")
    return matches[0]


def profile_projection(raw):
    try:
        tree = ET.fromstring(raw)
        if tree.tag != "map":
            raise ValueError()
        values = {}
        for node in tree:
            key = node.get("name")
            if not key or key in values:
                raise ValueError()
            values[key] = (
                node.tag,
                node.get("value"),
                node.text or "",
                tuple(sorted(child.text or "" for child in node)),
            )
        profiles = values.get("flutter.oc.profiles")
        if profiles is None or profiles[0] != "string":
            raise DriverFailure("preservation_fixture_required")
        parsed = json.loads(profiles[2])
        if (
            type(parsed) is not list
            or not parsed
            or any(type(p) is not dict or not p.get("id") for p in parsed)
        ):
            raise DriverFailure("preservation_fixture_required")
        # Semantic JSON comparison tolerates serialization order, while exact
        # metadata retention remains deliberately stricter than migration proof.
        profile_digest = hashlib.sha256(
            json.dumps(parsed, sort_keys=True).encode()
        ).digest()
        keys = (
            "flutter.oc.appearance",
            "flutter.oc.themePack",
            "flutter.oc.effectsMotion",
        )
        prefixes = (
            "flutter.oc.model.",
            "flutter.oc.modelExplicit.",
            "flutter.oc.variant.",
            "flutter.oc.sessionModels.",
            "flutter.oc.draft.",
            "flutter.oc.offlineQueue",
        )
        selected = {
            k: v for k, v in values.items() if k in keys or k.startswith(prefixes)
        }
        if not selected:
            raise DriverFailure("preservation_fixture_required")
        return profile_digest, selected
    except DriverFailure:
        raise
    except (ValueError, TypeError, ET.ParseError):
        raise DriverFailure("preferences_invalid") from None
