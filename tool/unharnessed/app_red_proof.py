"""Hosted-only, temporary reversions of this slice's guarded behaviors."""
import json
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET

native = Path.cwd()
root = native.parent
assert (native / "gradlew").is_file()


def results():
    return [p for p in native.rglob("TEST-*.xml") if "test-results" in p.parts and "unharnessed-green-results" not in p.parts]


if "--verify" in sys.argv:
    failed = set()
    for path in results():
        suite = ET.parse(path).getroot()
        for case in suite.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                match = re.search(r"B-AGENT-UH-(\d+)", case.get("name", ""))
                if match:
                    failed.add(match[1])
    expected = {"010", "011", "012", "013", "020", "021", "022", "023", "030", "031", "032", "033", "034"}
    (native / "unharnessed-red-proof.json").write_text(json.dumps({"expectedRows": sorted(expected), "failedAsExpected": sorted(failed)}, indent=2) + "\n")
    assert expected <= failed, "Missing expected red rows: " + str(sorted(expected - failed))
    raise SystemExit(0)

counts = {"tests": 0, "failures": 0, "errors": 0}
for path in results():
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.get(key, "0"))
    target = native / "unharnessed-green-results" / path.relative_to(native)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, target)
assert counts["failures"] == counts["errors"] == 0, counts
(native / "unharnessed-green-totals.json").write_text(json.dumps(counts, indent=2) + "\n")


def mutate(relative, before, after):
    path = root / relative
    text = path.read_text()
    assert before in text, relative + ": mutation anchor changed"
    path.write_text(text.replace(before, after, 1))


agents = "native/engine/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/agents/"
chat = "native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/"
mutate(agents + "catalog/AgentPins.kt", "AgentCommandApproval.NeverAsks", "AgentCommandApproval.Unknown")
mutate(agents + "catalog/AgentPins.kt", "AgentSetupMethod.KeyOrLocalServer", "AgentSetupMethod.SignInOrProvider")
path = root / (agents + "providers/OwnProvider.kt")
text, count = re.subn(r"keyOrLocal\s*&&\s*key\.isEmpty\(\)", "false", path.read_text(), count=1)
assert count == 1
path.write_text(text)
mutate(agents + "providers/UnharnessedProvider.kt", "os.replace(temporary, target)", "os.remove(temporary)")
mutate(agents + "scripts/AgentRemovalScripts.kt", "for data in private_data: shutil.rmtree(data)", "for data in private_data: pass")
mutate(chat + "header/approval/ApprovalPresenter.kt", "&& !neverAsks.value", "")
mutate(chat + "header/approval/ApprovalPresenter.kt", "neverAsks = neverAsks.value", "neverAsks = false")
mutate(chat + "header/ui/ApprovalChip.kt", "if (state.neverAsks)", "if (false && state.neverAsks)")
mutate("native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PhoneAgentRules.kt", "signIn != AgentSignInPhase.SignedIn", "false")
mutate("tool/unharnessed/adapter.py", 'return dict(frame, params=dict(params, update=dict(update, rawInput=dict(raw, command=raw["code"]))))', "return frame")
