#!/usr/bin/env python3
"""Validate live FQ3 evidence and generate its separate protocol matrix."""

import argparse
import copy
from datetime import datetime
import json
import math
import os
from pathlib import Path
import re
import tempfile


REPO = Path(__file__).resolve().parents[3]
EVIDENCE_DIRECTORY = "docs/qa/FQ3-2026-10-08"
PREVIOUS_EVIDENCE_DIRECTORY = "docs/qa/FQ3b-2026-10-08"
CURRENT_EVIDENCE_DIRECTORY = "docs/qa/FQ3c-2026-10-08"
HISTORICAL_EVIDENCE_DIRECTORIES = (EVIDENCE_DIRECTORY, PREVIOUS_EVIDENCE_DIRECTORY)
EVIDENCE_DIRECTORIES = (*HISTORICAL_EVIDENCE_DIRECTORIES, CURRENT_EVIDENCE_DIRECTORY)
MODEL_SCOPED_DIRECTORIES = {
    2196: PREVIOUS_EVIDENCE_DIRECTORY,
    2197: CURRENT_EVIDENCE_DIRECTORY,
    2202: "docs/qa/FQ3d-2026-10-09",
}
CERTIFIED_BUILDS = (2195, 2196, 2197, 2202)
MATRIX_PATH = "docs/verification/agent-certification-matrix.json"
MARKDOWN_PATH = "docs/verification/agent-certification-matrix.md"
PROTOCOL_SCOPE = "in-app Ubuntu protocol; no UI/restart/install qualification"
VERSIONS = {"opencode": "1.18.32", "opencode2": "2.0.10"}
CAPABILITIES = (
    "version", "create", "models", "modelSwitch", "stream", "abort",
    "reconnect", "permissionAllow", "permissionDeny", "image", "cards",
)
ALL_CAPABILITIES = (*CAPABILITIES, "protocolSwitch")
PASS_FACTS = {
    "modelSwitch": ("selectionObserved",),
    "stream": ("streamedDelta", "completedReply"),
    "abort": ("interrupted", "usableAfterAbort"),
    "reconnect": ("refetched",),
    "permissionAllow": ("requestObserved", "replyObserved"),
    "permissionDeny": ("requestObserved", "replyObserved"),
    "image": ("imageAnswerVerified",),
    "cards": ("cardsToolCall", "answerReceipt"),
    "protocolSwitch": ("bothHistoriesPreserved", "freshClients"),
}
MAX_RUN_BYTES = 64 * 1024
MODEL_REFERENCE = r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}"


class InvalidEvidence(ValueError):
    """Fixed guidance only; never include an untrusted input value."""


def require(condition, message):
    if not condition:
        raise InvalidEvidence(message)


def exact_keys(value, keys):
    require(type(value) is dict and set(value) == set(keys),
            "Evidence fields do not match the FQ3 schema.")


def validate_result(value, capability):
    exact_keys(value, ("state", "code", "facts"))
    require(value["state"] in ("pass", "fail"), "Invalid assertion state.")
    code = value["code"]
    require(type(code) is str and re.fullmatch(r"[a-z][a-z0-9_]{0,63}", code),
            "Invalid fixed assertion code.")
    facts = value["facts"]
    require(type(facts) is dict and len(facts) <= 32, "Invalid assertion facts.")
    for key, fact in facts.items():
        require(type(key) is str and re.fullmatch(r"[a-z][A-Za-z0-9]{0,63}", key),
                "Invalid fact identifier.")
        require(type(fact) in (bool, int, float),
                "Only numeric or boolean assertion facts are allowed.")
        if type(fact) is not bool:
            require(abs(fact) <= 10**12 and math.isfinite(fact),
                    "Assertion fact is outside its bounded range.")
    if value["state"] == "pass":
        require(code == "verified" and facts.get("asserted") is True,
                "A pass requires a successful explicit assertion.")
        require(all(facts.get(key) is True for key in PASS_FACTS.get(capability, ())),
                "A capability pass lacks its required observation.")
    else:
        require(code != "verified" and facts.get("asserted") is not True,
                "A failed assertion cannot claim verification.")
    return copy.deepcopy(value)


def validate_run(run):
    exact_keys(run, (
        "schemaVersion", "runID", "device", "appBuild", "sourceRevision",
        "startedAt", "scope", "engines", "protocolSwitch", "evidence",
        "attestation",
    ))
    require(type(run["schemaVersion"]) is int and run["schemaVersion"] in (1, 2),
            "Unsupported FQ3 evidence version.")
    require(type(run["runID"]) is str and
            re.fullmatch(r"fq3-[A-Za-z0-9_-]{1,96}", run["runID"]),
            "Invalid FQ3 run identifier.")
    require(run["device"] == "emulator-5554", "Evidence is from another device.")
    require(type(run["appBuild"]) is int and run["appBuild"] in CERTIFIED_BUILDS,
            "Evidence is from another app build.")
    if run["schemaVersion"] == 2:
        directory = MODEL_SCOPED_DIRECTORIES.get(run["appBuild"])
        require(directory is not None and
                run["evidence"] == f'{directory}/{run["runID"]}.json',
                "Model-scoped evidence requires its exact build and directory.")
    else:
        require(run["appBuild"] in (2195, 2196) and run["evidence"] in (
            f'{directory}/{run["runID"]}.json'
            for directory in HISTORICAL_EVIDENCE_DIRECTORIES
        ), "Schema one is reserved for historical evidence.")
    require(run["scope"] == "phone-runtime", "Live phone-runtime evidence is required.")
    require(type(run["sourceRevision"]) is str and
            re.fullmatch(r"[0-9a-f]{40}", run["sourceRevision"]),
            "Invalid source revision.")
    require(type(run["startedAt"]) is str and
            re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z",
                         run["startedAt"]), "Invalid UTC run time.")
    try:
        datetime.fromisoformat(run["startedAt"].replace("Z", "+00:00"))
    except ValueError:
        raise InvalidEvidence("Invalid UTC run time.") from None
    require(run["evidence"] in (
        f'{directory}/{run["runID"]}.json' for directory in EVIDENCE_DIRECTORIES
    ),
            "Evidence must use its fixed run path.")
    attestation = run["attestation"]
    attestation_keys = (
        "runtime", "transport", "live", "appUID", "versionProbeUID",
        "buildVerified", "credentialSource",
    )
    if run["schemaVersion"] == 2:
        attestation_keys += ("cleanupCompleted",)
    exact_keys(attestation, attestation_keys)
    if run["schemaVersion"] == 2:
        require(type(attestation["cleanupCompleted"]) is bool,
                "New evidence requires an explicit cleanup acknowledgment.")
    require(attestation["runtime"] == "in-app-ubuntu" and
            attestation["transport"] == "adb-forward" and
            attestation["live"] is True and attestation["buildVerified"] is True and
            attestation["credentialSource"] == "runtime-launch-config",
            "Live in-app runtime attestation is required.")
    app_uid = attestation["appUID"]
    require(type(app_uid) is int and 10000 <= app_uid <= 2147483647 and
            10000 <= app_uid % 100000 <= 19999,
            "An Android application UID is required.")
    require(type(attestation["versionProbeUID"]) is int and
            attestation["versionProbeUID"] == app_uid,
            "Version probes must run under the application UID.")
    exact_keys(run["engines"], VERSIONS)
    for engine_id, expected in VERSIONS.items():
        engine = run["engines"][engine_id]
        engine_keys = ("expectedVersion", "observedVersion", "results")
        if run["schemaVersion"] == 2:
            engine_keys += ("modelSelection",)
        exact_keys(engine, engine_keys)
        if run["schemaVersion"] == 2:
            selection = engine["modelSelection"]
            exact_keys(selection, ("source", "requested"))
            require(selection["source"] in ("explicit", "server-default"),
                    "Invalid base model selection source.")
            requested = selection["requested"]
            require((selection["source"] == "server-default" and requested is None) or
                    (selection["source"] == "explicit" and type(requested) is str and
                     re.fullmatch(MODEL_REFERENCE, requested)),
                    "Invalid public base model reference.")
        require(engine["expectedVersion"] == expected, "Unexpected engine pin.")
        observed = engine["observedVersion"]
        require(observed is None or (type(observed) is str and
                re.fullmatch(r"[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}", observed)),
                "Observed version must be a bounded version identifier.")
        exact_keys(engine["results"], CAPABILITIES)
        for capability in CAPABILITIES:
            validate_result(engine["results"][capability], capability)
        require(engine["results"]["version"]["state"] != "pass" or observed == expected,
                "Version pass does not match the expected pin.")
    validate_result(run["protocolSwitch"], "protocolSwitch")
    if run["schemaVersion"] == 2 and not attestation["cleanupCompleted"]:
        require(run["protocolSwitch"]["state"] == "fail" and all(
            result["state"] == "fail" for engine in run["engines"].values()
            for result in engine["results"].values()
        ), "Unacknowledged cleanup cannot qualify a capability pass.")
    return copy.deepcopy(run)


def apply_run(matrix, run):
    run = validate_run(run)
    require(type(matrix) is dict and type(matrix.get("agents")) is list,
            "Existing certification matrix is unavailable.")
    updated = copy.deepcopy(matrix)
    for engine_id in VERSIONS:
        matches = [agent for agent in updated["agents"]
                   if type(agent) is dict and agent.get("id") == engine_id]
        require(len(matches) == 1, "Existing engine row is missing or duplicated.")
        engine = run["engines"][engine_id]
        matches[0]["protocolCertification"] = {
            "scope": PROTOCOL_SCOPE,
            "expectedVersion": engine["expectedVersion"],
            "observedVersion": engine["observedVersion"],
            "deviceBuild": run["appBuild"],
            "runID": run["runID"],
            "evidence": run["evidence"],
            "capabilities": {
                **copy.deepcopy(engine["results"]),
                "protocolSwitch": copy.deepcopy(run["protocolSwitch"]),
            },
        }
        if run["schemaVersion"] == 2:
            matches[0]["protocolCertification"]["modelSelection"] = copy.deepcopy(
                engine["modelSelection"])
    return updated


def markdown_text(value):
    return str(value).replace("\n", " ").replace("|", "\\|")


def state_symbol(state):
    return {
        "pass": "✅", "partial": "🟡", "untested": "·", "off": "⛔", "n/a": "—",
        "fail": "❌",
    }.get(state, "🔒" if state.startswith("blocked:") else markdown_text(state))


def table(headers, rows):
    return ["| " + " | ".join(headers) + " |",
            "|" + "|".join("---" for _ in headers) + "|"] + [
                "| " + " | ".join(markdown_text(value) for value in row) + " |"
                for row in rows
            ]


def render_markdown(matrix):
    columns = matrix["columns"]
    agents = matrix["agents"]
    lines = [
        "# Agent certification matrix", "",
        f'Updated {matrix["updated"]} · device: {matrix["device"]}.', "",
        matrix["rule"], "", matrix["metadata"], "",
        "Machine-readable copy: [agent-certification-matrix.json](agent-certification-matrix.json) "
        "(BA4 reads it). Legend: ✅ pass · 🟡 partial · · untested · 🔒 needs an account "
        "(owner item OW1) · ⛔ turned off · — not applicable.", "",
    ]
    lines += table(
        ["Agent", "Version", "Route", *(column["id"] for column in columns)],
        [[agent["name"], agent.get("agentVersion", ""), agent["route"],
          *(state_symbol(agent["cells"][column["id"]]["state"]) for column in columns)]
         for agent in agents],
    )
    lines += ["", "## What each column means", ""]
    lines += [f'- **{column["id"]}** — {column["means"]}' for column in columns]
    lines += ["", "## Evidence", ""]
    for agent in agents:
        lines += [f'**{agent["name"]}**', ""]
        for key, cell in agent["cells"].items():
            if cell.get("evidence") is not None:
                lines.append(f'- {key}: {cell["state"]} — {cell["evidence"]}')
        lines += [""]
    lines += [
        "## How to fill a cell", "",
        "Run the scenario on a device, save a small JPG or log under `docs/qa/<item>-<date>/`, "
        "set the cell to `pass` with that path in the JSON, record agentVersion/helperVersion, "
        "then regenerate this table. A cell never turns `pass` from a unit test alone.", "",
        "## FQ3 protocol certification", "",
        PROTOCOL_SCOPE + ".", "",
        "These results apply only to the recorded emulator build and in-app runtime. "
        "They do not update BA4 cells or qualify UI, installation, app/server restart "
        "or other CPU architectures. protocolSwitch means fresh-client connection switching "
        "with both owned histories refetched, not app UI switching.", "",
    ]
    certified = [agent for agent in agents if "protocolCertification" in agent]
    rows = []
    for agent in certified:
        protocol = agent["protocolCertification"]
        selection = protocol.get("modelSelection")
        base_model = ("historical: not recorded" if selection is None else
                      "server-default" if selection["source"] == "server-default" else
                      "explicit: " + selection["requested"])
        rows.append([
            agent["name"], protocol["expectedVersion"],
            protocol["observedVersion"] or "not observed", protocol["deviceBuild"],
            protocol["runID"], base_model,
            *(state_symbol(protocol["capabilities"][key]["state"]) for key in ALL_CAPABILITIES),
        ])
    lines += table(["Agent", "Expected", "Observed", "Build", "Run", "Base model scope", *ALL_CAPABILITIES], rows)
    lines += ["", "Model-dependent passes apply to the recorded base model selection. "
              "An explicit selection does not qualify server-default inference or other base models."]
    lines += ["", "FQ3 legend: ✅ protocol assertion passed · ❌ assertion failed or prerequisite missing.", ""]
    for agent in certified:
        protocol = agent["protocolCertification"]
        evidence_link = "../" + protocol["evidence"].removeprefix("docs/")
        lines += [f'**{agent["name"]}** — [{protocol["runID"]}]({evidence_link})', ""]
        for capability, result in protocol["capabilities"].items():
            facts = json.dumps(result["facts"], sort_keys=True, separators=(",", ":"))
            lines.append(f'- {capability}: {result["state"]} — `{result["code"]}`; facts `{facts}`')
        lines += [""]
    return "\n".join(lines)


def unique_object(pairs):
    value = {}
    for key, item in pairs:
        require(key not in value, "Duplicate JSON fields are not accepted.")
        value[key] = item
    return value


def read_json(path, maximum_bytes=None):
    if maximum_bytes is not None:
        require(path.stat().st_size <= maximum_bytes, "Run evidence exceeds its size budget.")
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)


def atomic_write(path, text):
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                     prefix=".fq3-", delete=False) as temporary:
        pending = Path(temporary.name)
        try:
            temporary.write(text)
            temporary.flush()
            os.fsync(temporary.fileno())
        except BaseException:
            pending.unlink(missing_ok=True)
            raise
    try:
        os.replace(pending, path)
    finally:
        pending.unlink(missing_ok=True)


def update_paths(root, run_path):
    root = Path(root).resolve()
    run_path = Path(run_path)
    if not run_path.is_absolute():
        run_path = root / run_path
    require(not run_path.is_symlink(), "Evidence must be a regular local run file.")
    run = validate_run(read_json(run_path, MAX_RUN_BYTES))
    require(run_path.resolve() == root / run["evidence"],
            "The supplied file does not match its evidence path.")
    matrix_path = root / MATRIX_PATH
    updated = apply_run(read_json(matrix_path), run)
    markdown = render_markdown(updated)
    encoded = json.dumps(updated, ensure_ascii=False, indent=1) + "\n"
    atomic_write(matrix_path, encoded)
    atomic_write(root / MARKDOWN_PATH, markdown)
    return updated


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        update_paths(REPO, args.run)
    except (InvalidEvidence, ValueError, TypeError, KeyError, OSError, OverflowError):
        parser.exit(1, "FQ3 matrix update refused: invalid evidence or unavailable files.\n")
    print("FQ3 protocol evidence recorded; existing certification cells preserved.")


if __name__ == "__main__":
    main()
