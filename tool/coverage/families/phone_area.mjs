// Area "phone": setting OpenCode up on this phone, the tools it installs and
// the agents that run there. Families (each a schema plus cases):
//
//   phone_job        setup.json, the native runner's record of a setup job
//                    (the installer's result), read by the app's real engine
//                    mapper and drawn by the real progress screen.
//   (more families are added below as their screens are covered)
import { readFileSync } from 'node:fs';
const T0 = 1788950000000;
const ids = ['linux', 'essentials', 'python', 'node', 'opencode', 'start'];

const comp = (state, extra = {}) => ({ state, weight: 20, ...extra });

export function phoneFamilies() {
  const out = {};

  // ----------------------------------------------------------------- job
  const jobSchema = {
    job: {
      jobId: { kind: 'string' }, host: { kind: 'enum', values: ['builtin', 'termux'] },
      state: { kind: 'enum', values: ['running', 'done', 'failed', 'cancelled', 'interrupted'] },
      current: { kind: 'string' }, startedAt: { kind: 'number' }, updatedAt: { kind: 'number' },
      error: { kind: 'string' }, errorCode: { kind: 'string' }, logTail: { kind: 'string' },
      'order[]': { kind: 'string' },
      'params._job.first': { kind: 'string' }, 'params._job.adding': { kind: 'string' },
      'params.opencode.runtime': { kind: 'string' }, 'params.opencode.version': { kind: 'string' },
    },
    component: {
      state: { kind: 'enum', values: ['pending', 'running', 'done', 'failed', 'skipped'] },
      weight: { kind: 'number' }, stage: { kind: 'string' }, done: { kind: 'number' }, total: { kind: 'number' },
      percent: { kind: 'number' }, version: { kind: 'string' }, error: { kind: 'string' },
      startedAt: { kind: 'number' }, endedAt: { kind: 'number' }, data: { kind: 'any' },
    },
  };
  const base = (id, extra) => ({ jobId: id, host: 'builtin', startedAt: T0, updatedAt: T0 + 61000, order: ids, ...extra });
  const none = (o) => Object.fromEntries(Object.keys(o).map((k) => [k, []]));
  const jobPart = (rec, probes = {}) => {
    const value = { ...rec };
    delete value.components;
    const flat = {};
    const paths = flatPaths(value);
    const probe = { ...Object.fromEntries(Object.keys(paths).map((k) => [k, []])), ...probes };
    return { group: 'job', value, probes: Object.fromEntries(Object.entries(probe).filter(([k]) => k in paths)) };
  };
  const flatPaths = (v, prefix = '', o = {}) => {
    for (const [k, x] of Object.entries(v)) {
      if (Array.isArray(x)) o[`${prefix}${k}[]`] = 1;
      else if (x && typeof x === 'object') flatPaths(x, `${prefix}${k}.`, o);
      else o[`${prefix}${k}`] = 1;
    }
    return o;
  };
  const compPart = (value, probes = {}) => ({ group: 'component', value, probes: { ...none(value), ...probes } });
  const jcase = (id, rec, parts) => ({ id, kind: 'job', payload: rec, parts: [jobPart(rec, parts.job), ...parts.components.map(([v, p]) => compPart(v, p))] });

  const running = base('job_run', {
    state: 'running', current: 'node', logTail: 'node: fetching the package index',
    params: { _job: { first: '1' }, opencode: { runtime: 'opencode1', version: '1.18.32' } },
    components: {
      linux: comp('done', { stage: 'Ready', version: '24.04.5', startedAt: T0, endedAt: T0 + 9000 }),
      essentials: comp('done', { startedAt: T0 + 9000, endedAt: T0 + 20000 }),
      python: comp('skipped'),
      node: comp('running', { stage: 'Downloading', done: 18000000, total: 30000000, percent: 60, startedAt: T0 + 20000, data: { mirror: 'default' } }),
      opencode: comp('pending'), start: comp('pending'),
    },
  });
  const failed = base('job_fail', {
    state: 'failed', current: 'node', error: 'node exited with 1', errorCode: 'setup_persistence',
    logTail: 'curl: (6) Could not resolve host: nodejs.org',
    params: {},
    components: {
      linux: comp('done', { version: '24.04.5' }), essentials: comp('done'), python: comp('done', { version: '3.12.3' }),
      node: comp('failed', { stage: 'Downloading', error: 'curl: (6) Could not resolve host' }),
      opencode: comp('pending'), start: comp('pending'),
    },
  });
  const states = (state) => base(`job_${state}`, {
    state, current: 'opencode', logTail: '',
    components: {
      linux: comp('done'), essentials: comp('done'), python: comp('done'), node: comp('done', { version: '22.11.0' }),
      opencode: comp(state === 'done' ? 'done' : 'pending', state === 'done' ? { version: '1.18.32' } : {}),
      start: comp(state === 'done' ? 'done' : 'pending'),
    },
  });
  const termux = base('job_termux', {
    host: 'termux', state: 'running', current: 'opencode', logTail: '', params: { _job: { adding: 'aiteam' } },
    components: { linux: comp('skipped'), essentials: comp('done'), python: comp('skipped'), node: comp('done'), opencode: comp('running', { stage: 'Installing', percent: 40 }), start: comp('pending') },
  });
  const compsOf = (rec, by = {}) => Object.entries(rec.components).map(([id, v]) => [v, by[id] ?? {}]);
  const runBy = {
    linux: { state: [], stage: [], version: ['24.04.5'], startedAt: [], endedAt: [], weight: [] },
    node: { stage: ['Downloading'], done: ['18 of 30 MB'], total: ['18 of 30 MB'], percent: [], data: [] },
  };
  const failBy = { node: { stage: [], error: ['No internet connection'], state: ['No internet connection'] }, python: { version: ['3.12.3'] } };
  out.phone_job = {
    schema: jobSchema, excluded: {}, source: 'lib/builtin/setup/setup_engine/job_record.dart (setup.json)',
    cases: [
      jcase('job_running_download', running, { job: { state: ['Setting up OpenCode on this phone'], logTail: ['node: fetching the package index'], 'order[]': ['Linux base', 'Node.js', 'Start OpenCode'], 'params._job.first': ['Step 1 of 3'], 'params.opencode.runtime': [], 'params.opencode.version': [] }, components: compsOf(running, runBy) }),
      jcase('job_failed_offline', failed, { job: { state: ["Setup didn't finish"], errorCode: ['Could not install OpenCode'], error: [], logTail: ['curl: (6) Could not resolve host'] }, components: compsOf(failed, failBy) }),
      ...[['done', 'All set'], ['interrupted', "Setup was interrupted"], ['cancelled', 'Setup stopped']].map(([s, word]) => { const r = states(s); return jcase(`job_${s}`, r, { job: { state: [word] }, components: compsOf(r, s === 'done' ? { node: { version: ['22.11.0'] }, opencode: { version: ['1.18.32'] } } : {}) }); }),
      jcase('job_on_termux', termux, { job: { state: ['Adding AI Team'], 'params._job.adding': ['Adding AI Team'] }, components: compsOf(termux, { opencode: { stage: ['Installing'], percent: ['40%'] } }) }),
    ],
  };

  // -------------------------------------------------------------- device
  const devSchema = {
    device: {
      availableStorageBytes: { kind: 'number' }, memoryClassMb: { kind: 'number' }, totalMemoryMb: { kind: 'number' },
      lowRamDevice: { kind: 'boolean' }, 'supportedAbis[]': { kind: 'string' }, hasMicrophone: { kind: 'boolean' },
    },
  };
  const okDev = { availableStorageBytes: 2000000000, memoryClassMb: 256, totalMemoryMb: 4096, lowRamDevice: false, supportedAbis: ['arm64-v8a', 'armeabi-v7a'], hasMicrophone: false };
  const dcase = (id, over, probes) => {
    const value = { ...okDev, ...over };
    const flat = { availableStorageBytes: [], memoryClassMb: [], totalMemoryMb: [], lowRamDevice: [], 'supportedAbis[]': [], hasMicrophone: [] };
    return { id, kind: 'device', payload: value, parts: [{ group: 'device', value, probes: { ...flat, ...probes } }] };
  };
  out.phone_device = {
    schema: devSchema, excluded: {}, source: 'lib/voice/device.dart (oc/voice getDeviceInfo) read by lib/builtin/setup/preflight.dart',
    cases: [
      dcase('device_ok', {}, {}),
      dcase('device_arm32', { supportedAbis: ['armeabi-v7a'] }, { 'supportedAbis[]': ['armeabi-v7a'] }),
      dcase('device_low_memory', { totalMemoryMb: 1200 }, { totalMemoryMb: ['1,200 MB'] }),
      dcase('device_low_space', { availableStorageBytes: 100000000 }, { availableStorageBytes: ['Free about'] }),
      dcase('device_slow', { totalMemoryMb: 2048 }, { totalMemoryMb: ['2,048 MB'] }),
    ],
  };

  // -------------------------------------------------------------- agents
  const mk = (id, kind, payload, parts) => ({
    id, kind, payload,
    parts: parts.map(([group, value0, probes0 = {}]) => {
      const prune = (v) => Array.isArray(v) ? v : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).filter(([, x]) => x !== undefined && !(Array.isArray(x) && x.length === 0)).map(([k, x]) => [k, prune(x)])) : v;
      const value = prune(value0);
      const probes = probes0;
      const flat = Object.fromEntries(Object.keys(flatPaths(value)).map((k) => [k, []]));
      return { group, value, probes: { ...flat, ...probes } };
    }),
  });
  const rtSchema = {
    runtime: {
      installed: { kind: 'boolean' }, payloadPresent: { kind: 'boolean' }, hostAvailable: { kind: 'boolean' },
      architectureQualified: { kind: 'boolean' }, stoppedInBackground: { kind: 'boolean' },
      signInPhase: { kind: 'enum', values: ['signedIn', 'signedOut', 'failed', 'limitReached'] }, resetAt: { kind: 'string' },
      'capabilities.resumeVerified': { kind: 'boolean' }, 'capabilities.modelList': { kind: 'boolean' },
      'capabilities.permissions': { kind: 'boolean' }, 'capabilities.images': { kind: 'boolean' }, 'capabilities.cancel': { kind: 'boolean' },
    },
    account: {
      state: { kind: 'enum', values: ['signedIn', 'signedOut', 'error'] }, accountDisplayName: { kind: 'string' },
      error: { kind: 'enum', values: ['probeUnsupported', 'invalidResponse', 'timedOut', 'hostUnavailable', 'notInstalled', 'invalidContext', 'signInExpired', 'signOutFailed'] },
    },
  };
  const rt = (over = {}) => ({
    installed: true, payloadPresent: true, hostAvailable: true, architectureQualified: true, stoppedInBackground: false,
    signInPhase: 'signedIn', capabilities: { resumeVerified: false, modelList: false, permissions: false, images: false, cancel: false }, ...over,
  });
  const reset = '2026-10-10T18:30:00.000Z';
  const rowCase = (id, runtime, probes, account = { state: 'signedOut' }, accountProbes = {}) =>
    mk(id, 'row', { agent: 'claude', runtime, account }, [['runtime', runtime, probes], ['account', account, accountProbes]]);
  out.phone_agent_row = {
    schema: rtSchema, excluded: {}, source: 'lib/domain/phone_agents.dart (PhoneAgentRuntime) and agent_auth_probe.dart (helper reply)',
    cases: [
      rowCase('row_not_installed', rt({ installed: false, payloadPresent: false, architectureQualified: false, hostAvailable: false, signInPhase: undefined }), { installed: ['Not installed'] }),
      rowCase('row_partial_install', rt({ installed: false, payloadPresent: true, architectureQualified: false, hostAvailable: false, signInPhase: undefined }), {}),
      rowCase('row_needs_check', rt({ architectureQualified: false }), { architectureQualified: ['Phone check needed'] }),
      rowCase('row_stopped', rt({ stoppedInBackground: true }), { stoppedInBackground: ['Stopped in the background'] }),
      rowCase('row_host_down', rt({ hostAvailable: false }), { hostAvailable: ['Not available on this phone yet'] }),
      rowCase('row_signed_out', rt({ signInPhase: 'signedOut' }), { signInPhase: ['Sign in needed'] }),
      rowCase('row_limit', rt({ signInPhase: 'limitReached', resetAt: reset }), { signInPhase: ['Plan limit reached'], resetAt: ['resets'] }),
      rowCase('row_check_failed', rt({ signInPhase: 'failed' }), { signInPhase: ['Sign in needed'] }, { state: 'error', error: 'timedOut' }),
      rowCase('row_ready_named', rt({ signInPhase: 'signedIn' }), { 'capabilities.resumeVerified': ["Can't reopen old conversations"] }, { state: 'signedIn', accountDisplayName: 'Example account' }, { state: ['Signed in as'], accountDisplayName: ['Example account'] }),
      rowCase('row_ready_verified', rt({ capabilities: { resumeVerified: true, modelList: true, permissions: true, images: true, cancel: true } }), {}, { state: 'signedIn' }, { state: ['Signed in'] }),
    ].map((c) => { for (const part of c.parts) { for (const k of Object.keys(part.value)) if (part.value[k] === undefined) delete part.value[k]; } return c; }),
  };

  // ------------------------------------------------------------- install
  const instSchema = {
    progress: {
      phase: { kind: 'enum', values: ['idle', 'installing', 'done', 'interrupted', 'failed'] }, fraction: { kind: 'number' },
      componentId: { kind: 'string' },
      failure: { kind: 'enum', values: ['unavailable', 'storage', 'install', 'interrupted', 'version', 'daemon', 'hello', 'wrongArchitecture', 'stale', 'busy'] },
    },
  };
  const inst = (id, value, probes) => mk(id, 'install', { agent: 'codex', progress: value }, [['progress', value, probes]]);
  out.phone_agent_install = {
    schema: instSchema, excluded: {}, source: 'lib/domain/phone_agent_host.dart (AgentSetupProgress)',
    cases: [
      inst('install_running', { phase: 'installing', fraction: 0.4, componentId: 'linux' }, { phase: ['Installing Codex'], fraction: ['[bar 40%]'] }),
      inst('install_waiting', { phase: 'installing' }, { phase: ['Installing Codex'] }),
      inst('install_interrupted', { phase: 'interrupted' }, { phase: ['Setup stopped before it finished'] }),
      inst('install_failed_storage', { phase: 'failed', failure: 'storage' }, { phase: ['Free some space'], failure: ['Free some space'] }),
      inst('install_failed_plain', { phase: 'failed' }, { phase: ["Setup didn't finish"] }),
    ],
  };

  // --------------------------------------------------------------- check
  const chkSchema = {
    check: {
      agentId: { kind: 'string' }, architecture: { kind: 'enum', values: ['arm64', 'x64'] }, passed: { kind: 'boolean' },
      'completed[]': { kind: 'enum', values: ['install', 'version', 'daemon', 'hello'] },
      failure: { kind: 'enum', values: ['unavailable', 'storage', 'install', 'interrupted', 'version', 'daemon', 'hello', 'wrongArchitecture', 'stale', 'busy'] },
    },
  };
  const chk = (id, value, probes) => mk(id, 'check', { agent: 'codex', check: value }, [['check', value, probes]]);
  out.phone_agent_check = {
    schema: chkSchema, excluded: {}, source: 'lib/domain/phone_agent_host.dart (AgentPhoneCheckResult)',
    cases: [
      chk('check_passed', { agentId: 'codex', architecture: 'arm64', passed: true, completed: ['install', 'version', 'daemon', 'hello'] }, { passed: ['is ready on this phone'], 'completed[]': ['[Installed done]', '[Version done]', '[Connection done]', '[Ready done]'] }),
      chk('check_failed_version', { agentId: 'codex', architecture: 'arm64', passed: false, completed: ['install'], failure: 'version' }, { passed: ["didn't pass the check"], 'completed[]': ['[Installed done]', '[Version failed]', '[Connection waiting]'], failure: ["didn't pass its version check"] }),
      chk('check_failed_arch', { agentId: 'codex', architecture: 'x64', passed: false, failure: 'wrongArchitecture' }, { passed: ["didn't pass the check"], failure: ["doesn't match this phone's processor"] }),
    ],
  };

  // ------------------------------------------------------------- removal
  const remSchema = { removal: { agentId: { kind: 'string' }, freedBytes: { kind: 'number' }, alreadyAbsent: { kind: 'boolean' } } };
  const rem = (id, value, probes) => mk(id, 'removal', { agent: 'codex', removal: value }, [['removal', value, probes]]);
  out.phone_agent_removal = {
    schema: remSchema, excluded: {}, source: 'lib/domain/phone_agent_host.dart (AgentRemovalResult)',
    cases: [
      rem('removal_freed', { agentId: 'codex', freedBytes: 98000000, alreadyAbsent: false }, { freedBytes: ['Freed 98 MB'] }),
      rem('removal_already', { agentId: 'codex', freedBytes: 0, alreadyAbsent: true }, { alreadyAbsent: ['is already removed'] }),
    ],
  };

  // ------------------------------------------------------------- sign-in
  const siSchema = {
    state: {
      phase: { kind: 'enum', values: ['signedOut', 'urlReady', 'awaitingCode', 'signedIn', 'limitReached', 'failed'] },
      method: { kind: 'enum', values: ['browserOAuthHost', 'apiKeyHost', 'none'] }, inspected: { kind: 'boolean' },
      failure: { kind: 'string' }, resetAt: { kind: 'string' }, authorizationUrl: { kind: 'string' }, codeSubmitted: { kind: 'boolean' },
    },
  };
  const si = (id, value, probes) => mk(id, 'signin', { agent: 'codex', state: value }, [['state', value, probes]]);
  out.phone_agent_signin = {
    schema: siSchema, excluded: {}, source: 'lib/domain/agent_sign_in.dart (AgentSignInState)',
    cases: [
      si('signin_checking', { phase: 'signedOut', method: 'browserOAuthHost', inspected: false }, { inspected: ['Checking sign-in'] }),
      si('signin_signed_out', { phase: 'signedOut', method: 'browserOAuthHost', inspected: true }, { phase: ['signs in with its own prompts'] }),
      si('signin_signed_in', { phase: 'signedIn', method: 'browserOAuthHost', inspected: true }, { phase: ['Codex is ready for new conversations'] }),
      si('signin_limit', { phase: 'limitReached', method: 'browserOAuthHost', inspected: true, resetAt: reset }, { phase: ['plan limit reached'] }),
      si('signin_failed', { phase: 'failed', method: 'browserOAuthHost', inspected: true, failure: 'unavailable' }, { phase: ['signs in with its own prompts'] }),
      si('signin_host_key', { phase: 'signedOut', method: 'apiKeyHost', inspected: true }, { method: ["Sign-in isn't ready on this phone yet"] }),
      si('signin_code_wait', { phase: 'awaitingCode', method: 'browserOAuthHost', inspected: true, authorizationUrl: 'https://example.invalid/auth', codeSubmitted: true }, {}),
    ],
  };

  // ------------------------------------------------- sign-in terminal output
  const soSchema = { output: { page: { kind: 'string' }, code: { kind: 'string' } } };
  const so = (id, text, value, probes) => mk(id, 'output', { agent: 'codex', text }, [['output', value, probes]]);
  out.phone_agent_signin_output = {
    schema: soSchema, excluded: {}, source: 'lib/domain/agent_sign_in_output.dart (what the agent printed)',
    cases: [
      so('output_fx', 'Open https://vercel.com/oauth/device?user_code=ABCD-EFGH to sign in\r\n', { page: 'https://vercel.com/oauth/device?user_code=ABCD-EFGH', code: 'ABCD-EFGH' }, { page: ['Open sign-in page'], code: ['Copy code ABCD-EFGH'] }),
      so('output_codex', 'Go to https://auth.openai.com/codex/device\r\nEnter this one-time code\r\nWXYZ-12345\r\n', { page: 'https://auth.openai.com/codex/device', code: 'WXYZ-12345' }, { page: ['Open sign-in page'], code: ['Copy code WXYZ-12345'] }),
      so('output_claude', 'Visit https://claude.ai/oauth/authorize?client=x to sign in\r\n', { page: 'https://claude.ai/oauth/authorize?client=x' }, { page: ['Open sign-in page'] }),
    ],
  };

  // --------------------------------------------------------------- termux
  const txSchema = {
    status: {
      phase: { kind: 'enum', values: ['queued', 'preparing', 'installing_dependencies', 'installing_ubuntu', 'installing_opencode', 'refreshing_models', 'restarting', 'starting_server', 'ready', 'failed', 'idle'] },
      message: { kind: 'string' }, port: { kind: 'number' }, runner: { kind: 'string' }, version: { kind: 'string' }, pid: { kind: 'number' },
      started_at: { kind: 'number' }, operation: { kind: 'string' }, operation_result: { kind: 'string' },
      failure_kind: { kind: 'string' }, runtime: { kind: 'enum', values: ['opencode1', 'opencode2'] },
      switch_previous: { kind: 'string' }, switch_target: { kind: 'string' }, switch_phase: { kind: 'string' }, switch_return: { kind: 'string' },
    },
    log: { output: { kind: 'string' } },
  };
  const tx = (id, status, log, probes, logProbes = {}) => mk(id, 'termux', { status, log }, [['status', status, probes], ['log', { output: log }, { output: logProbes.output ?? [] }]]);
  const txBase = { port: 4096, runner: 'proot', pid: 123, runtime: 'opencode1' };
  out.phone_termux = {
    schema: txSchema, excluded: {}, source: 'lib/termux/bridge_models.dart (TermuxSetupStatus, the manager status script)',
    cases: [
      tx('termux_installing', { ...txBase, phase: 'installing_opencode', message: 'Fetching the OpenCode package', version: '', started_at: 'NOW-90' }, 'x', { phase: ['Updating this phone'], message: ['Fetching the OpenCode package'], started_at: ['elapsed'] }),
      tx('termux_starting', { ...txBase, phase: 'starting_server', message: 'Starting the server', version: '1.18.29' }, 'x', { phase: ['Updating this phone'], message: ['Starting the server'], version: ['1.18.29'] }),
      tx('termux_failed', { ...txBase, phase: 'failed', message: 'OpenCode install failed', version: '', failure_kind: 'crash', operation: 'op1', operation_result: 'failed' }, '[oc] ERROR: npm could not install', { phase: ["Setup didn't finish"], message: ['OpenCode install failed'] }, { output: ['npm could not install'] }),
      tx('termux_ready', { ...txBase, phase: 'ready', message: 'OpenCode is ready', version: '1.18.29', operation: 'op2', operation_result: 'completed' }, '', { phase: ['All set'], version: ['1.18.29'] }),
      tx('termux_switch', { ...txBase, runtime: 'opencode2', phase: 'restarting', message: 'Switching to OpenCode 2', version: '2.0.10', switch_previous: 'opencode1', switch_target: 'opencode2', switch_phase: 'switching', switch_return: 'opencode1' }, 'x', { runtime: ['OpenCode 2'], message: ['Switching to OpenCode 2'], switch_target: ['OpenCode 2'] }),
    ],
  };

  // ------------------------------------------------------------ component
  const cmpSchema = {
    component: {
      id: { kind: 'string' }, title: { kind: 'string' }, shortTitle: { kind: 'string' }, why: { kind: 'string' }, summary: { kind: 'string' },
      'dependsOn[]': { kind: 'string' }, required: { kind: 'boolean' }, defaultOn: { kind: 'boolean' }, estimatedSeconds: { kind: 'number' },
      downloadBytes: { kind: 'number' }, installedBytes: { kind: 'number' }, downloadSize: { kind: 'string' },
      native: { kind: 'boolean' }, agentUser: { kind: 'boolean' }, jobStep: { kind: 'boolean' },
      checkScript: { kind: 'string' }, installScript: { kind: 'string' }, removeScript: { kind: 'string' }, presenceScript: { kind: 'string' }, sizeScript: { kind: 'string' },
      app: { kind: 'string' },
    },
  };
  const cdef = (id, o) => ({ id, title: o.title, shortTitle: o.short ?? o.title, ...(o.why ? { why: o.why } : {}), ...(o.summary ? { summary: o.summary } : {}),
    ...(o.deps ? { dependsOn: o.deps } : {}), required: !!o.required, defaultOn: !!o.defaultOn, estimatedSeconds: o.est, downloadBytes: o.bytes, ...(o.app ? { app: 'voice typing' } : {}) });
  const cparts = [
    [cdef('linux', { title: 'Linux base', why: 'Everything else runs inside it.', required: true, est: 15, bytes: 30000000 }), { title: ['Linux base'], why: ['Everything else runs inside it.'], required: ['Required'] }],
    [cdef('essentials', { title: 'Git, SSH and certificates', short: 'Git and SSH', why: 'Agents use Git and SSH to work on your projects.', deps: ['linux'], required: true, est: 60, bytes: 45000000 }), { title: ['Git, SSH and certificates'], shortTitle: ['Git and SSH'], why: ['Agents use Git and SSH to work on your projects.'], required: ['Required'] }],
    [cdef('python', { title: 'Python', deps: ['essentials'], defaultOn: true, est: 40, bytes: 25000000 }), { title: ['Python'], shortTitle: ['Python'], defaultOn: ['[python on]'], downloadBytes: ['~25 MB'], 'dependsOn[]': [] }],
    [cdef('node', { title: 'Node.js', why: 'OpenCode runs on Node.js.', deps: ['essentials'], required: true, est: 20, bytes: 58000000 }), { title: ['Node.js'], shortTitle: ['Node.js'], why: ['OpenCode runs on Node.js.'], required: ['Required'] }],
    [cdef('opencode', { title: 'OpenCode', why: 'The coding agent itself.', deps: ['node'], required: true, est: 100, bytes: 50000000 }), { title: ['OpenCode'], why: ['The coding agent itself.'], required: ['Required'] }],
    [cdef('aiteam', { title: 'AI Team', deps: ['opencode', 'python'], est: 150, bytes: 115000000 }), { title: ['AI Team'], defaultOn: ['[aiteam off]'], downloadBytes: ['~115 MB'] }],
    [cdef('voice', { title: 'Voice typing', summary: 'Speak instead of typing, even offline', est: 45, bytes: 160000000, app: true }), { title: ['Voice typing'], summary: ['Speak instead of typing, even offline'], downloadBytes: ['~160 MB'], defaultOn: ['[voice off]'] }],
  ];
  const cmpCase = (id, extraProbes = {}) => ({
    id, kind: 'component', payload: { components: cparts.map(([v]) => v) },
    parts: cparts.map(([value, probes], i) => {
      const flat = Object.fromEntries(Object.keys(flatPaths(value)).map((k) => [k, []]));
      return { group: 'component', value, probes: { ...flat, ...probes, ...(i === 0 ? extraProbes : {}) } };
    }),
  });
  out.phone_component = {
    schema: cmpSchema,
    excluded: Object.fromEntries(['installedBytes', 'downloadSize', 'native', 'agentUser', 'jobStep', 'checkScript', 'installScript', 'removeScript', 'presenceScript', 'sizeScript'].map((k) => [`component.${k}`, 'code and engine bookkeeping; never a value a person reads'])), source: 'lib/builtin/setup/components.dart (the registry the Start page and Customize sheet draw)',
    cases: [cmpCase('component_first_setup', { estimatedSeconds: ['minutes'] })],
  };

  // -------------------------------------------------------------- storage
  const stSchema = {
    gate: {
      server: { kind: 'enum', values: ['inApp', 'termux', 'remote'] }, accessGranted: { kind: 'boolean' }, accessAfterAllow: { kind: 'boolean' }, termuxCanRead: { kind: 'boolean' },
      confinedRunning: { kind: 'boolean' }, restartBack: { kind: 'boolean' }, workRunning: { kind: 'boolean' },
    },
  };
  const stc = (id, value, probes) => mk(id, 'storage', value, [['gate', value, probes]]);
  const stBase = { server: 'inApp', accessGranted: false, accessAfterAllow: true, termuxCanRead: true, confinedRunning: false, restartBack: true, workRunning: false };
  out.phone_storage = {
    schema: stSchema, excluded: {}, source: 'lib/state/shared_storage_gate.dart and lib/ui/screens/shared_storage_access_flow.dart (what the phone says about file access)',
    cases: [
      stc('storage_app_refused', { ...stBase, accessAfterAllow: false }, { server: ['Allow access to files?'], accessAfterAllow: ['Folder not opened'] }),
      stc('storage_app_granted', { ...stBase }, { accessGranted: ['Allow access to files?'] }),
      stc('storage_termux', { ...stBase, server: 'termux', termuxCanRead: false }, { server: ['Allow Termux storage?'], termuxCanRead: ['Allow storage in Termux'] }),
      stc('storage_remote', { ...stBase, server: 'remote' }, {}),
      stc('storage_restart', { ...stBase, accessGranted: true, confinedRunning: true }, { confinedRunning: ['Restart to open folder?'], workRunning: ['pause and carry on'] }),
      stc('storage_restart_busy', { ...stBase, accessGranted: true, confinedRunning: true, workRunning: true }, { workRunning: ['running right now'] }),
      stc('storage_restart_failed', { ...stBase, accessGranted: true, confinedRunning: true, restartBack: false }, { restartBack: ['Restart did not finish'] }),
    ],
  };

  // -------------------------------------------------------------- catalog
  const source = JSON.parse(readFileSync('test/fixtures/coverage/phone_agent_catalog_source.json', 'utf8')).agents;
  const sizeText = (b) => (b >= 1e9 ? `${(b / 1e9).toFixed(1)} GB` : b >= 1e6 ? `${Math.round(b / 1e6)} MB` : b >= 1e3 ? `${Math.round(b / 1e3)} kB` : `${b} B`);
  const listed = source.filter((a) => (a.route === 'paseoNative' || a.route === 'acpPaseo') && a.availability !== 'hidden');
  const catSchema = { agent: {} };
  const catFields = (a) => Object.keys(flatPaths(a));
  for (const a of source) for (const path of catFields(a)) catSchema.agent[path] = { kind: 'string' };
  out.phone_agent_catalog = {
    schema: catSchema, excluded: {}, source: 'lib/domain/agent_catalog.dart (AgentCatalog.builtIn, dumped to phone_agent_catalog_source.json)',
    cases: listed.map((a) => {
      const arm = a.recipe?.artifacts?.arm64?.downloadBytes;
      const probes = { name: [a.name] };
      if (arm != null) probes['recipe.artifacts.arm64.downloadBytes'] = [sizeText(arm)];
      return mk(`catalog_${a.id}`, 'catalog', { agent: a }, [['agent', a, probes]]);
    }),
  };

  // -------------------------------------------------------- certification
  const matrix = JSON.parse(readFileSync('docs/verification/agent-certification-matrix.json', 'utf8'));
  const cellNames = matrix.columns.map((c) => c.id);
  const certSchema = { cert: { agentVersion: { kind: 'string' }, helperVersion: { kind: 'string' }, architecture: { kind: 'string' } } };
  for (const c of cellNames) { certSchema.cert[`cells.${c}.state`] = { kind: 'string' }; certSchema.cert[`cells.${c}.evidence`] = { kind: 'string' }; }
  const certRow = (id) => { const r = matrix.agents.find((x) => x.id === id); return { agentVersion: r.agentVersion, helperVersion: r.helperVersion, ...(r.architecture ? { architecture: r.architecture } : {}), cells: r.cells }; };
  out.phone_agent_certification = {
    schema: certSchema, excluded: {}, source: 'docs/verification/agent-certification-matrix.json (bundled as agent_certification_snapshot.dart)',
    cases: [
      mk('certification_claude', 'certification', { id: 'claude', row: certRow('claude') }, [['cert', certRow('claude'), { 'cells.install.state': ['Ready'], 'cells.smoke.state': ['Ready'] }]]),
      mk('certification_codex', 'certification', { id: 'codex', row: certRow('codex') }, [['cert', certRow('codex'), { 'cells.smoke.state': ['Not certified on this version yet'] }]]),
    ],
  };
  return out;
}
