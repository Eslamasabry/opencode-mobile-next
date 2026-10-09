// Families for settings, saved permissions, automation and keep running: what
// a server sends those pages.
//   oc1_settings - always-allowed actions (/api/permission/saved) and the
//                  shells the default-shell row offers (/pty/shells, the
//                  server's configured `shell`).
//   oc2_settings - always-allowed actions and the managed commands
//                  Development services lists (/api/shell).
// The app's own stored preferences are decided in settings_preferences_ledger.json.
import { familyBuilder } from '../oc_cases_lib.mjs';
import { only, none } from './models.mjs';

const f = (kind, values) => ({ kind, ...(values ? { values } : {}) });
const T = 1788960000000;
const u = (use, g, v, o = {}) => use(g, v, { ...o, probes: only(v, o.probes ?? {}) });

export function oc1SettingsFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'PermissionSavedInfo' },
    { name: 'pty:shell', extra: { path: f('string'), name: f('string'), acceptable: f('boolean') } },
    { name: 'config:shell', extra: { shell: f('string') } },
  ]);
  const cases = [];
  cases.push(kase('saved', (use) => [
    u(use, 'PermissionSavedInfo', { id: 'psv_settings1', projectID: 'prj_shopfront', action: 'bash', resource: 'git push *' }, { probes: { ...none('id', 'projectID'), action: ['Run a shell command'], resource: ['git push *'] } }),
    u(use, 'PermissionSavedInfo', { id: 'psv_settings2', projectID: 'prj_shopfront', action: 'edit', resource: 'lib/checkout/*' }, { probes: { ...none('id', 'projectID'), action: ['Edit a file'], resource: ['lib/checkout/*'] } }),
  ], { kind: 'saved' }));
  cases.push(kase('shells', (use) => ({
    shells: [
      u(use, 'pty:shell', { path: '/bin/bash', name: 'bash', acceptable: true }, { probes: { path: ['/bin/bash'], name: ['bash'], acceptable: [] } }),
      u(use, 'pty:shell', { path: '/usr/bin/fish', name: 'fish', acceptable: false }, { probes: { path: [], name: ['fish'], acceptable: ['Terminal only'] } }),
    ],
    config: u(use, 'config:shell', { shell: '/bin/bash' }, { probes: { shell: ['/bin/bash'] } }),
  }), { kind: 'shells' }));
  return { schema, cases };
}

export function oc2SettingsFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'PermissionSaved.Info' },
    { name: 'Shell.Info' },
  ]);
  const cases = [];
  cases.push(kase('saved', (use) => [
    u(use, 'PermissionSaved.Info', { id: 'psv_settings1', projectID: 'prj_shopfront', action: 'bash', resource: 'git push *' }, { probes: { ...none('id', 'projectID'), action: ['Run a shell command'], resource: ['git push *'] } }),
  ], { kind: 'saved' }));
  cases.push(kase('shell_running', (use) => [u(use, 'Shell.Info', {
    id: 'sh_settings1', status: 'running', command: 'npm run dev', cwd: '/work/shopfront', shell: '/bin/bash', file: '/tmp/oc-shell-1.log', pid: 4242,
    metadata: { ocDevelopmentServiceOwner: 'owner_settings', sessionID: 'ses_settings1' }, time: { started: T },
  }, { probes: { ...none('id', 'shell', 'file', 'pid', 'metadata', 'time.started'), status: ['Running command'], command: ['npm run dev'], cwd: ['/work/shopfront'] } })], { kind: 'shell' }));
  cases.push(kase('shell_exited', (use) => [u(use, 'Shell.Info', {
    id: 'sh_settings2', status: 'exited', command: 'npm run dev', cwd: '/work/shopfront', shell: '/bin/bash', file: '/tmp/oc-shell-2.log', pid: 4243, exit: 137,
    metadata: { ocDevelopmentServiceOwner: 'owner_settings' }, time: { started: T, completed: T + 60000 },
  }, { probes: { ...none('id', 'shell', 'file', 'pid', 'metadata', 'time.started', 'time.completed'), status: ['Stopped'], command: ['npm run dev'], cwd: ['/work/shopfront'], exit: ['Recorded exit code: 137'] } })], { kind: 'shell' }));
  return { schema, cases };
}
