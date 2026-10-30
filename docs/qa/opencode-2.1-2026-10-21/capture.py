import json, sys, time, urllib.request, urllib.parse, base64
port, pwfile, work, out = sys.argv[1:5]
pw = open(pwfile).read().strip(); base = f'http://127.0.0.1:{port}/api'
auth = 'Basic ' + base64.b64encode(f'opencode:{pw}'.encode()).decode()
def req(m, p, b=None, q=''):
    r = urllib.request.Request(base + p + ('?' + q if q else ''), method=m, data=json.dumps(b).encode() if b is not None else None, headers={'Authorization': auth, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(r, timeout=180) as resp:
        t = resp.read().decode(); return json.loads(t) if t else None
sid = req('POST', '/session', {'location': {'directory': work}, 'model': {'id': 'glm-5.3-flash-free', 'providerID': 'opencode'}})['data']['id']
def wait():
    req('POST', f'/experimental/session/{sid}/wait')
req('POST', f'/session/{sid}/prompt', {'text': 'Use the read tool on README.md, then reply with exactly its first line.'}); time.sleep(2); wait()
req('POST', f'/session/{sid}/compact', {}); time.sleep(2); wait()
page = req('GET', f'/session/{sid}/message', q='order=asc')
req('DELETE', f'/session/{sid}')
s = json.dumps(page).replace(work, '/home/dev/project').replace(sid, 'ses_fb534b6a0ffeJ5pNlwiesRdWV2')
json.dump(json.loads(s), open(out, 'w'), indent=1)
print([(m['type'], [c.get('type') for c in m.get('content', [])] if m['type']=='assistant' else m.get('status')) for m in json.loads(s)['data']])
