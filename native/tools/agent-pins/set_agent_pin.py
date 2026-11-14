#!/usr/bin/env python3
"""Move one agent's pin in agent-pins.json and its byte-for-byte copy AgentPinsJson.kt. Edits files only; fetches nothing.

Run from the repository root: python3 -I native/tools/agent-pins/set_agent_pin.py ID VERSION
  ARM64_URL ARM64_SHA256 ARM64_DOWNLOAD_BYTES ARM64_INSTALLED_BYTES X64_URL X64_SHA256 X64_DOWNLOAD_BYTES X64_INSTALLED_BYTES
Every value comes from tools/agent-pins/agent_artifact.py output (sha256, downloadBytes, memberBytes for a tar.gz, the
registry's unpackedSize for an npm package, the file size for a plain executable). Tested-version lists are not touched.
"""
import json, sys

ROOT = "native/engine/agents/"
JSON_PATH = ROOT + "pins/agent-pins.json"
KT_PATH = ROOT + "src/commonMain/kotlin/io/github/eslamasabry/oc/engine/agents/catalog/AgentPinsJson.kt"


def main():
    agent_id, version, *rest = sys.argv[1:]
    if len(rest) != 8:
        sys.exit("need 8 artifact values")
    text = open(JSON_PATH).read()
    pins = json.loads(text)
    agent = next(a for a in pins["agents"] if a["id"] == agent_id)
    agent["recipe"]["version"] = version
    for arch, vals in (("arm64", rest[:4]), ("x64", rest[4:])):
        art = agent["recipe"]["artifacts"][arch]
        url, sha, dl, inst = vals
        if len(sha) != 64 or any(c not in "0123456789abcdef" for c in sha):
            sys.exit("bad sha256")
        art["url"], art["sha256"], art["downloadBytes"], art["installedBytes"] = url, sha, int(dl), int(inst)
    new = json.dumps(pins, indent=2) + "\n"
    open(JSON_PATH, "w").write(new)
    kt = open(KT_PATH).read()
    i = kt.index('"""') + 3
    j = kt.rindex('"""')
    assert kt[i:j] == text
    open(KT_PATH, "w").write(kt[:i] + new + kt[j:])


if __name__ == "__main__":
    main()
