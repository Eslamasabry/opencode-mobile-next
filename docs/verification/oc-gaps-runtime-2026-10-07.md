# OpenCode Agent cards: runtime qualification handoff

This is a device procedure, not a device result. Vega did not execute this
procedure, install dependencies, restart a server, or enable OpenCode readiness.
Later read-only device logs for the separate Claude reopen bug are recorded in
[the main handoff](oc-gaps-2026-10-07.md); they do not qualify this runtime.

## Selected runtime and scope

The in-app Ubuntu OpenCode installer already downloads the hash-pinned Node
release to `/opt/node/bin/node` (`BuiltinLinux._fastPrerequisites`), then creates
`/usr/local/bin/node` as a symlink. Agent-card setup previously required only
`/usr/bin/node`. Use the existing absolute `/opt/node/bin/node` first, or the
system `/usr/bin/node` if the former is absent. Never execute the mutable
`/home/oc/.local/node/bin/node` as root, and never follow the convenience symlink.
An existing unsafe preferred candidate is an error, not a reason to try another.

No extra Node download, npm invocation, binary, native code, asset or dependency
is needed when one of those safe candidates exists. If neither exists, use the
existing user-initiated OpenCode setup/repair journey; do not silently download a
runtime from Agent cards. Local workstation `opencode` and `opencode2` commands
are postinstall-error stubs, so no pinned `BUN_BE_BUN=1` claim was established.
The Bun fallback is unnecessary for this path and remains unqualified.

OC1 retains the existing ownership-aware persistent file transaction at
`/root/.config/opencode/opencode.json`: `mcp.oc-ui`, `type:local`, command argv,
`enabled:true`. Its pinned loader deeply merges `config.json`, `opencode.json`,
then `opencode.jsonc`; a schema-only or disjoint JSONC therefore remains
byte-for-byte unchanged while the JSON file receives the managed entry.
Sibling flat or compatibility-format `oc-ui` collisions are refused. The existing gateway's global config PATCH may refresh/dispose
live instances, so this staged transaction deliberately does not call it while
work may be active. OC2 uses its existing isolated file
`/root/.oc-opencode2/config/opencode/opencode.json`: `mcp.servers.oc-ui`,
`type:local`, command argv, `disabled:false`, `codemode:false`. OC2 JSONC
precedence remains unqualified and refused. Unowned collisions and edits to
owned entries remain refusal cases for both runtimes. Neither
path promises live reload. Finish running work before an explicit user-approved
server restart. A configuration write is not direct-tool qualification.

## Device procedure for Claude

1. Select the approved device explicitly; never use an unqualified default:

   ```sh
   adb devices -l
   adb -s "$DEVICE" shell pm path io.github.eslamasabry.opencode_mobile
   adb -s "$DEVICE" shell dumpsys package io.github.eslamasabry.opencode_mobile
   ```

   Record installed app version and actual Android application UID from the
   package result. Do not print environment, provider configuration, auth files,
   or the OC2 launch password. Use the app's existing Ubuntu terminal/setup
   bridges; do not run guest setup as real Android UID 0. The previous probe
   demonstrated that files created by real root can be inaccessible to the app.

2. In the existing **root Ubuntu terminal**, run this read-only prerequisite
   probe. It neither opens unrelated configs nor launches OpenCode services:

   ```sh
   /usr/bin/python3 - <<'PY'
   import os, pathlib, stat, subprocess
   selected = None
   for candidate in ['/opt/node/bin/node', '/usr/bin/node']:
       if not os.path.lexists(candidate):
           continue
       for part in [*reversed(pathlib.Path(candidate).parents), pathlib.Path(candidate)]:
           info = os.lstat(part)
           if stat.S_ISLNK(info.st_mode) or info.st_uid != 0 or info.st_mode & 0o022:
               raise SystemExit('OC_UI_RUNTIME=unsafe')
       if not os.access(candidate, os.X_OK):
           raise SystemExit('OC_UI_RUNTIME=not-executable')
       selected = candidate
       break
   if selected is None:
       raise SystemExit('OC_UI_RUNTIME=missing')
   version = subprocess.check_output([selected, '--version'], timeout=5, text=True).strip()
   print('OC_UI_RUNTIME=available path=' + selected + ' version=' + version)
   PY
   ```

   Expected line: `OC_UI_RUNTIME=available path=/opt/node/bin/node version=v24.21.0`
   for the current in-app setup recipe; a system Node has its own recorded
   version. Missing Node requires the existing explicit OpenCode setup/repair
   action. Do not install anything if the available candidate passes.

3. With the candidate app backend installed by the coordinator, enable Agent
   cards using its existing setting. Check only the app-owned entry and files;
   never dump the whole OpenCode config. Both OC runtimes remain `notQualified`
   and are absent from `GenUiSetupStatus.agents` even after successful staging.
   A mixed Claude/OpenCode result may be partial with Claude alone ready.
   These are the expected statuses before qualification, not a setup failure
   that a restart alone resolves.

4. Prove the safety precondition through the **actual existing agent-user
   bridge** (`BuiltinLinux.runAgentSetupCheck`, uid-1000 guest path with the
   app's normal confinement), not `setpriv` inside the root terminal. Submit
   this fixed script with the exact selected runtime and helper paths. Opening
   with `O_WRONLY` without `O_TRUNC` or a write does not modify file contents:

   ```sh
   /usr/bin/python3 - <<'PY'
   import os
   paths = ['/opt/node/bin/node',
            '/root/.oc-genui/openCode1/server.cjs',
            '/root/.oc-genui/openCode2/server.cjs']
   for path in paths:
       try:
           fd = os.open(path, os.O_WRONLY | os.O_NOFOLLOW)
       except (PermissionError, FileNotFoundError):
           print('OC_UI_AGENT_WRITE=blocked path=' + path)
       else:
           os.close(fd)
           raise SystemExit('OC_UI_AGENT_WRITE=UNSAFE path=' + path)
   PY
   ```

   Use `/usr/bin/node` instead if selected in step 2. Root-side existence and
   contents must already have been verified; otherwise `FileNotFoundError`
   alone proves nothing. Confirm the actual agent view cannot replace parent
   directories either. Guest `id -u`, root ownership and file modes alone are
   not proof: PRoot does not itself establish a kernel UID boundary. An unsafe
   result blocks qualification even if MCP calls work.

5. Independently qualify OC1 **1.18.32** and OC2 **2.0.10** in dedicated disposable
   sessions after an explicitly approved idle-server restart. Install a missing
   OC2 only via the existing OpenCode runtime setup and only with owner approval.
   Keep its existing isolated XDG directories and config path. Never restart or
   dispose an active shared instance to load a catalog. Record the runtime
   version and selected Node version in the result.

6. Verify the owned `oc-ui` MCP status becomes `connected`, and the exact direct
   tool name exposed to the agent is **`oc-ui_show`** for each dialect. An MCP
   `show` catalog entry alone is insufficient. For OC2 specifically prove
   `codemode:false` loads the direct tool rather than a Code Mode wrapper.
   Request this precise call in the disposable session:

   ```json
   {"v":1,"id":"oc-gaps-probe","title":"Agent cards probe","body":[{"type":"text","text":"Runtime qualification"}],"ask":{"kind":"confirm"}}
   ```

   A successful completed call has full structured input, an assistant-owned
   non-synthetic tool part, `status:completed`, `executed:true`, and exact name
   `oc-ui_show`. The output is:

   ```text
   Card accepted for display in OpenCode Mobile. If it asks a question, end your turn and wait for the person's next message tagged [oc-ui answer <id>]. This call does not return their answer.
   ```

   Wait for authoritative session idle. Once the coordinator has reviewed the
   captured dialect fixture and deliberately enabled that runtime's readiness
   gate, repeat through the app, confirm the rendered card, and verify the
   authoritative user receipt has exactly the same call ID and
   `value:{"confirm":true}`. Test process restart/reconnect, list recovery,
   disabling the marker, and shared-owner removal separately. Do not infer app
   rendering or receipt success from the helper's acceptance text.

7. Delete only the disposable sessions and probe artifacts owned by this run.
   Retain sanitized tool/receipt fixtures and record failures as unavailable.
   Do not promote the compile-time qualification set based on unit tests,
   configuration success, or a merely connected MCP server.

## Local verification

The coordinator ran the focused installer file through the serial machine lock:

```sh
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- \
  /home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter \
  test --no-pub --concurrency=1 test/gen_ui_install_test.dart
```

Before implementation, 27 passed and 8 failed, including missing `/opt/node`
selection, unsafe runtime/helper paths, OC2 registration and staging/readiness
assertions (`/tmp/vega-gap2-red.log`). After implementation, all 36 passed
(`/tmp/vega-gap2-green.log`), including legacy registration cleanup after runtime
removal. The tests execute the real generated Python file transaction in an
isolated temporary tree while substituting runtime execution and host metadata;
they never read or change real device or host MCP configuration. Formatting used
the pinned Dart with `--language-version=3.10`; scoped `git diff --check` passed.
Device qualification and integration analyzer results belong to the coordinator's
combined verification note.


## OC1 JSONC follow-up before qualification

On APK 2177 the user found that the app-authored schema-only
`/root/.config/opencode/opencode.jsonc` caused OC1 setup to return a collision.
The candidate removes that blanket refusal. The pinned OC1
[global loader](https://github.com/anomalyco/opencode/blob/v1.18.32/packages/opencode/src/config/config.ts#L235-L268)
and [compatibility lowering](https://github.com/anomalyco/opencode/blob/v1.18.32/packages/opencode/src/config/v2-compat.ts#L229-L299)
establish the merge behavior used here.

With the updated candidate, rerun Cards setup (Off then On when convenient).
Expect the JSONC file to be unchanged and `opencode.json` to contain the owned
`mcp.oc-ui` entry. The managed OC1 directory should now contain `server.cjs`,
`owners.json`, and `enabled` as well as its lock. With Claude already qualified,
the English setting line should become:

> On for Claude Code. This agent version hasn't been checked to work with cards.

That line is expected until the OpenCode runtime is qualified. Finish running
work, then perform the owner's planned restart and continue the procedure above.
This patch does not restart the server or promote readiness. Inspect only the
owned entry and file existence; do not dump provider configuration.

Malformed/duplicate-key JSONC, nonobject MCP overrides, unowned flat or nested
`oc-ui` entries, unsafe paths and an extensionless legacy TOML config remain
refused. Later project/environment/managed configuration layers are not edited
by this transaction; actual post-restart MCP visibility remains part of runtime
qualification. If a user adds a conflicting entry after installation, setup
still refuses instead of claiming that disable succeeded.

Follow-up local result: all 59 installer tests passed after the JSONC fix,
including true-collision refusal, commented/trailing-comma configs, valid
command arrays, idempotence, disable and rollback. Reverting the fix produced
13 failures; it was restored before the final pass. The controller file also
passed 14 tests and the analyzer was clean. These remain local tests, not a
runtime qualification result.
