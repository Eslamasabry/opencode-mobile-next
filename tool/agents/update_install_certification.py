#!/usr/bin/env python3
"""Validate signed-out device install reports and generate the reviewed matrix."""

import argparse
import copy
import importlib.util
import json
import math
from pathlib import Path
import re


REPO = Path(__file__).resolve().parents[2]
_SPEC = importlib.util.spec_from_file_location(
    "fq3_matrix_renderer", REPO / "tool/qa/fq3/update_matrix.py"
)
renderer = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(renderer)
PINS = {
    "codex": "0.160.0", "gemini": "0.62.0", "qwen": "0.24.7",
    "goose": "1.53.0", "omp-acp": "18.5.1", "fx": "0.0.12",
}
RESULTS = (
    "install", "version", "signedOut", "phoneCheck", "launchNoAccount",
    "cancelRetry", "lowStorage", "uninstall",
)
CELL_RESULTS = ("install", "version", "signedOut")
STATES = {"pass", "fail", "partial", "untested", "blocked", "n/a"}
PASS_FACTS = {
    "install": ("installedViaApp", "checksumVerified"),
    "signedOut": ("namedSignedOut",),
    "phoneCheck": ("completed",),
    "launchNoAccount": ("plainError", "noHang"),
    "cancelRetry": ("cancelObserved", "retryCompleted"),
    "lowStorage": ("guardRefused", "noDownload"),
    "uninstall": ("removedViaApp", "leftoversRemoved", "noOrphans"),
}
MAX_REPORT_BYTES = 64 * 1024
SCOPE = "App installation on x64 emulator; signed-out, no account qualification"


class InvalidEvidence(ValueError):
    """Fixed messages avoid reflecting credentials or arbitrary input."""


def require(condition, message):
    if not condition:
        raise InvalidEvidence(message)


def _exact_keys(value, required, optional=()):
    require(type(value) is dict and set(required) <= set(value) and
            set(value) <= set(required) | set(optional),
            "Report fields do not match the install evidence schema.")


def _version(value):
    return value is None or (type(value) is str and
                            re.fullmatch(r"[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}", value))


def verify_catalog_pins(root):
    catalog = (Path(root) / "lib/domain/agent_catalog.dart").read_text()
    for agent_id, version in PINS.items():
        pattern = (r"entry\(\s*'" + re.escape(agent_id) +
                   r"'(?:(?!\n\s*entry\().)*?version:\s*'([^']+)'")
        match = re.search(pattern, catalog, re.DOTALL)
        require(match is not None and match[1] == version,
                "Install report pins differ from the current catalog.")


def validate_result(value, name):
    _exact_keys(value, ("state", "facts"), ("code",))
    require(type(value["state"]) is str and value["state"] in STATES,
            "Invalid install assertion state.")
    default_code = ("verified" if value["state"] == "pass" else
                    "not_applicable" if value["state"] == "n/a" else value["state"])
    code = value.get("code", default_code)
    require(type(code) is str and re.fullmatch(r"[a-z][a-z0-9_]{0,63}", code),
            "Invalid fixed assertion code.")
    facts = value["facts"]
    require(type(facts) is dict and len(facts) <= 32, "Invalid install assertion facts.")
    for key, fact in facts.items():
        require(type(key) is str and re.fullmatch(r"[a-z][A-Za-z0-9]{0,63}", key),
                "Invalid fact identifier.")
        require(type(fact) in (bool, int, float),
                "Only boolean or numeric observation facts are accepted.")
        if type(fact) is not bool:
            require(abs(fact) <= 10**12 and math.isfinite(fact),
                    "Observation fact is outside its bounded range.")
    if value["state"] == "pass":
        require(code == "verified" and facts.get("asserted") is True,
                "A pass requires a successful explicit observation.")
        require(all(facts.get(key) is True for key in PASS_FACTS.get(name, ())),
                "An install assertion lacks required observations.")
        if name == "uninstall":
            freed = facts.get("bytesFreed")
            require(type(freed) in (int, float) and freed > 0,
                    "Uninstall pass requires observed reclaimed bytes.")
    else:
        require(code != "verified" and facts.get("asserted") is not True,
                "An incomplete assertion cannot claim verification.")
    return {"state": value["state"], "code": code, "facts": copy.deepcopy(facts)}


def validate_report(report, root=REPO):
    _exact_keys(report, (
        "schemaVersion", "runID", "device", "appBuild", "sourceRevision",
        "architecture", "agentId", "expectedVersion", "evidence", "results",
    ), ("observedVersion", "helperVersion"))
    require(type(report["schemaVersion"]) is int and report["schemaVersion"] == 1,
            "Unsupported install evidence version.")
    require(type(report["runID"]) is str and
            re.fullmatch(r"fq-install-[a-z0-9][a-z0-9_-]{0,95}", report["runID"]),
            "Invalid install run identifier.")
    require(report["device"] == "emulator-5554" and report["architecture"] == "x64",
            "Install evidence must use the authorized x64 emulator.")
    require(type(report["appBuild"]) is int and 2196 <= report["appBuild"] <= 99999,
            "Invalid install evidence app build.")
    require(type(report["sourceRevision"]) is str and
            re.fullmatch(r"[0-9a-f]{40}", report["sourceRevision"]),
            "Invalid source revision.")
    agent_id = report["agentId"]
    require(type(agent_id) is str and agent_id in PINS,
            "Install evidence agent is not in this lane.")
    require(report["expectedVersion"] == PINS[agent_id], "Unexpected agent version pin.")
    require(_version(report.get("observedVersion")) and _version(report.get("helperVersion")),
            "Invalid observed agent or helper version.")
    evidence = report["evidence"]
    require(type(evidence) is str and
            re.fullmatch(r"docs/qa/[A-Za-z0-9_/-]+\.md(?:#[A-Za-z0-9_-]+)?", evidence),
            "Install evidence must reference a local QA Markdown document.")
    evidence_path = Path(root) / evidence.split("#", 1)[0]
    require(evidence_path.is_file() and not evidence_path.is_symlink() and
            evidence_path.resolve().is_relative_to(Path(root).resolve() / "docs/qa"),
            "Install evidence document is missing or outside QA.")
    _exact_keys(report["results"], RESULTS)
    normalized = copy.deepcopy(report)
    normalized["results"] = {
        name: validate_result(report["results"][name], name) for name in RESULTS
    }
    if normalized["results"]["version"]["state"] == "pass":
        require(report.get("observedVersion") == PINS[agent_id] and
                normalized["results"]["install"]["state"] == "pass",
                "Version pass requires a successful app install and exact observed pin.")
    return normalized


def apply_report(matrix, report, root=REPO):
    report = validate_report(report, root)
    require(type(matrix) is dict and type(matrix.get("agents")) is list,
            "Existing certification matrix is unavailable.")
    updated = copy.deepcopy(matrix)
    matches = [row for row in updated["agents"]
               if type(row) is dict and row.get("id") == report["agentId"]]
    require(len(matches) == 1, "Existing install agent row is missing or duplicated.")
    row = matches[0]
    require(type(row.get("cells")) is dict, "Existing certification cells are unavailable.")
    for name in CELL_RESULTS:
        row["cells"][name] = {
            "state": report["results"][name]["state"], "evidence": report["evidence"],
        }
    row["agentVersion"] = report["expectedVersion"]
    row["architecture"] = report["architecture"]
    if report.get("helperVersion") is not None:
        row["helperVersion"] = report["helperVersion"]
    else:
        row.pop("helperVersion", None)
    row["installCertification"] = {
        "scope": SCOPE,
        **{key: report.get(key) for key in (
            "runID", "device", "appBuild", "sourceRevision", "architecture",
            "expectedVersion", "observedVersion", "helperVersion", "evidence",
        )},
        "results": copy.deepcopy(report["results"]),
    }
    return updated


def render_markdown(matrix):
    lines = [renderer.render_markdown(matrix).rstrip(), "", "## Phone-agent install certification", "", SCOPE + ".", "",
             "These cells qualify installation only. Phone check means the check completed; "
             "a signed-out agent remains unavailable for authenticated chat. No account "
             "sign-in, prompt smoke, or runtime capabilities are granted here.", ""]
    rows = []
    for agent in matrix["agents"]:
        proof = agent.get("installCertification")
        if proof is None:
            continue
        rows.append([agent["name"], proof["expectedVersion"],
                     proof["observedVersion"] or "not observed", proof["appBuild"],
                     *(renderer.state_symbol(proof["results"][name]["state"]) for name in RESULTS)])
    lines += renderer.table(["Agent", "Expected", "Observed", "Build", *RESULTS], rows)
    for agent in matrix["agents"]:
        proof = agent.get("installCertification")
        if proof is None:
            continue
        lines += ["", f'**{agent["name"]}** — {proof["runID"]}; {proof["evidence"]}', ""]
        for name, result in proof["results"].items():
            facts = json.dumps(result["facts"], sort_keys=True, separators=(",", ":"))
            lines.append(f'- {name}: {result["state"]} — `{result["code"]}`; facts `{facts}`')
    return "\n".join(lines) + "\n"


def update_paths(root, report_paths, check=False):
    root = Path(root).resolve()
    verify_catalog_pins(root)
    matrix_path = root / renderer.MATRIX_PATH
    matrix = renderer.read_json(matrix_path)
    seen = set()
    for report_path in report_paths:
        path = Path(report_path)
        if not path.is_absolute():
            path = root / path
        require(path.is_file() and not path.is_symlink(), "Report must be a regular local file.")
        report = renderer.read_json(path, MAX_REPORT_BYTES)
        report = validate_report(report, root)
        require(report["agentId"] not in seen, "Duplicate agent reports are not accepted.")
        seen.add(report["agentId"])
        matrix = apply_report(matrix, report, root)
    encoded = json.dumps(matrix, ensure_ascii=False, indent=1) + "\n"
    markdown = render_markdown(matrix)
    markdown_path = root / renderer.MARKDOWN_PATH
    if check:
        require(matrix_path.read_text() == encoded and markdown_path.read_text() == markdown,
                "Install certification outputs are stale; run the generator.")
    else:
        renderer.atomic_write(matrix_path, encoded)
        renderer.atomic_write(markdown_path, markdown)
    return matrix


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True, nargs="+", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    try:
        update_paths(REPO, args.report, args.check)
    except (InvalidEvidence, renderer.InvalidEvidence, ValueError, TypeError,
            KeyError, OSError, OverflowError):
        parser.exit(1, "Install matrix update refused: invalid evidence or unavailable files.\n")
    print("Install certification outputs match reports." if args.check else
          "Install evidence recorded; unrelated certification cells preserved.")


if __name__ == "__main__":
    main()
