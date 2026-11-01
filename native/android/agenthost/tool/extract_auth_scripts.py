#!/usr/bin/env python3
"""Writes the auth-probe fixtures the Flutter test wrote at run time (test/phone_agent_auth_native_test.dart).

Reads lib/builtin/agents/agent_scripts.dart (AgentPhoneScripts.authProbe / signOut) without a Dart SDK: the template
of _authScript and the raw _authProbe body are copied, `\\n` in the non-raw template becomes a backslash-n, and the
three interpolations are filled exactly as Dart does (`_quote` = POSIX single quotes). PhoneAgentAuthRequest's own
SHA-256 check (request-admission) proves the output is byte-identical to what Dart authors.

usage: extract_auth_scripts.py <repo root> <output dir>
"""
import pathlib
import re
import sys

root, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
dart = (root / "lib/builtin/agents/agent_scripts.dart").read_text()
template = dart.split("static String _authScript(", 1)[1].split("return '''\n", 1)[1].split("''';", 1)[0]
probe = dart.split("static const _authProbe = r'''\n", 1)[1].split("''';", 1)[0]
assert "${" not in probe
template = template.replace("\\\\n", "\\n")  # Dart '\\n' in a non-raw string is backslash + n
executables = {"claude": "claude", "codex": "codex", "gemini": "gemini", "qwen": "qwen", "kilo": "kilo",
               "cline": "cline", "goose": "goose", "omp-acp": "omp", "fx": "fx"}
home = "/home/oc/.oc-profiles/synthetic-profile"
prefix = (f"export HOME='{home}' CLAUDE_CONFIG_DIR='{home}/claude' CODEX_HOME='{home}/codex' "
          "DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1\n")
quote = lambda text: "'" + text.replace("'", "'\\''") + "'"
out.mkdir(parents=True, exist_ok=True)
for agent, exe in executables.items():
    for logout in [False] + ([True] if agent in ("claude", "fx") else []):
        action = "logout" if logout else "probe"
        body = (template.replace("${_quote(agent.id)}", quote(agent))
                .replace("${_quote(recipe.executable)}", quote(exe))
                .replace("${signOut ? 'logout' : 'probe'}", action)
                .replace("$_authProbe", probe))
        assert not re.search(r"\$\{|\$_", body), body[:200]
        (out / f"{agent}-{action}.txt").write_text(prefix + body)
