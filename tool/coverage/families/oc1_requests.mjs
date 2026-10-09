// Family "oc1_requests": what an OpenCode 1 server asks the person: permission
// requests (PermissionRequest) and questions (QuestionRequest). The contract
// types a permission's `metadata` as a free object; the keys OpenCode's tools
// put there and the app reads are the group "permission:metadata".
import { familyBuilder } from '../oc_cases_lib.mjs';

const str = (...paths) => Object.fromEntries(paths.map((p) => [p, { kind: 'string' }]));

export function oc1RequestsFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'PermissionRequest' },
    { name: 'permission:metadata', extra: { ...str('command', 'filepath', 'diff', 'url', 'format', 'parentDir', 'description', 'subagent_type'), offset: { kind: 'number' }, limit: { kind: 'number' } } },
    { name: 'QuestionRequest' },
  ]);
  const S = 'ses_checkout';
  const tool = { messageID: 'msg_ask01', callID: 'call_ask01' };
  const cases = [];
  const perm = (id, permission, patterns, always, metadata, probes = {}, metaProbes = {}) =>
    cases.push(kase(id, (use) => {
      if (Object.keys(metadata).length) use('permission:metadata', metadata, { probes: metaProbes });
      return use('PermissionRequest', { id: `per_${id}`, sessionID: S, permission, patterns, metadata, always, tool }, { probes: { metadata: [], ...probes }, primary: ['patterns[]'] });
    }, { kind: 'permission' }));
  perm('perm_bash', 'bash', ['git push origin main'], ['git push *'], { command: 'git push origin main', description: 'Publish the retry button branch' });
  perm('perm_bash_patterns_only', 'bash', ['flutter test test/upload_screen_test.dart'], ['flutter test *'], {});
  perm('perm_edit', 'edit', ['lib/upload/upload_screen.dart'], ['lib/upload/*'], { filepath: '/work/app/lib/upload/upload_screen.dart', diff: '--- a/lib/upload/upload_screen.dart\n+++ b/lib/upload/upload_screen.dart\n@@ -1 +1 @@\n-final retries = 1;\n+final retries = 3;' }, {}, { filepath: ['upload_screen.dart'], diff: ['final retries = 3;'] });
  perm('perm_read_range', 'read', ['/work/app/lib/main.dart'], ['/work/app/*'], { offset: 20, limit: 80 }, {}, { offset: ['from line 20'], limit: ['80 lines'] });
  perm('perm_webfetch', 'webfetch', ['https://docs.flutter.dev/cookbook/networking'], ['https://docs.flutter.dev/*'], { url: 'https://docs.flutter.dev/cookbook/networking', format: 'markdown' });
  perm('perm_external_directory', 'external_directory', ['/etc/hosts/*'], ['/etc/hosts/*'], { filepath: '/etc/hosts', parentDir: '/etc' }, { 'patterns[]': [] }, { filepath: ['/etc/hosts'] });
  perm('perm_task', 'task', ['explore'], ['explore'], { description: 'Audit the upload retry code', subagent_type: 'explore' });

  const question = (id, questions, probes = {}) => cases.push(kase(id, (use) => use('QuestionRequest', { id: `que_${id}`, sessionID: S, questions, tool }, { probes: { 'questions[].multiple': [], 'questions[].custom': [], ...probes }, primary: ['questions[].question', 'questions[].options[].label'] }), { kind: 'question' }));
  question('question_single_custom', [{ question: 'Which retry limit should the upload screen use?', header: 'Retry limit', options: [{ label: 'Three tries', description: 'Stops after three failed attempts' }, { label: 'Keep trying', description: 'Retries until the network is back' }], multiple: false, custom: true }], { 'questions[].custom': ['Something else'] });
  question('question_multiple', [{ question: 'Which screens should get the new retry button?', header: 'Screens', options: [{ label: 'Uploads', description: 'The upload queue screen' }, { label: 'Downloads', description: 'The download history' }, { label: 'Backups', description: 'The nightly backup list' }], multiple: true, custom: false }], { 'questions[].multiple': ['None selected'] });
  question('question_two_questions', [
    { question: 'Should the retry run in the background?', header: 'Background', options: [{ label: 'Yes', description: 'Keep going when the app is closed' }, { label: 'No', description: 'Only while the screen is open' }], multiple: false, custom: false },
    { question: 'What should the button say?', header: 'Label', options: [{ label: 'Retry', description: 'Short and plain' }, { label: 'Try again', description: 'Friendlier' }], multiple: false, custom: true },
  ]);
  return { schema, cases };
}
