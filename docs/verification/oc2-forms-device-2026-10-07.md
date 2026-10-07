# OC2 typed forms under Conversations rows — device qualification

Status: operator procedure, not a recorded device pass. Run against **OpenCode
2.0.10** in the existing in-app Ubuntu. No agent ran these commands on a device.
The route/body contract below comes from
[the captured OC2 specification](../../contracts/opencode2-openapi-beta-18600.json),
[protocol notes §8](../opencode2-protocol-notes.md#8-forms-replaces-v1-questions)
and `lib/api2/client.dart`. The health assertion deliberately stops on any
version other than 2.0.10; HTTP contract errors are evidence to report, not a
reason to mutate the server database or use an internal endpoint.

## Prepare

1. Record the installed candidate APK version and revision. From the workstation:

   ```sh
   adb -s emulator-5554 shell dumpsys package io.github.eslamasabry.opencode_mobile
   ```

   No install, restart, or tap automation is required by this procedure. Use the
   app's existing root Ubuntu terminal for commands below; do not create guest
   files through real Android `su 0`, which can give them the wrong ownership.
2. Use the existing runtime setup to select OC2 2.0.10, if necessary, only after
   finishing live work. In Ubuntu create the disposable directories:

   ```sh
   mkdir -p /root/oc-forms-home /root/oc-forms-other /root/oc-forms-third
   ```

   Point the main app connection at `/root/oc-forms-home`. Leave Conversations
   visible; do not open either test conversation. The global event connection
   must be online when the forms are created. `form.created` supplies the
   waiting signal even though this API fixture does not run a model.
3. Open another existing Ubuntu terminal tab to run this helper. Its password
   prompt is hidden; enter the OC2 server password already configured in the
   app. Do not print the service registry, credentials, environment, provider
   config, or authorization headers. The helper writes only disposable
   sessions/forms and prints their IDs and selected form state.

## API fixture helper

Save this as `/tmp/oc2-forms-qualification.py` inside Ubuntu, then run
`python3 /tmp/oc2-forms-qualification.py` interactively. The server origin is the
same **local** OC2 origin used by the app (use its actual port, not a guessed
replacement server). It must be loopback; for a side server run the helper in
that server's own terminal.

```python
import base64, getpass, json, pathlib, urllib.error, urllib.parse, urllib.request

origin = input('Existing local OC2 origin, e.g. http://127.0.0.1:4097: ').rstrip('/')
url = urllib.parse.urlsplit(origin)
assert url.scheme == 'http' and url.hostname in ('127.0.0.1', 'localhost', '::1')
auth = base64.b64encode(('opencode:' + getpass.getpass('OC2 password: ')).encode()).decode()

def api(method, path, body=None):
    req = urllib.request.Request(origin + '/api' + path, method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={'Authorization': 'Basic ' + auth, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=12) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        # Do not dump arbitrary response content or request headers.
        raise RuntimeError(f'{method} {path.split("?")[0]}: HTTP {error.code}') from None

health = api('GET', '/health')
assert health['version'] == '2.0.10', 'Wrong server version; stop and report it'
print('OC2_FORMS_VERSION=2.0.10')

def fields(revised=False):
    return [
        {'key':'enabled', 'type':'boolean', 'title':'Enable details',
         'required':True, 'default':False},
        {'key':'count', 'type':'integer', 'title':'Count', 'required':True,
         'minimum':1, 'maximum':3 if revised else 5, 'default':2},
        {'key':'weight', 'type':'number', 'title':'Weight', 'required':True,
         'minimum':0.5, 'maximum':9.5, 'default':1.25},
        {'key':'tags', 'type':'multiselect', 'title':'Tags', 'required':True,
         'minItems':1, 'maxItems':2, 'default':['alpha'],
         'options':[{'value':'alpha','label':'Alpha'}, {'value':'beta','label':'Beta'}]},
        {'key':'note', 'type':'string', 'title':'Details', 'required':True,
         'minLength':2, 'maxLength':40,
         'when':[{'key':'enabled','op':'eq','value':True}]},
    ]

rows = {}
def create(label, directory, shared=False):
    assert pathlib.Path(directory).is_dir(), 'Create the disposable directory first'
    body = {'title':'Forms QA ' + label, 'location':{'directory':directory}}
    # Use shared=True once per independent server to check identical IDs across profiles.
    if shared: body['id'] = 'ses_vega_forms_scope_20261007'
    session = api('POST', '/session', body)['data']['id']
    payload = {'title':'Typed form ' + label, 'fields':fields()}
    if shared: payload['id'] = 'frm_vega_forms_scope_20261007'
    form = api('POST', f'/session/{session}/form', payload)['data']['id']
    rows[label] = (session, form, directory)
    print(label, 'session=' + session, 'form=' + form)

def state(label):
    session, form, _ = rows[label]
    data = api('GET', f'/session/{session}/form/{form}/state')
    print(json.dumps(data, sort_keys=True))

def cancel(label):
    session, form, _ = rows[label]
    api('POST', f'/session/{session}/form/{form}/cancel', {})
    state(label)

def next_form(label):
    session, _, directory = rows[label]
    form = api('POST', f'/session/{session}/form',
        {'title':'Revised typed form ' + label, 'fields':fields(True)})['data']['id']
    rows[label] = (session, form, directory)
    print(label, 'form=' + form)

def pending(label):
    _, _, directory = rows[label]
    query = urllib.parse.urlencode({'location[directory]':directory})
    data = api('GET', '/form/request?' + query)['data']
    print('pending form IDs:', [f['id'] for f in data])

import code
code.interact(banner='Use create/state/cancel/next_form/pending below; Ctrl-D exits.', local=locals())
```

At its `>>>` prompt:

```python
create('other', '/root/oc-forms-other', shared=True)
create('third', '/root/oc-forms-third')
pending('other')
state('other')
```

Expect pending IDs to include `frm_vega_forms_scope_20261007` and state `pending`.
If those explicit fixture IDs already exist, use a fresh suffix in the script
on both servers; never delete an existing conversation to make a rerun pass.

## Main-server, unopened row, values, and receipts

1. Return to Conversations with the main location still `/root/oc-forms-home`.
   The `Forms QA other` row must show Needs you and its typed form. Do not open
   the row/chat. The form must not become a legacy question with string answers.
2. Open the form's existing renderer/sheet from the row. Keep **Enable details**
   off: Details must be hidden. Set Count to 3, Weight to 2.5, and select Alpha
   and Beta. Send. After the reply's normal delay, `state('other')` must report:

   ```json
   {"data":{"status":"answered","answer":{"enabled":false,"count":3,"weight":2.5,"tags":["alpha","beta"]}}}
   ```

   Key order does not matter. False is a boolean, Count/Weight are numbers,
   Tags is a string array, and inactive `note` must be absent. Check the list
   receipt; opening the unrelated `third` form must not show this draft/receipt.
3. Run `next_form('other')`. On the row, enable details, enter `checked`, Count
   2, Weight 1.25, and Alpha. Send and verify `state('other')` includes
   `enabled:true` and `note:"checked"`. Confirm the form stays on the right row
   and the main directory never changes.
4. Use Cancel on the original `third` row form; verify state `cancelled` and
   no answer body. Run `next_form('third')` and check that this next request for
   the same row is actionable.

## Unopened side server and identical IDs

For exact-ID collision proof, do this subsection immediately after the two
initial `create(...)` calls, before settling their forms; then return to the
typed-value checks above. Use an existing independently running OC2 2.0.10 side
server profile (Termux or
PC), not a second profile alias for the main daemon. In that server's terminal,
create a disposable directory, run the same helper and call:

```python
create('side', '/absolute/disposable/directory', shared=True)
state('side')
```

This deliberately repeats the main test's session and form IDs on another
server. Save/enable the side profile for Conversations monitoring, leaving the
main profile/location selected. Do not open the side conversation. Its Needs
you row must render the side form through the captured side profile/directory.
Fill a different draft on the side row and the main row. Switch sheets: drafts
must remain independent. Reply on the side row; `state('side')` changes, while
main `state('other')` does not. Cancel a fresh side form and check the converse.
For a cold-start check, leave a side form pending, close/reopen the app normally,
and verify it appears after monitoring refresh without ever opening its chat.

## Stale sheets, schema replacement, and uncertain delivery

- Open a pending row form, enter a draft, and leave its sheet open. From the
  helper call `cancel(label)` and then `next_form(label)`. The old sheet must not
  send or cancel the replacement; reopen to see the revised Count maximum 3.
  Do not reuse the old form ID: OC2's public API has creation/read/reply/cancel,
  **no schema-update route**, and duplicate-ID creation returns conflict. Exact
  same-ID schema-revision races are covered by focused controller tests; this
  live sequence proves stale-sheet handling through supported endpoints.
- For an ordinary disconnect, disable the side server's route using the
  operator's existing network controls, then attempt a side reply. Restore it
  and refresh. A request that may have reached the server must not be sent
  again automatically. Read `state('side')` to distinguish answered/pending.
  Do not claim deterministic lost-ACK proof from a simple disconnect: the
  focused transport/controller test deliberately accepts the write and loses
  its response. A live lost-ACK proof requires a separately approved response-
  dropping proxy; stopping/restarting the running server is not a substitute.
- Record APK revision, exact OC2 version, main/side directories, row never-opened
  status, visible receipts, typed state results, and any HTTP status failure.
  Do not include credentials or unrelated transcript content in evidence.
