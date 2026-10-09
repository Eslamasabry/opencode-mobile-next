// Family "oc2_requests": what an OpenCode 2 server asks the person: permission
// requests (Permission.Request) and forms (Form.Info with its six field
// types), from contracts/opencode2-openapi-beta-18600.json. A permission's
// `metadata` is a free object: its keys come from the tools ("permission:
// metadata"), as in oc1_requests.
import { familyBuilder } from '../oc_cases_lib.mjs';

const str = (...paths) => Object.fromEntries(paths.map((p) => [p, { kind: 'string' }]));

export function oc2RequestsFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'Permission.Request' },
    { name: 'permission:metadata', extra: { ...str('command', 'filepath', 'diff', 'url', 'format', 'parentDir', 'description', 'subagent_type'), offset: { kind: 'number' }, limit: { kind: 'number' } } },
    { name: 'Form.Info', stop: ['Form.Fields_2'] },
    { name: 'Form.StringField' },
    { name: 'Form.NumberField' },
    { name: 'Form.IntegerField' },
    { name: 'Form.BooleanField' },
    { name: 'Form.MultiselectField' },
    { name: 'Form.ExternalField' },
  ]);
  const cases = [];
  const src = (n) => ({ messageID: `msg_p${n}`, id: `call_p${n}` });
  let n = 0;
  const perm = (id, action, resources, save, metadata, message, probes = {}, metaProbes = {}) => cases.push(kase(id, (use) => {
    n += 1;
    if (Object.keys(metadata).length) use('permission:metadata', metadata, { probes: metaProbes });
    return use('Permission.Request', { id: `per_${id}`, sessionID: 'ses_checkout', action, resources, save, metadata, source: src(n), ...(message ? { message } : {}) }, { probes: { metadata: [], ...probes }, primary: ['resources[]'] });
  }, { kind: 'permission' }));
  perm('perm2_bash', 'bash', ['git push origin main'], ['git push *'], { command: 'git push origin main', description: 'Publish the retry button branch' }, 'The agent wants to publish the branch it just finished', {}, { description: [] });
  perm('perm2_bash_no_message', 'bash', ['flutter test test/upload_screen_test.dart'], ['flutter test *'], {}, undefined);
  perm('perm2_edit', 'edit', ['lib/upload/upload_screen.dart'], ['lib/upload/*'], { filepath: '/work/app/lib/upload/upload_screen.dart', diff: '--- a/lib/upload/upload_screen.dart\n+++ b/lib/upload/upload_screen.dart\n@@ -1 +1 @@\n-final retries = 1;\n+final retries = 3;' }, undefined, {}, { filepath: ['upload_screen.dart'], diff: ['final retries = 3;'] });
  perm('perm2_read_range', 'read', ['/work/app/lib/main.dart'], ['/work/app/*'], { offset: 20, limit: 80 }, undefined, {}, { offset: ['from line 20'], limit: ['80 lines'] });
  perm('perm2_webfetch', 'webfetch', ['https://docs.flutter.dev/cookbook/networking'], ['https://docs.flutter.dev/*'], { url: 'https://docs.flutter.dev/cookbook/networking', format: 'markdown' }, undefined);
  perm('perm2_external_directory', 'external_directory', ['/etc/hosts/*'], ['/etc/hosts/*'], { filepath: '/etc/hosts', parentDir: '/etc' }, undefined, { 'resources[]': [] }, { filepath: ['/etc/hosts'] });
  perm('perm2_task', 'task', ['explore'], ['explore'], { description: 'Audit the upload retry code', subagent_type: 'explore' }, undefined);

  // Forms: one case per group of field types, so the sheet stays readable.
  const form = (id, title, metadata, build, probes = {}) => cases.push(kase(id, (use) => {
    const fields = build(use);
    return use('Form.Info', { id: `frm_${id}`, sessionID: 'ses_checkout', title, metadata, fields }, { probes: { fields: [], metadata: [], ...probes }, primary: ['title'] });
  }, { kind: 'form' }));
  const whenProbes = { 'when[].key': [], 'when[].op': [], 'when[].value': [] };
  form('form_choose_and_type', 'Connect to Sentry', { server: 'sentry', elicitation: 'oauth-setup' }, (use) => [
    use('Form.StringField', { key: 'target_env', title: 'Environment', description: 'Where the project runs', required: true, options: [{ value: 'prod', label: 'Production', description: 'Live traffic' }, { value: 'stage', label: 'Staging', description: 'Test traffic' }], custom: true, default: 'prod' }, { probes: { key: [], required: ['Environment *'], 'options[].value': [], custom: ['Other…'], default: [] } }),
    use('Form.StringField', { key: 'contact_addr', title: 'Contact email', description: 'Where alerts are sent', required: false, format: 'email', minLength: 6, maxLength: 80, pattern: '^[^@]+@[^@]+$', placeholder: 'you@example.com', default: 'oncall@example.com', when: [{ key: 'target_env', op: 'eq', value: 'prod' }] }, { probes: { key: [], required: [], format: [], minLength: [], maxLength: [], pattern: [], ...whenProbes } }),
  ]);
  form('form_numbers_and_switch', 'Upload settings', {}, (use) => [
    use('Form.IntegerField', { key: 'tries', title: 'Retries', description: 'How many times to try again', required: true, minimum: 1, maximum: 50, default: 17 }, { probes: { key: [], required: ['Retries *'], minimum: [], maximum: [], default: ['17'] } }),
    use('Form.IntegerField', { key: 'pause', title: 'Delay in seconds', description: 'Wait before each retry', required: false, minimum: 0, maximum: 60, default: 42, when: [{ key: 'tries', op: 'eq', value: 17 }] }, { probes: { key: [], required: [], minimum: [], maximum: [], default: ['42'], ...whenProbes } }),
    use('Form.NumberField', { key: 'squeeze', title: 'Compression ratio', description: 'Between 0 and 1', required: true, minimum: 0.1, maximum: 0.9, default: 0.5, when: [{ key: 'tries', op: 'neq', value: 1 }] }, { probes: { key: [], required: ['Compression ratio *'], minimum: [], maximum: [], default: ['0.5'], ...whenProbes } }),
    use('Form.BooleanField', { key: 'keep_running', title: 'Keep going in the background', description: 'Continue when the app is closed', required: false, default: true, when: [{ key: 'tries', op: 'eq', value: 17 }] }, { probes: { key: [], required: [], default: [], ...whenProbes } }),
  ]);
  form('form_multiselect_and_link', 'Choose screens', {}, (use) => [
    use('Form.BooleanField', { key: 'everywhere', title: 'Every screen', description: 'Skip the choice below', required: false, default: false }, { probes: { key: [], required: [], default: [] } }),
    use('Form.ExternalField', { key: 'sso_step', url: 'https://sentry.io/oauth/authorize?client=oc', title: 'Sign in to Sentry', description: 'Opens your browser to sign in' }, { probes: { key: [], url: ['sentry.io'] } }),
    use('Form.MultiselectField', { key: 'targets', title: 'Screens', description: 'Which screens get the retry button', required: true, options: [{ value: 'uploads', label: 'Uploads', description: 'The upload queue' }, { value: 'downloads', label: 'Downloads', description: 'The download history' }], minItems: 1, maxItems: 2, custom: true, default: ['uploads'], when: [{ key: 'everywhere', op: 'eq', value: false }] }, { probes: { key: [], required: ['Screens *'], 'options[].value': [], minItems: ['Pick 1–2'], maxItems: ['Pick 1–2'], custom: ['Add your own'], 'default[]': [], ...whenProbes } }),
  ]);
  return { schema, cases };
}
