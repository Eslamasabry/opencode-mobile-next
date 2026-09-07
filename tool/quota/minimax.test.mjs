// Entirely synthetic: no provider calls, listening sockets, or credential files.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { CACHE_MS, MINIMAX_QUOTA_PATH, MINIMAX_QUOTA_URL, createCollector,
  createMiniMaxKeySource, createRequestHandler, loadConfiguration, mapMiniMaxUsage } from './collector.mjs';

const READ_TOKEN = 'SYNTHETIC_READ_TOKEN_abcdefghijklmnopqrstuvwxyz0123456789';
const KEY_A = 'synthetic-subscription-key-A';
const KEY_B = 'synthetic-subscription-key-B';
const NOW = 1_800_000_000_000;
const fixture = JSON.parse(await readFile(new URL('./fixtures/minimax-general.json', import.meta.url)));
const payload = () => structuredClone(fixture);
const response = (body = payload(), status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});
const collector = (options = {}) => createCollector({ readToken: READ_TOKEN, provider: 'minimax',
  clock: () => NOW, authSource: async () => ({ status: 'ok', accessToken: KEY_A }),
  fetchImpl: async () => response(), ...options });
const encoded = (text) => new TextEncoder().encode(text);

function assertEmpty(value, status) {
  assert.equal(value.status, status);
  assert.equal(value.freshness, 'none');
  assert.deepEqual(value.windows, []);
  assert.equal(value.ordinaryUsageAllowed, null);
}

test('MiniMax explicit reported percentages, scopes, units and epoch resets are preserved', () => {
  const value = mapMiniMaxUsage(payload(), 'opaque-ref', NOW);
  assert.equal(value.provider, 'minimax');
  assert.equal(value.source, 'minimax.tokenPlan');
  assert.deepEqual(value.account, { ref: 'opaque-ref', status: 'sourceBound' });
  assert.equal(value.ordinaryUsageAllowed, null);
  assert.equal(value.expiresAtMs, NOW + CACHE_MS);
  assert.deepEqual(value.windows, [
    { id: 'primary', status: 'reported', usedPercent: 27.5,
      durationSeconds: 18000, resetsAtMs: 1800010800000 },
    { id: 'secondary', status: 'reported', usedPercent: 60,
      durationSeconds: 604800, resetsAtMs: 1800345600000 },
  ]);
  // Fixture counts intentionally disagree: no count, token, or spend conversion.
  assert.equal(JSON.stringify(value).includes('999'), false);
});

test('MiniMax missing percentages, unlimited and boosted windows stay unknown', () => {
  for (const adjust of [
    (row) => { delete row.current_weekly_remaining_percent; },
    (row) => { row.current_weekly_status = 3; },
    (row) => { row.weekly_boost_permille = 1500; },
  ]) {
    const body = payload(); adjust(body.model_remains[0]);
    const value = mapMiniMaxUsage(body, 'ref', NOW);
    assert.equal(value.windows[0].status, 'reported');
    assert.deepEqual(value.windows[1], { id: 'secondary', status: 'missing' });
  }
  const body = payload(); body.model_remains[0].model_name = 'MiniMax-M*';
  assertEmpty(mapMiniMaxUsage(body, 'ref', NOW), 'unsupported');
});

test('MiniMax rejects malformed/ambiguous percentages, statuses, scopes and resets', async () => {
  for (const change of [
    (body) => { body.model_remains[0].current_interval_remaining_percent = -1; },
    (body) => { body.model_remains[0].current_interval_remaining_percent = 101; },
    (body) => { body.model_remains[0].current_interval_remaining_percent = '50'; },
    (body) => { body.model_remains[0].current_interval_status = 4; },
    (body) => { body.model_remains[0].current_interval_status = 2; },
    (body) => { body.model_remains[0].end_time = 0; },
    (body) => { delete body.model_remains[0].start_time; },
    (body) => { delete body.model_remains[0].end_time; },
    (body) => { delete body.model_remains[0].weekly_start_time; },
    (body) => { delete body.model_remains[0].weekly_end_time; },
    (body) => { body.model_remains[0].end_time = null; },
    (body) => { body.model_remains[0].start_time = '1799992800000'; },
    (body) => { body.model_remains[0].weekly_end_time = null; },
    (body) => { body.model_remains[0].weekly_boost_permille = -1; },
    (body) => { body.model_remains.push(body.model_remains[0]); },
    (body) => { body.model_remains = [null]; },
  ]) {
    const body = payload(); change(body);
    assertEmpty(await collector({ fetchImpl: async () => response(body) }).readSnapshot(), 'invalidResponse');
  }
  const body = payload(); body.base_resp.status_code = 1004;
  assertEmpty(await collector({ fetchImpl: async () => response(body) }).readSnapshot(), 'unavailable');
});

test('MiniMax exhaustion is provider reported and does not grant ordinary usage', () => {
  const body = payload();
  body.model_remains[0].current_interval_status = 2;
  body.model_remains[0].current_interval_remaining_percent = 0;
  const value = mapMiniMaxUsage(body, 'ref', NOW);
  assert.equal(value.windows[0].usedPercent, 100);
  assert.equal(value.ordinaryUsageAllowed, null);
});

test('MiniMax key source requires explicit file; rejects PAYG and never discovers OAuth', async () => {
  let reads = 0;
  const read = async () => { reads++; return encoded(`${KEY_A}\n`); };
  assert.deepEqual(await createMiniMaxKeySource({ readFile: read })(), { status: 'unconfigured' });
  assert.equal(reads, 0);
  assert.deepEqual(await createMiniMaxKeySource({ filePath: '/synthetic/key', readFile: read })(),
    { status: 'ok', accessToken: KEY_A });
  for (const [content, status] of [['sk-api-SYNTHETIC-payg', 'unsupported'],
    ['key with whitespace', 'authRequired'], ['{"oauth":"synthetic"}', 'authRequired']]) {
    assert.deepEqual(await createMiniMaxKeySource({ filePath: '/synthetic/key',
      readFile: async () => encoded(content) })(), { status });
  }
});

test('MiniMax fetch is host pinned, source bound, cached and invalidated by key rotation', async () => {
  let key = KEY_A, calls = 0;
  const service = collector({ authSource: async () => ({ status: 'ok', accessToken: key }),
    fetchImpl: async (url, options) => {
      calls++; assert.equal(url, MINIMAX_QUOTA_URL);
      assert.equal(options.method, 'GET'); assert.equal(options.redirect, 'error');
      assert.equal(options.credentials, 'omit');
      assert.equal(options.headers.Authorization, `Bearer ${key}`);
      assert.equal(options.headers['ChatGPT-Account-Id'], undefined);
      return response();
    } });
  const first = await service.readSnapshot();
  assert.equal(first.account.status, 'sourceBound');
  assert.equal(first.account.ref.length, 64);
  assert.equal(JSON.stringify(first).includes(KEY_A), false);
  await service.readSnapshot(); assert.equal(calls, 1);
  key = KEY_B;
  const second = await service.readSnapshot();
  assert.equal(calls, 2); assert.notEqual(first.account.ref, second.account.ref);
  key = 'sk-api-SYNTHETIC-payg';
  assertEmpty(await service.readSnapshot(), 'unsupported'); assert.equal(calls, 2);
});

test('MiniMax expired cache and transport errors never preserve falsely fresh quota', async () => {
  let now = NOW, status = 200;
  const service = collector({ clock: () => now, fetchImpl: async () => response(payload(), status) });
  await service.readSnapshot(); now += CACHE_MS;
  for (const [code, expected] of [[401, 'authRequired'], [429, 'rateLimited'],
    [404, 'unsupported'], [500, 'unavailable']]) {
    status = code; assertEmpty(await service.readSnapshot(), expected);
  }
});

test('MiniMax route shares authenticated exact-path request handling', async () => {
  let calls = 0;
  const handler = createRequestHandler({ readToken: READ_TOKEN,
    minimaxCollector: { readSnapshot: async () => { calls++; return { provider: 'minimax' }; } } });
  async function invoke(url, token = READ_TOKEN) {
    const result = {};
    await handler({ method: 'GET', url, rawHeaders: [],
      headers: { authorization: `Bearer ${token}` }, socket: { remoteAddress: '127.0.0.1' } }, {
      writeHead(status) { result.status = status; },
      end(body) { result.body = JSON.parse(body); },
    });
    return result;
  }
  assert.equal((await invoke(MINIMAX_QUOTA_PATH, 'wrong')).status, 401);
  assert.equal((await invoke(`${MINIMAX_QUOTA_PATH}?key=ignored`)).status, 404);
  assert.equal(calls, 0);
  assert.deepEqual(await invoke(MINIMAX_QUOTA_PATH), { status: 200, body: { provider: 'minimax' } });
});

test('MiniMax CLI config accepts only explicit absolute key paths and loads no key at startup', async () => {
  const reads = [];
  const env = { OCMN_QUOTA_READ_TOKEN_FILE: '/synthetic/read', OCMN_MINIMAX_KEY_FILE: '/synthetic/minimax' };
  const config = await loadConfiguration(env, { readFile: async (path) => {
    reads.push(path); return encoded(READ_TOKEN);
  } });
  assert.equal(config.minimaxKeyFile, '/synthetic/minimax');
  assert.deepEqual(reads, ['/synthetic/read']);
  await assert.rejects(loadConfiguration({ ...env, OCMN_MINIMAX_KEY_FILE: 'relative' }),
    { code: 'QUOTA_MINIMAX_KEY_FILE_INVALID' });
});
