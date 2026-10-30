# Raw probe: compaction event names + archive round trip. Args: port pwfile workdir out.json
import json, sys, threading, time, urllib.request, base64
port, pwfile, work, out = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
pw = open(pwfile).read().strip()
base = f'http://127.0.0.1:{port}/api'
auth = 'Basic ' + base64.b64encode(f'opencode:{pw}'.encode()).decode()
loc = urllib.parse.quote('location[directory]') + '=' + urllib.parse.quote(work)
def req(method, path, body=None, q=''):
    url = base + path + ('?' + q if q else '')
    r = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body is not None else None,
        headers={'Authorization': auth, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(r, timeout=120) as resp:
            t = resp.read().decode(); return resp.status, (json.loads(t) if t else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300]
events = []
def sse():
    r = urllib.request.Request(base + '/event', headers={'Authorization': auth})
    with urllib.request.urlopen(r, timeout=600) as resp:
        for line in resp:
            line = line.decode().strip()
            if line.startswith('data:'):
                try: e = json.loads(line[5:])
                except Exception: continue
                events.append(e)
threading.Thread(target=sse, daemon=True).start(); time.sleep(1)
res = {}
s, j = req('POST', '/session', {'location': {'directory': work}, 'model': {'id': 'glm-5.3-flash-free', 'providerID': 'opencode'}})
sid = j['data']['id']; res['create'] = s
def wait_idle(after, timeout=120):
    t0 = time.time()
    while time.time() - t0 < timeout:
        if any(e['type'] in ('session.execution.succeeded','session.execution.failed') and e['data'].get('sessionID') == sid for e in events[after:]): return True
        time.sleep(0.5)
    return False
n = len(events); res['prompt'] = req('POST', f'/session/{sid}/prompt', {'text': 'Reply with exactly the word: pong'})[0]
res['prompt_idle'] = wait_idle(n)
n = len(events); res['compact'] = req('POST', f'/session/{sid}/compact', {})[0]
t0 = time.time()
while time.time() - t0 < 120 and not any(e['type'] in ('session.compaction.ended','session.compaction.failed') for e in events[n:]): time.sleep(0.5)
wait_idle(n, 30); time.sleep(2)
comp = [e for e in events[n:] if e.get('data', {}).get('sessionID') == sid]
res['compaction_event_types'] = sorted({e['type'] for e in comp})
res['compaction_ended_keys'] = sorted({k for e in comp if e['type'] == 'session.compaction.ended' for k in e['data']})
res['compaction_started_keys'] = sorted({k for e in comp if e['type'] == 'session.compaction.started' for k in e['data']})
s, msgs = req('GET', f'/session/{sid}/message')
res['message_types'] = [m.get('type') for m in (msgs or {}).get('data', [])] if isinstance(msgs, dict) else msgs
res['compaction_message_keys'] = sorted({k for m in (msgs or {}).get('data', []) if m.get('type') == 'compaction' for k in m}) if isinstance(msgs, dict) else None
res['assistant_content_keys'] = sorted({k for m in (msgs or {}).get('data', []) if m.get('type') == 'assistant' for c in m.get('content', []) for k in c}) if isinstance(msgs, dict) else None
res['idle_filter'] = req('GET', f'/session/{sid}/message', q='type=idle')[0]
# archive round trip
res['archive_patch'] = req('PATCH', f'/session/{sid}', {'archived': True})[0]
s, info = req('GET', f'/session/{sid}')
res['archived_time_after_patch'] = isinstance(info, dict) and bool(((info.get('data') or {}).get('time') or {}).get('archived'))
s, lst = req('GET', '/session', q=loc + '&archived=true')
res['list_archived_true'] = (s, isinstance(lst, dict) and any(x['id'] == sid for x in lst.get('data', [])))
s, lst = req('GET', '/session', q=loc)
res['list_default_contains_archived'] = (s, isinstance(lst, dict) and any(x['id'] == sid for x in lst.get('data', [])))
res['unarchive_patch'] = req('PATCH', f'/session/{sid}', {'archived': False})[0]
s, info = req('GET', f'/session/{sid}')
res['archived_time_after_unarchive'] = isinstance(info, dict) and bool(((info.get('data') or {}).get('time') or {}).get('archived'))
res['saved_permission_patch_unknown'] = req('PATCH', '/permission/saved/per_doesnotexist', {})[0]
res['delete'] = req('DELETE', f'/session/{sid}')[0]
res['all_event_types'] = sorted({e['type'] for e in events})
json.dump(res, open(out, 'w'), indent=1); print(json.dumps(res, indent=1))
