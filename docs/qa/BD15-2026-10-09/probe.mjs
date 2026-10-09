// Offline synthetic transcript probe; never reads a real account or starts Claude.
// Usage: node probe.mjs /absolute/path/to/node_modules
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, readFileSync } from 'node:fs';
import { join, resolve, basename } from 'node:path';
import { tmpdir } from 'node:os';
import { pathToFileURL } from 'node:url';

const modules = resolve(process.argv[2]);
const owned = mkdtempSync(join(tmpdir(), 'bd15-transcript-'));
// Confine all package lookups/materialized fixtures to this probe's own directory.
process.env.CLAUDE_CONFIG_DIR = join(owned, 'claude');
process.env.TMPDIR = owned;
const load = (path) => import(pathToFileURL(join(modules, path)).href);
try {
  const server = '@getpaseo/server/dist/server/server/agent/providers/';
  assert.equal(JSON.parse(readFileSync(join(modules, '@getpaseo/server/package.json'))).version, '0.9.2');
  assert.equal(JSON.parse(readFileSync(join(modules, '@anthropic-ai/claude-agent-sdk/package.json'))).version, '0.3.246');
  const { getSessionMessages } = await load('@anthropic-ai/claude-agent-sdk/sdk.mjs');
  const { ClaudeAgentClient, convertClaudeHistoryEntry } = await load(`${server}claude/agent.js`);
  const { claudeProjectDirSync } = await load(`${server}claude/project-dir.js`);
  const { isProviderImageMarkdown } = await load(`${server}provider-image-output.js`);
  const cwd = join(owned, 'project');
  mkdirSync(cwd);
  const projectDir = claudeProjectDirSync(cwd);
  mkdirSync(projectDir, { recursive: true });
  const sessionId = '00000000-0000-4000-8000-000000000001';
  const messageId = '00000000-0000-4000-8000-000000000002';
  const bytes = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jxioAAAAASUVORK5CYII=', 'base64');
  const hash = createHash('sha256').update(bytes).digest('hex');
  const image = { type: 'image', source: { type: 'base64', media_type: 'image/png', data: bytes.toString('base64') } };
  const record = {
    type: 'user', uuid: messageId, parentUuid: null, sessionId, isSidechain: false,
    timestamp: '2026-10-09T00:00:00.000Z', cwd,
    message: { role: 'user', content: [{ type: 'text', text: 'Fixture picture' }, image] },
  };
  writeFileSync(join(projectDir, `${sessionId}.jsonl`), `${JSON.stringify(record)}\n`);
  const messages = await getSessionMessages(sessionId, { dir: cwd });
  assert.equal(messages.length, 1);
  assert.deepEqual(messages[0].message.content[1], image);
  console.log('PASS: shipped SDK reads inline base64 image blocks from a synthetic session JSONL unchanged.');

  const logger = { child() { return this; }, trace() {}, debug() {}, info() {}, warn() {}, error() {} };
  const client = new ClaudeAgentClient({ logger, defaults: {}, queryFactory() { throw Error('No Claude launch permitted'); } });
  const session = await client.createSession({ provider: 'claude', cwd });
  const prompt = session.toSdkUserMessage([{ type: 'text', text: 'Fixture picture' }, { type: 'image', mimeType: 'image/png', data: image.source.data }]);
  assert.deepEqual(prompt.message.content[1], image);
  const mapped = session.convertHistoryEntry(record);
  assert.deepEqual(mapped, [{ type: 'user_message', text: 'Fixture picture', messageId }]);
  assert.deepEqual(convertClaudeHistoryEntry({ ...record, message: { role: 'user', content: [image] } }, () => { throw Error('Unexpected tool mapper'); }), []);
  console.log('PASS: Paseo sends user images as base64, but its user-history mapper emits text only, not hash links.');

  const bare = `![Image](${hash})`;
  const literal = session.convertHistoryEntry({ ...record, message: { role: 'user', content: bare } });
  assert.equal(literal[0].text, bare);
  const toolRecord = { ...record, message: { role: 'user', content: [{ type: 'tool_result', tool_use_id: 'fixture-tool', content: [image] }] } };
  const toolItems = session.convertHistoryEntry(toolRecord);
  const rendered = toolItems.find((item) => item.type === 'assistant_message' && isProviderImageMarkdown(item.text));
  assert(rendered, 'Expected materialized tool-result image markdown');
  const target = /^!\[Image\]\((.*)\)$/.exec(rendered.text)[1];
  const file = new URL(target);
  assert.equal(basename(file.pathname), `${hash}.png`);
  assert.deepEqual(readFileSync(file), bytes);
  assert.notEqual(rendered.text, bare);
  console.log('PASS: tool-result image filename is SHA-256(decoded bytes).png; markdown contains a full file URI.');
  console.log('PASS: pre-existing bare-hash markdown passes through as text; its source-to-ID mapping is not established.');
  console.log('LIMIT: synthetic JSONL acceptance proves reader compatibility, not what the pinned native Claude writer persists.');
} finally {
  rmSync(owned, { recursive: true, force: true });
}
