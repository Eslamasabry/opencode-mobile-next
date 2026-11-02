# Regenerates agent-pins.json from the Flutter freeze tip (run from the repo root, only with an app release, S3-PINS):
#   python3 -I native/engine/agents/pins/generate_pins.py lib/domain/agent_catalog.dart \
#     lib/builtin/agents/paseo_scripts.dart lib/paseo/tested_versions.dart > native/engine/agents/pins/agent-pins.json
# then regenerate catalog/AgentPinsJson.kt (the file is embedded byte for byte; AgentPinsParityTest checks both).
import re, json, sys
src = open(sys.argv[1]).read()
body = src[src.index('AgentCatalog _builtInCatalog()'):]
body = body.replace("'$github", "'https://github.com")
paseo = open(sys.argv[2]).read()
def const(name):
    m = re.search(r"static const %s =\s*'([^']*)'" % name, paseo); return m.group(1)
def strs(s): return re.findall(r"'([^']*)'", s)
def parse_artifact(call):
    kind = call[0]
    args = call[1]
    s = strs(args)
    ints = [int(x) for x in re.findall(r"(?<![\w.])(\d{4,})(?![\w.])", args)]
    if kind == 'binary':
        url, sha = s[0], s[1]
        return dict(url=url, sha256=sha, format='executable', member=None, downloadBytes=ints[-1], installedBytes=ints[-1])
    url, sha, member = s[0], s[1], s[2]
    fmt = 'npmTarGz' if 'npmTarGz' in args else 'tarGz'
    db = re.search(r'bytes:\s*(\d+)', args); ib = re.search(r'installedBytes:\s*(\d+)', args)
    return dict(url=url, sha256=sha, format=fmt, member=member,
                downloadBytes=int(db.group(1)) if db else None, installedBytes=int(ib.group(1)) if ib else None)
def balanced(text, start):
    depth = 0; i = start
    while True:
        c = text[i]
        if c == '(': depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0: return text[start+1:i], i
        elif c == "'":
            j = text.index("'", i+1); i = j
        i += 1
named = {}
for name in ['gemini', 'qwen']:
    m = re.search(r"final %s = archive\(" % name, body)
    args, _ = balanced(body, m.end()-1)
    named[name] = parse_artifact(('archive', args))
agents = []
pos = body.index('agents: [')
while True:
    m = re.compile(r"\n      entry\(").search(body, pos)
    if not m: break
    args, end = balanced(body, m.end()-1)
    pos = end
    head = strs(args[:args.index('AgentInstallRecipe')])
    aid, name, provider = head[0], head[1], head[2]
    route = re.search(r'AgentRoute\.(\w+)', args).group(1)
    signin = re.search(r'AgentSignInMethod\.(\w+)', args).group(1)
    rargs_start = args.index('AgentInstallRecipe(') + len('AgentInstallRecipe')
    rargs, _ = balanced(args, rargs_start)
    version = re.search(r"version:\s*'([^']+)'", rargs).group(1)
    exe = re.search(r"executable:\s*'([^']+)'", rargs).group(1)
    la = re.search(r"launchArgs:\s*\[([^\]]*)\]", rargs); sa = re.search(r"signInArgs:\s*\[([^\]]*)\]", rargs)
    arts = {}
    for arch in ['arm64', 'x64']:
        am = re.search(r"AgentArchitecture\.%s:\s*" % arch, rargs)
        rest = rargs[am.end():]
        if rest.startswith(('binary(', 'archive(')):
            kind = rest[:rest.index('(')]
            a, _ = balanced(rest, rest.index('('))
            arts[arch] = parse_artifact((kind, a))
        else:
            arts[arch] = named[re.match(r'(\w+)', rest).group(1)]
    agents.append(dict(id=aid, name=name, route=route, providerId=provider, signInMethod=signin,
        recipe=dict(version=version, executable=exe, launchArgs=strs(la.group(1)) if la else [],
                    signInArgs=strs(sa.group(1)) if sa else [], artifacts=arts)))
tv = open(sys.argv[3]).read()
tested = re.search(r"claudeCodeTestedVersions = \{([^}]*)\}", tv).group(1)
paseo_tested = re.search(r"paseoTestedVersions = \{([^}]*)\}", tv).group(1)
out = dict(schema=1,
  source="lib/domain/agent_catalog.dart _builtInCatalog(), lib/builtin/agents/paseo_scripts.dart, lib/paseo/tested_versions.dart (copied verbatim; a pin moves only with an app release)",
  paseo=dict(version=const('version'), packageLockSha256=const('packageLockSha256'), cliSha256=const('cliSha256'),
             payloadSha256=const('payloadSha256'), codeTreeSha256=const('codeTreeSha256')),
  node=dict(version=const('nodeVersion'), sha256=dict(arm64=const('nodeArm64Sha256'), x64=const('nodeX64Sha256'))),
  claudeCodeTestedVersions=strs(tested), paseoTestedVersions=strs(paseo_tested), agents=agents)
print(json.dumps(out, indent=2))
