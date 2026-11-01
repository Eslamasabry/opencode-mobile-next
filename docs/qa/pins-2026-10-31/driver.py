import sys, json, time, urllib.request, base64, os
port, pwfile, proj, sidfile, mode = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]
pw = open(pwfile).read().strip()
auth = "Basic " + base64.b64encode(f"opencode:{pw}".encode()).decode()
loc = "location%5Bdirectory%5D=" + proj
def call(method, path, body=None):
    sep = "&" if "?" in path else "?"
    req = urllib.request.Request(f"http://127.0.0.1:{port}{path}{sep}{loc}", method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Authorization": auth, "content-type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            t = r.read().decode(); return json.loads(t) if t else {}
    except urllib.error.HTTPError as e:
        return {"_http": e.code, "_body": e.read().decode()[:200]}
def msgs(sid):
    d = call("GET", f"/api/session/{sid}/message?limit=200")
    return d.get("data", d) if isinstance(d, dict) else d
def summary(sid):
    m = msgs(sid)
    if not isinstance(m, list): return {"error": str(m)[:200]}
    kinds = {}
    tools = 0
    for x in m:
        kinds[x.get("type") or x.get("role") or "?"] = kinds.get(x.get("type") or x.get("role") or "?", 0) + 1
        for p in (x.get("content") or x.get("parts") or []):
            if isinstance(p, dict) and p.get("type") == "tool": tools += 1
    return {"messages": len(m), "kinds": kinds, "tool_parts": tools}
def wait_idle(sid, secs=150):
    end = time.time() + secs
    while time.time() < end:
        a = call("GET", "/api/session/active")
        act = a.get("data", a) if isinstance(a, dict) else a
        ids = [x.get("sessionID") or x.get("id") for x in act] if isinstance(act, list) else list(act.keys()) if isinstance(act, dict) else []
        if sid not in ids and time.time() > end - secs + 3: return True
        time.sleep(2)
    return False
if mode == "create":
    s = call("POST", "/api/session", {"title": "rollback long", "location": {"directory": proj}})
    sid = s.get("id") or s.get("data", {}).get("id"); open(sidfile, "w").write(sid)
    print("session", sid, call("POST", f"/api/session/{sid}/model", {"model": {"id": "big-pickle", "providerID": "opencode"}}))
    prompts = ["Use your read tool to read notes.txt in the project and tell me the second line.",
               "Use a shell command to list the files in the project directory.",
               "Edit notes.txt: add a fourth line saying four. Then read it back.",
               "Create a file hello.txt containing the word hello, then run cat on it.",
               "In one sentence, what did we do so far?"]
    for i, t in enumerate(prompts):
        r = call("POST", f"/api/session/{sid}/prompt", {"text": t})
        ok = wait_idle(sid)
        print(f"turn {i+1} sent={'_http' not in r} idle={ok}", summary(sid))
elif mode == "more":
    sid = open(sidfile).read().strip()
    for i in range(8):
        t = ["Read hello.txt with the read tool.", "Run: ls -la", "Append the line five to notes.txt using the edit tool.", "Run: wc -l notes.txt", "Run: git status --short", "Read notes.txt again.", "Create a.txt with the text A and read it.", "Summarize in 3 words."][i]
        r = call("POST", f"/api/session/{sid}/prompt", {"text": t}); ok = wait_idle(sid)
        print(f"more {i+1} idle={ok}", summary(sid))
else:
    sid = open(sidfile).read().strip()
    print("read-back", summary(sid))
    if mode == "continue":
        r = call("POST", f"/api/session/{sid}/prompt", {"text": "Say the word done."})
        print("continued sent=", '_http' not in r, "idle=", wait_idle(sid), summary(sid))
