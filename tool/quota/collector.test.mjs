// All identities, tokens, payloads, and files in this suite are SYNTHETIC.
// No sockets/listeners or real provider/auth-store access are used.
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { mkdtemp, readFile, rm, stat, symlink, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { setImmediate as tick } from 'node:timers/promises';
import test from 'node:test';
import {
  CACHE_MS, MAX_BYTES, PROVIDER_TIMEOUT_MS, QUOTA_PATH, WHAM_URL, CLAUDE_QUOTA_PATH,
  ConfigurationError, createCollector, createFileAuthSource, createReadTokenVerifier,
  createRequestHandler, loadConfiguration, loadReadToken, parseAuthDocument, readBoundedFile,
  parseClaudeAuthDocument, mapClaudeUsage,
} from './collector.mjs';

const READ_TOKEN = 'SYNTHETIC_READ_TOKEN_abcdefghijklmnopqrstuvwxyz0123456789';
const NOW = 1_800_000_000_000;
const clone = (value) => structuredClone(value);
const auth = (overrides = {}) => ({ status: 'ok', accessToken: 'synthetic-access-token-A',
  accountId: 'synthetic-account-A', userId: 'synthetic-user-A', ...overrides });
const rawWindow = (used = 25) => ({ used_percent: used, limit_window_seconds: 18_000,
  reset_after_seconds: 1800, reset_at: 1_800_001_800 });
const payload = (overrides = {}) => ({ plan_type: 'plus', account_id: 'synthetic-account-A',
  user_id: 'synthetic-user-A', rate_limit: { allowed: false, limit_reached: true,
    primary_window: rawWindow(), secondary_window: { ...rawWindow(100),
      limit_window_seconds: 604_800, reset_after_seconds: 86400, reset_at: 1_800_086_400 } }, ...overrides });
const jsonResponse = (value, status = 200, headers = {}) => new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json', ...headers },
});
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
};

function setup(options = {}) {
  const calls = [];
  let current = auth();
  let time = NOW;
  const collector = createCollector({ readToken: READ_TOKEN, clock: () => time,
    authSource: () => current,
    fetchImpl: async (...args) => { calls.push(args); return jsonResponse(payload()); }, ...options });
  return { collector, calls, setAuth: (value) => { current = value; },
    setTime: (value) => { time = value; } };
}

function assertEmpty(snapshot, status) {
  assert.equal(snapshot.status, status);
  assert.equal(snapshot.freshness, 'none');
  assert.deepEqual(snapshot.windows, []);
  assert.equal(snapshot.ordinaryUsageAllowed, null);
  assert.equal(snapshot.fetchedAtMs, snapshot.expiresAtMs);
}

async function invoke(handler, overrides = {}) {
  const result = {};
  await handler({ method: 'GET', url: QUOTA_PATH, headers: { authorization: `Bearer ${READ_TOKEN}` },
    rawHeaders: [], socket: { remoteAddress: '127.0.0.1' }, ...overrides }, {
    writeHead(status, headers) { result.status = status; result.headers = headers; },
    end(body) { result.body = JSON.parse(body); },
  });
  return result;
}

test('success is exactly the frozen domain snapshot; outbound request is fixed and read-only', async () => {
  const h = setup();
  const snapshot = await h.collector.readSnapshot();
  assert.deepEqual(snapshot, {
    schemaVersion: 1, provider: 'codex', source: 'codex.wham', status: 'ok', freshness: 'fresh',
    fetchedAtMs: NOW, expiresAtMs: NOW + CACHE_MS,
    account: { ref: createHmac('sha256', READ_TOKEN).update('synthetic-account-A').digest('hex'),
      status: 'matched', plan: 'plus' }, ordinaryUsageAllowed: false,
    windows: [
      { id: 'primary', status: 'reported', usedPercent: 25, durationSeconds: 18000, resetsAtMs: 1800001800000 },
      { id: 'secondary', status: 'reported', usedPercent: 100, durationSeconds: 604800, resetsAtMs: 1800086400000 },
    ],
  });
  assert.equal(h.calls.length, 1);
  const [url, options] = h.calls[0];
  assert.equal(url, WHAM_URL);
  assert.equal(new URL(url).origin, 'https://chatgpt.com');
  assert.equal(new URL(url).search, '');
  assert.equal(options.method, 'GET');
  assert.equal(options.redirect, 'error');
  assert.equal(options.credentials, 'omit');
  assert.equal(Object.hasOwn(options, 'body'), false);
  assert.deepEqual(options.headers, { Authorization: 'Bearer synthetic-access-token-A',
    'ChatGPT-Account-Id': 'synthetic-account-A', Accept: 'application/json', 'User-Agent': 'ocmn-quota/1' });
  assert.equal(Object.hasOwn(options.headers, 'x-openai-codex-luna-reserve'), false);
});

test('zero, 100, and fractional percentages preserve reported units, independent of allowed', async () => {
  for (const used of [0, 100, 0.5, 42.75]) {
    for (const allowed of [true, false]) {
      const data = payload();
      data.rate_limit.primary_window.used_percent = used;
      data.rate_limit.allowed = allowed;
      const { collector } = setup({ fetchImpl: async () => jsonResponse(data) });
      const snapshot = await collector.readSnapshot();
      assert.equal(snapshot.windows[0].usedPercent, used);
      assert.equal(snapshot.ordinaryUsageAllowed, allowed);
    }
  }
});

test('absent/null windows stay missing, not full or empty capacity; absent/null reset stays absent', async () => {
  for (const rate of [null, undefined, { allowed: true, limit_reached: false, primary_window: null }]) {
    const { collector } = setup({ fetchImpl: async () => jsonResponse(payload({ rate_limit: rate })) });
    assert.deepEqual((await collector.readSnapshot()).windows,
      [{ id: 'primary', status: 'missing' }, { id: 'secondary', status: 'missing' }]);
  }
  for (const reset of [null, undefined]) {
    const data = payload();
    data.rate_limit.primary_window = { used_percent: 0, reset_at: reset, limit_window_seconds: null };
    const { collector } = setup({ fetchImpl: async () => jsonResponse(data) });
    assert.deepEqual((await collector.readSnapshot()).windows[0],
      { id: 'primary', status: 'reported', usedPercent: 0 });
  }
});

test('absolute reset wins over relative countdown; unusual window lengths are not relabeled', async () => {
  const data = payload();
  data.rate_limit.primary_window = { ...rawWindow(100), limit_window_seconds: 61,
    reset_after_seconds: 999999, reset_at: 1_700_000_000 };
  const { collector } = setup({ fetchImpl: async () => jsonResponse(data) });
  const window = (await collector.readSnapshot()).windows[0];
  assert.equal(window.usedPercent, 100);
  assert.equal(window.durationSeconds, 61);
  assert.equal(window.resetsAtMs, 1_700_000_000_000);
});

test('known malformed windows fail visibly rather than becoming missing', async () => {
  const badWindows = [false, [], {}, { ...rawWindow(), used_percent: null },
    ...[-1, 100.01, '25'].map((value) => ({ ...rawWindow(), used_percent: value })),
    ...[0, -1, 1.5, '300', 315360001].map((value) => ({ ...rawWindow(), limit_window_seconds: value })),
    ...[-1, 0.25, '1800', 8640000000001].map((value) => ({ ...rawWindow(), reset_at: value })),
    ...[-1, 1.5, '60'].map((value) => ({ ...rawWindow(), reset_after_seconds: value }))];
  for (const window of badWindows) {
    const data = payload();
    data.rate_limit.primary_window = window;
    const { collector } = setup({ fetchImpl: async () => jsonResponse(data) });
    assertEmpty(await collector.readSnapshot(), 'invalidResponse');
  }
});

test('invalid payload, JSON, UTF-8, content type, missing plan, and non-finite percent reject safely', async () => {
  const malformed = [null, [], {}, { data: payload() }, payload({ plan_type: null }),
    payload({ rate_limit: [] }), payload({ rate_limit: { primary_window: rawWindow() } }),
    payload({ rate_limit: { allowed: null, limit_reached: false } }), payload({ account_id: 3 })];
  for (const body of malformed) {
    const { collector } = setup({ fetchImpl: async () => jsonResponse(body) });
    assertEmpty(await collector.readSnapshot(), 'invalidResponse');
  }
  for (const body of ['not json', '{', JSON.stringify(payload()).replace('"used_percent":25', '"used_percent":1e309'),
    new Uint8Array([0xff, 0xfe])]) {
    const { collector } = setup({ fetchImpl: async () => new Response(body,
      { headers: { 'Content-Type': 'application/json' } }) });
    assertEmpty(await collector.readSnapshot(), 'invalidResponse');
  }
  const { collector } = setup({ fetchImpl: async () => new Response('<html>synthetic-private-body</html>') });
  assertEmpty(await collector.readSnapshot(), 'invalidResponse');
});

test('unknown data, unsafe plan copy, extra buckets, identities and credentials never leave the collector', async () => {
  const data = payload({ plan_type: 'synthetic-private-plan', email: 'synthetic@example.invalid',
    access_token: 'synthetic-private-provider-token', rate_limit_upsell: { body: 'synthetic-private-banner' },
    additional_rate_limits: [{ metered_feature: 'synthetic-extra', rate_limit: {} }],
    credits: { balance: 'synthetic-private-balance' } });
  const { collector } = setup({ fetchImpl: async () => jsonResponse(data) });
  const snapshot = await collector.readSnapshot();
  assert.equal(snapshot.account.plan, undefined);
  assert.equal(snapshot.windows.length, 2);
  assert.match(snapshot.account.ref, /^[a-f0-9]{64}$/);
  assert.equal(/synthetic|private|@/.test(JSON.stringify(snapshot)), false);
});

test('missing/mismatched provider account or selected user suppresses every measurement', async () => {
  for (const [fields, status] of [
    [{ account_id: undefined }, 'unverified'], [{ account_id: null }, 'unverified'],
    [{ user_id: undefined }, 'unverified'], [{ account_id: 'synthetic-account-B' }, 'mismatch'],
    [{ user_id: 'synthetic-user-B' }, 'mismatch'],
  ]) {
    const { collector } = setup({ fetchImpl: async () => jsonResponse(payload(fields)) });
    const snapshot = await collector.readSnapshot();
    assertEmpty(snapshot, 'ok');
    assert.equal(snapshot.account.status, status);
    assert.equal(snapshot.account.plan, undefined);
  }
  const malformed = payload({ account_id: 'synthetic-account-B' });
  malformed.rate_limit.primary_window.used_percent = -1;
  const { collector } = setup({ fetchImpl: async () => jsonResponse(malformed) });
  assertEmpty(await collector.readSnapshot(), 'invalidResponse');
});

test('account-only identity can report windows but cannot claim validated ordinary usage permission', async () => {
  const { collector } = setup({ authSource: () => auth({ userId: undefined }) });
  const snapshot = await collector.readSnapshot();
  assert.equal(snapshot.account.status, 'matched');
  assert.equal(snapshot.windows.length, 2);
  assert.equal(snapshot.ordinaryUsageAllowed, null);
});

test('unconfigured, unsupported, missing/expired auth and thrown auth errors make no provider calls', async () => {
  for (const [value, status] of [
    [{ status: 'unconfigured' }, 'unconfigured'], [{ status: 'unsupported' }, 'unsupported'],
    [null, 'authRequired'], [auth({ accountId: '' }), 'authRequired'],
    [auth({ accessToken: 'bad\r\nheader' }), 'authRequired'],
    [auth({ expiresAtMs: NOW }), 'authRequired'],
  ]) {
    const h = setup({ authSource: () => value });
    assertEmpty(await h.collector.readSnapshot(), status);
    assert.equal(h.calls.length, 0);
  }
  const h = setup({ authSource: () => { throw new Error('synthetic-private-error'); } });
  assertEmpty(await h.collector.readSnapshot(), 'authRequired');
  assert.equal(h.calls.length, 0);
});

test('provider errors are typed snapshots, never raw errors or quota exhaustion', async () => {
  for (const [code, expected] of [[401, 'authRequired'], [403, 'unavailable'], [429, 'rateLimited'],
    [500, 'unavailable'], [503, 'unavailable'], [404, 'unsupported'], [405, 'unsupported'],
    [501, 'unsupported'], [302, 'unavailable'], [204, 'invalidResponse']]) {
    let count = 0;
    const { collector } = setup({ fetchImpl: async () => {
      count++;
      return new Response(code === 204 ? null : 'synthetic-private-provider-error', { status: code });
    } });
    const snapshot = await collector.readSnapshot();
    assertEmpty(snapshot, expected);
    assert.equal(JSON.stringify(snapshot).includes('synthetic'), false);
    assert.equal(count, 1); // In particular, no token refresh or 401 retry.
  }
  const { collector } = setup({ fetchImpl: async () => { throw new Error('synthetic-private-network-error'); } });
  assertEmpty(await collector.readSnapshot(), 'unavailable');
});

test('no redirect/query/host override is supported, even by injected response metadata', async () => {
  const h = setup({ baseUrl: 'https://synthetic.invalid', providerUrl: 'https://synthetic.invalid' });
  await h.collector.readSnapshot();
  assert.equal(h.calls[0][0], WHAM_URL);
  for (const metadata of [{ redirected: true }, { url: 'https://synthetic.invalid' },
    { url: `${WHAM_URL}?synthetic=1` }]) {
    const response = jsonResponse(payload());
    for (const [name, value] of Object.entries(metadata)) Object.defineProperty(response, name, { value });
    const { collector } = setup({ fetchImpl: async () => response });
    assertEmpty(await collector.readSnapshot(), 'invalidResponse');
  }
});

test('provider body is bounded by actual streamed bytes, not just content-length', async () => {
  const text = JSON.stringify(payload());
  const exact = text + ' '.repeat(MAX_BYTES - Buffer.byteLength(text));
  const good = setup({ fetchImpl: async () => new Response(exact,
    { headers: { 'Content-Type': 'application/json' } }) });
  assert.equal((await good.collector.readSnapshot()).status, 'ok');
  for (const headers of [{}, { 'Content-Length': '1' }]) {
    let cancelled = false;
    const body = new ReadableStream({ start(controller) {
      controller.enqueue(new Uint8Array(MAX_BYTES)); controller.enqueue(new Uint8Array(1));
    }, cancel() { cancelled = true; } });
    const { collector } = setup({ fetchImpl: async () => new Response(body,
      { headers: { 'Content-Type': 'application/json', ...headers } }) });
    assertEmpty(await collector.readSnapshot(), 'invalidResponse');
    assert.equal(cancelled, true);
  }
  for (const length of [String(MAX_BYTES + 1), '-1', 'not-a-number']) {
    let cancelled = false;
    const body = new ReadableStream({ cancel() { cancelled = true; } });
    const { collector } = setup({ fetchImpl: async () => new Response(body,
      { headers: { 'Content-Type': 'application/json', 'Content-Length': length } }) });
    assertEmpty(await collector.readSnapshot(), 'invalidResponse');
    assert.equal(cancelled, true);
  }
});

test('10-second maximum bounds both fetch and a stalled response body, including abort-ignorant fakes', async () => {
  assert.equal(PROVIDER_TIMEOUT_MS, 10000);
  assert.throws(() => setup({ timeoutMs: 10001 }), ConfigurationError);
  let signal;
  const h = setup({ timeoutMs: 20, fetchImpl: (_url, options) => {
    signal = options.signal; return new Promise(() => {});
  } });
  assertEmpty(await h.collector.readSnapshot(), 'unavailable');
  assert.equal(signal.aborted, true);
  let cancelled = false;
  const body = new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode('{')); },
    cancel() { cancelled = true; } });
  const stalled = setup({ timeoutMs: 20, fetchImpl: async () => new Response(body,
    { headers: { 'Content-Type': 'application/json' } }) });
  assertEmpty(await stalled.collector.readSnapshot(), 'unavailable');
  assert.equal(cancelled, true);
});

test('concurrent reads singleflight; successful cache lasts exactly 60s and is not caller-mutable', async () => {
  const gate = deferred();
  let calls = 0, authCalls = 0, time = NOW;
  const { collector } = setup({ clock: () => time, authSource: () => { authCalls++; return auth(); },
    fetchImpl: async () => { calls++; await gate.promise; return jsonResponse(payload()); } });
  const pending = Array.from({ length: 12 }, () => collector.readSnapshot());
  await tick();
  assert.equal(calls, 1);
  assert.ok(authCalls >= 12);
  gate.resolve();
  const results = await Promise.all(pending);
  results[0].windows[0].usedPercent = 99;
  assert.equal(results[1].windows[0].usedPercent, 25);
  time = NOW + 59999;
  assert.equal((await collector.readSnapshot()).windows[0].usedPercent, 25);
  assert.equal(calls, 1);
  time = NOW + 60000;
  await collector.readSnapshot();
  assert.equal(calls, 2);
});

test('auth is checked before cache, and account/user/token/expiry changes invalidate it', async () => {
  const h = setup();
  const first = await h.collector.readSnapshot();
  h.setAuth({ status: 'authRequired' });
  assertEmpty(await h.collector.readSnapshot(), 'authRequired');
  assert.equal(h.calls.length, 1);
  h.setAuth(auth());
  await h.collector.readSnapshot();
  assert.equal(h.calls.length, 2);
  h.setAuth(auth({ accessToken: 'synthetic-renewed-token' }));
  await h.collector.readSnapshot();
  assert.equal(h.calls.length, 3);
  h.setAuth(auth({ userId: 'synthetic-user-B' }));
  assert.equal((await h.collector.readSnapshot()).account.status, 'mismatch');
  h.setAuth(auth({ accountId: 'synthetic-account-B' }));
  const changed = await h.collector.readSnapshot();
  assertEmpty(changed, 'ok');
  assert.notEqual(changed.account.ref, first.account.ref);
  h.setAuth(auth({ expiresAtMs: NOW + 1 }));
  await h.collector.readSnapshot();
  h.setTime(NOW + 1);
  assertEmpty(await h.collector.readSnapshot(), 'authRequired');
});

test('late old-account completion cannot publish or repopulate cache after an account change', async () => {
  const old = deferred();
  let selected = auth(), calls = 0;
  const { collector } = setup({ authSource: () => selected, fetchImpl: async (_url, options) => {
    calls++;
    if (options.headers['ChatGPT-Account-Id'] === 'synthetic-account-A') {
      await old.promise; return jsonResponse(payload());
    }
    return jsonResponse(payload({ account_id: 'synthetic-account-B', user_id: 'synthetic-user-B' }));
  } });
  const first = collector.readSnapshot();
  await tick();
  selected = auth({ accountId: 'synthetic-account-B', userId: 'synthetic-user-B' });
  const second = await collector.readSnapshot();
  old.resolve();
  assertEmpty(await first, 'unavailable');
  assert.deepEqual(await collector.readSnapshot(), second);
  assert.equal(calls, 2);
});

test('credential change during the only provider read is detected before publication', async () => {
  const gate = deferred();
  let selected = auth();
  const { collector } = setup({ authSource: () => selected,
    fetchImpl: async () => { await gate.promise; return jsonResponse(payload()); } });
  const pending = collector.readSnapshot();
  await tick();
  selected = auth({ accountId: 'synthetic-account-B' });
  gate.resolve();
  assertEmpty(await pending, 'unavailable');
});

test('slow auth reads serialize, rather than completing out of order and restoring an old identity', async () => {
  const gate = deferred();
  let loads = 0;
  const { collector } = setup({ authSource: async () => {
    if (++loads === 1) { await gate.promise; return auth(); }
    return { status: 'authRequired' };
  } });
  const first = collector.readSnapshot();
  const second = collector.readSnapshot();
  await tick();
  assert.equal(loads, 1);
  gate.resolve();
  assertEmpty(await second, 'authRequired');
  assert.equal((await first).windows.length, 0);
  assertEmpty(await collector.readSnapshot(), 'authRequired');
});

test('failed refresh and backwards clock never serve expired measurements as fresh', async () => {
  let time = NOW, failed = false, calls = 0;
  const { collector } = setup({ clock: () => time, fetchImpl: async () => {
    calls++; return failed ? jsonResponse({}, 500) : jsonResponse(payload());
  } });
  await collector.readSnapshot();
  time = NOW - 1;
  await collector.readSnapshot();
  assert.equal(calls, 2);
  failed = true;
  time = NOW + CACHE_MS;
  assertEmpty(await collector.readSnapshot(), 'unavailable');
});

test('collector bearer is mandatory even on loopback; comparison is exact and errors are sanitized', async () => {
  const verifier = createReadTokenVerifier(READ_TOKEN);
  assert.equal(verifier(`Bearer ${READ_TOKEN}`), true);
  assert.equal(verifier(`bearer ${READ_TOKEN}`), true);
  for (const header of [undefined, '', 'Basic synthetic', `Bearer ${READ_TOKEN.slice(0, -1)}!`,
    `Bearer ${READ_TOKEN}x`, `Bearer ${READ_TOKEN.toLowerCase()}`, [`Bearer ${READ_TOKEN}`]]) {
    assert.equal(verifier(header), false);
  }
  for (const token of ['', 'x'.repeat(31), ' '.repeat(32), 'x'.repeat(4097), `${READ_TOKEN}\n`]) {
    assert.throws(() => createReadTokenVerifier(token), ConfigurationError);
  }
  let calls = 0;
  const handler = createRequestHandler({ readToken: READ_TOKEN, collector: {
    async readSnapshot() { calls++; throw new Error('synthetic-private-server-error'); },
  } });
  for (const header of [undefined, 'Basic synthetic', `Bearer ${'wrong'.repeat(12)}`]) {
    const result = await invoke(handler, { headers: { authorization: header } });
    assert.equal(result.status, 401);
    assert.deepEqual(result.body, { error: 'collectorAuth' });
  }
  const duplicates = await invoke(handler, { rawHeaders: ['Authorization', `Bearer ${READ_TOKEN}`,
    'authorization', `Bearer ${READ_TOKEN}`] });
  assert.equal(duplicates.status, 401);
  assert.equal(calls, 0);
  assert.deepEqual((await invoke(handler)).body, { error: 'unavailable' });
});

test('HTTP routing rejects unknown paths, queries, methods, non-loopback clients and request bodies', async () => {
  let calls = 0;
  const handler = createRequestHandler({ readToken: READ_TOKEN, collector: {
    async readSnapshot() { calls++; return { synthetic: true }; },
  } });
  for (const url of ['/', '/api/session/stats', `${QUOTA_PATH}?x=1`, `${QUOTA_PATH}/`,
    `${QUOTA_PATH}#fragment`, 'https://synthetic.invalid/ocmn/quota/v1', '/ocmn/%71uota/v1']) {
    assert.equal((await invoke(handler, { url })).status, 404);
  }
  for (const method of ['POST', 'PUT', 'DELETE', 'HEAD', 'OPTIONS']) {
    assert.equal((await invoke(handler, { method })).status, 405);
  }
  assert.equal((await invoke(handler, { socket: { remoteAddress: '192.0.2.10' } })).status, 403);
  for (const headers of [{ 'content-length': '1' }, { 'transfer-encoding': 'chunked' }]) {
    assert.equal((await invoke(handler, { headers: { authorization: `Bearer ${READ_TOKEN}`, ...headers } })).status, 403);
  }
  assert.equal(calls, 0);
  const success = await invoke(handler);
  assert.equal(success.status, 200);
  assert.equal(success.headers['Cache-Control'], 'no-store');
  assert.equal(calls, 1);
});

test('provider auth/rate-limit failures remain HTTP 200 snapshots, distinct from collector authentication', async () => {
  for (const code of [401, 429, 500]) {
    const { collector } = setup({ fetchImpl: async () => jsonResponse({}, code) });
    const handler = createRequestHandler({ readToken: READ_TOKEN, collector });
    const response = await invoke(handler);
    assert.equal(response.status, 200);
    assertEmpty(response.body, code === 401 ? 'authRequired' : code === 429 ? 'rateLimited' : 'unavailable');
  }
});

const jwt = (claims) => `synthetic.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.synthetic`;
const claims = { exp: NOW / 1000 + 3600, 'https://api.openai.com/auth': {
  chatgpt_account_id: 'synthetic-account-A', chatgpt_user_id: 'synthetic-user-A', chatgpt_plan_type: 'plus',
} };
const codexFile = () => ({ auth_mode: 'chatgpt', OPENAI_API_KEY: null, tokens: {
  account_id: 'synthetic-account-A', access_token: jwt(claims), id_token: jwt(claims),
  refresh_token: 'synthetic-refresh-token-NEVER-USED',
} });

test('explicit Codex and pinned OpenCode OAuth formats load only selected identity and access material', () => {
  const parsed = parseAuthDocument(codexFile(), { nowMs: NOW });
  assert.deepEqual(parsed, { status: 'ok', accessToken: jwt(claims), accountId: 'synthetic-account-A',
    userId: 'synthetic-user-A', expiresAtMs: NOW + 3600000 });
  const opencode = { openai: { type: 'oauth', access: jwt(claims), accountId: 'synthetic-account-A',
    expires: NOW + 900000, refresh: 'synthetic-refresh-token-NEVER-USED' } };
  assert.deepEqual(parseAuthDocument(opencode, { format: 'opencode', nowMs: NOW }),
    { ...parsed, expiresAtMs: NOW + 900000 });
  assert.equal(JSON.stringify(parsed).includes('refresh'), false);
  const withoutJwt = codexFile();
  withoutJwt.tokens.access_token = 'synthetic-opaque-oauth-token';
  delete withoutJwt.tokens.id_token;
  assert.equal(parseAuthDocument(withoutJwt, { nowMs: NOW }).status, 'ok');
  const legacyClaim = clone(claims);
  legacyClaim['https://api.openai.com/auth'].user_id = 'synthetic-user-A';
  delete legacyClaim['https://api.openai.com/auth'].chatgpt_user_id;
  withoutJwt.tokens.id_token = jwt(legacyClaim);
  assert.equal(parseAuthDocument(withoutJwt, { nowMs: NOW }).userId, 'synthetic-user-A');
});

test('API-key/PAT/unknown auth mode, missing account, expired OAuth and alternate edge fail closed', () => {
  for (const document of [null, {}, { tokens: {} }, { tokens: { access_token: 'synthetic-access' } }]) {
    assert.equal(parseAuthDocument(document, { nowMs: NOW }).status, 'authRequired');
  }
  for (const change of [{ auth_mode: 'apikey' }, { OPENAI_API_KEY: 'synthetic-api-key' },
    { auth_mode: 'personalAccessToken' }, { auth_mode: 'future-auth-mode' }]) {
    assert.equal(parseAuthDocument({ ...codexFile(), ...change }, { nowMs: NOW }).status, 'unsupported');
  }
  assert.equal(parseAuthDocument({ openai: { type: 'api', key: 'synthetic' } },
    { format: 'opencode', nowMs: NOW }).status, 'unsupported');
  assert.equal(parseAuthDocument({ openai: { type: 'oauth', access: 'synthetic', accountId: 'synthetic-A' } },
    { format: 'opencode', nowMs: NOW }).status, 'authRequired');
  for (const exp of [NOW / 1000, 'not-a-timestamp']) {
    const file = codexFile(); file.tokens.access_token = jwt({ ...claims, exp });
    assert.equal(parseAuthDocument(file, { nowMs: NOW }).status, 'authRequired');
  }
  const file = codexFile();
  file.tokens.id_token = jwt({ 'https://api.openai.com/auth': { chatgpt_account_is_fedramp: true } });
  assert.equal(parseAuthDocument(file, { nowMs: NOW }).status, 'unsupported');
});

test('unconfigured imports/source never default-read an auth store or start a server', async () => {
  const imported = await import('./collector.mjs?synthetic-import-check');
  const source = imported.createFileAuthSource({ readFile: () => { assert.fail('unexpected file read'); } });
  assert.deepEqual(await source(), { status: 'unconfigured' });
  const collector = imported.createCollector({ readToken: READ_TOKEN,
    fetchImpl: () => { assert.fail('unexpected provider call'); }, clock: () => NOW });
  assertEmpty(await collector.readSnapshot(), 'unconfigured');
});

test('file adapters use only explicit, size-capped, read-only synthetic files; never refresh/write credentials', async (t) => {
  const directory = await mkdtemp(join(process.env.OCMN_QUOTA_TEST_TMPDIR ?? tmpdir(), 'ocmn-quota-test-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const filePath = join(directory, 'synthetic-auth.json');
  const contents = JSON.stringify(codexFile());
  await writeFile(filePath, contents, { mode: 0o600 });
  const before = await stat(filePath);
  const source = createFileAuthSource({ filePath, clock: () => NOW });
  const h = setup({ authSource: source, fetchImpl: async () => jsonResponse({}, 401) });
  assertEmpty(await h.collector.readSnapshot(), 'authRequired');
  const expired = createFileAuthSource({ filePath, clock: () => NOW + 3600001 });
  assert.deepEqual(await expired(), { status: 'authRequired' });
  assert.equal(await readFile(filePath, 'utf8'), contents);
  assert.equal((await stat(filePath)).mtimeMs, before.mtimeMs);
  assert.deepEqual(await createFileAuthSource({ filePath: join(directory, 'absent.json') })(), { status: 'authRequired' });
  await assert.rejects(readBoundedFile(join(directory, 'absent.json')), { message: 'QUOTA_SOURCE_FILE_INVALID' });
  await writeFile(filePath, 'synthetic-invalid-json');
  assert.deepEqual(await source(), { status: 'authRequired' });
  await writeFile(filePath, Buffer.alloc(MAX_BYTES + 1));
  await assert.rejects(readBoundedFile(filePath));
  assert.deepEqual(await source(), { status: 'authRequired' });
  await assert.rejects(readBoundedFile(directory));
  const link = join(directory, 'synthetic-link.json');
  await symlink(filePath, link);
  await assert.rejects(readBoundedFile(link));
  const tokenPath = join(directory, 'synthetic-read-token');
  await writeFile(tokenPath, `${READ_TOKEN}\n`, { mode: 0o600 });
  assert.equal(await loadReadToken(tokenPath), READ_TOKEN);
  await writeFile(tokenPath, Buffer.alloc(MAX_BYTES + 1));
  await assert.rejects(loadReadToken(tokenPath), { message: 'QUOTA_READ_TOKEN_FILE_INVALID' });
});

test('CLI config requires a dedicated token file, binds loopback only, validates format and port, redacts errors', async () => {
  const env = { OCMN_QUOTA_READ_TOKEN_FILE: '/tmp/opencode/synthetic-read-token',
    OCMN_QUOTA_AUTH_FILE: '/tmp/opencode/synthetic-auth.json', OCMN_QUOTA_AUTH_FORMAT: 'codex' };
  let reads = 0;
  const fakeFiles = { readFile: async (path) => {
    reads++; assert.equal(path, env.OCMN_QUOTA_READ_TOKEN_FILE); return Buffer.from(READ_TOKEN);
  } };
  const result = await loadConfiguration({ ...env, OCMN_QUOTA_HOST: '0.0.0.0',
    OCMN_QUOTA_PROVIDER_URL: 'https://synthetic.invalid' }, fakeFiles);
  assert.equal(result.host, '127.0.0.1');
  assert.equal(result.port, 4195);
  assert.equal(result.ignoredClaudeConfiguration, false);
  assert.equal(reads, 1);
  for (const port of ['1024', '65535']) {
    assert.equal((await loadConfiguration({ ...env, OCMN_QUOTA_PORT: port }, fakeFiles)).port, Number(port));
  }
  for (const port of ['1023', '65536', '0', '-1', '', '4195.0', ' 4195', '4195x']) {
    await assert.rejects(loadConfiguration({ ...env, OCMN_QUOTA_PORT: port }, fakeFiles),
      { message: 'QUOTA_PORT_INVALID' });
  }
  await assert.rejects(loadConfiguration({}, fakeFiles), { message: 'QUOTA_READ_TOKEN_FILE_REQUIRED' });
  await assert.rejects(loadConfiguration({ ...env, OCMN_QUOTA_AUTH_FORMAT: 'guessed' }, fakeFiles),
    { message: 'QUOTA_AUTH_FORMAT_INVALID' });
  await assert.rejects(loadConfiguration({ ...env, OCMN_QUOTA_AUTH_FILE: 'relative.json' }, fakeFiles),
    { message: 'QUOTA_AUTH_FILE_INVALID' });
  await assert.rejects(loadConfiguration(env, { readFile: () => { throw new Error('synthetic-secret-path-value'); } }),
    { message: 'QUOTA_READ_TOKEN_FILE_INVALID' });
  await assert.rejects(loadReadToken(env.OCMN_QUOTA_READ_TOKEN_FILE,
    { readFile: () => Buffer.from('short-synthetic-token') }), { message: 'QUOTA_READ_TOKEN_FILE_INVALID' });
});

const claudePayload = (used = 12.5) => ({
  five_hour: { utilization: used, resets_at: new Date(NOW + 300_000).toISOString() },
  seven_day: { utilization: 100, resets_at: null },
});
const claudeMapping = (response) => mapClaudeUsage(response, 'a'.repeat(64), NOW);

test('Claude live collection is disabled before auth-source or network access', async () => {
  const collector = createCollector({ provider: 'claude', readToken: READ_TOKEN, clock: () => NOW,
    authSource: () => assert.fail('Claude credential access is not permitted'),
    fetchImpl: () => assert.fail('Claude network collection is not permitted') });
  const snapshot = await collector.readSnapshot();
  assert.equal(snapshot.status, 'unsupported');
  assert.deepEqual(snapshot.windows, []);
  assert.equal(snapshot.account.ref, undefined);
  assert.equal(snapshot.ordinaryUsageAllowed, null);
});

test('Claude research-only payload mapping retains the source-bound distinction', () => {
  const snapshot = claudeMapping(claudePayload());
  assert.equal(snapshot.provider, 'claude');
  assert.equal(snapshot.source, 'claude.oauth');
  assert.equal(snapshot.account.status, 'sourceBound');
  assert.match(snapshot.account.ref, /^[a-f0-9]{64}$/);
  assert.equal(snapshot.ordinaryUsageAllowed, null);
  assert.equal(snapshot.account.plan, undefined);
  assert.deepEqual(snapshot.windows, [
    { id: 'primary', status: 'reported', usedPercent: 12.5, durationSeconds: 18_000, resetsAtMs: NOW + 300_000 },
    { id: 'secondary', status: 'reported', usedPercent: 100, durationSeconds: 604_800 },
  ]);
  assert.equal(JSON.stringify(snapshot).includes('synthetic-claude-token'), false);
});

test('Claude research mapper preserves zero/full/fraction and missing windows without extrapolating resets', () => {
  for (const used of [0, 100, 12.5]) {
    assert.equal(claudeMapping(claudePayload(used)).windows[0].usedPercent, used);
  }
  for (const response of [{ five_hour: null }, { five_hour: null, seven_day: null }, { limits: [] }]) {
    const snapshot = claudeMapping(response);
    assert.equal(snapshot.status, 'ok');
    assert.deepEqual(snapshot.windows, [{ id: 'primary', status: 'missing' }, { id: 'secondary', status: 'missing' }]);
  }
});

test('Claude research mapper uses authoritative structured limits without merging legacy or inventing duration', () => {
  const snapshot = claudeMapping({ five_hour: { utilization: 99 }, limits: [
    { kind: 'session', percent: 25, resets_at: '2026-09-06T14:00:00+02:00' },
    { kind: 'weekly_all', percent: 50 },
    { kind: 'weekly_scoped', percent: 98, display_name: 'PRIVATE_COPY_NOT_FOR_OUTPUT' },
  ] });
  assert.deepEqual(snapshot.windows, [
    { id: 'primary', status: 'reported', usedPercent: 25, resetsAtMs: Date.parse('2026-09-06T12:00:00Z') },
    { id: 'secondary', status: 'reported', usedPercent: 50 },
  ]);
  assert.equal(JSON.stringify(snapshot).includes('PRIVATE_COPY'), false);
});

test('Claude research mapper rejects malformed schemas, percentages and reset dates', () => {
  const cases = [null, {}, [], { limits: {} }, { limits: [null] },
    { limits: [{ kind: 'session', percent: 1 }, { kind: 'session', percent: 2 }] }];
  for (const used of [-1, 101, '5', true, null]) cases.push({ five_hour: { utilization: used } });
  for (const resets_at of ['', 'tomorrow', '2026-02-31T00:00:00Z', '2026-09-06T24:01:00Z', 1]) {
    cases.push({ five_hour: { utilization: 10, resets_at } });
  }
  for (const response of cases) {
    assert.throws(() => claudeMapping(response), { message: 'invalidResponse' });
  }
});

test('historical Claude auth-schema research does not invent account ID or refresh ownership', () => {
  const cli = { claudeAiOauth: { accessToken: 'synthetic-claude-access', expiresAt: NOW + 1000,
    refreshToken: 'synthetic-refresh-NEVER-USED', subscriptionType: 'max' } };
  const expected = { status: 'ok', accessToken: 'synthetic-claude-access', expiresAtMs: NOW + 1000 };
  assert.deepEqual(parseClaudeAuthDocument(cli, { nowMs: NOW }), expected);
  assert.equal(parseClaudeAuthDocument(cli, { nowMs: NOW + 1000 }).status, 'authRequired');
  assert.deepEqual(parseClaudeAuthDocument({ anthropic: { type: 'oauth', access: 'synthetic-claude-access',
    expires: NOW + 1000, refresh: 'synthetic-refresh-NEVER-USED' } }, { format: 'opencode', nowMs: NOW }), expected);
  assert.equal(parseClaudeAuthDocument({ anthropic: { type: 'api', key: 'synthetic' } }, { format: 'opencode' }).status, 'unsupported');
  assert.equal(JSON.stringify(expected).includes('refresh'), false);
});

test('legacy Claude route returns unsupported without credential reads', async () => {
  let codexReads = 0;
  const claudeCollector = createCollector({ provider: 'claude', readToken: READ_TOKEN, clock: () => NOW,
    authSource: () => assert.fail('unexpected credential read'), fetchImpl: () => assert.fail('unexpected fetch') });
  const handler = createRequestHandler({ readToken: READ_TOKEN,
    collector: { readSnapshot: async () => { codexReads++; return { provider: 'codex' }; } },
    claudeCollector });
  const response = await invoke(handler, { url: CLAUDE_QUOTA_PATH });
  assert.equal(response.body.provider, 'claude');
  assert.equal(response.body.status, 'unsupported');
  assert.deepEqual(response.body.windows, []);
  assert.equal(codexReads, 0);
  for (const url of [`${CLAUDE_QUOTA_PATH}?provider=codex`, `${CLAUDE_QUOTA_PATH}/`, `${QUOTA_PATH}/unknown`]) {
    assert.equal((await invoke(handler, { url })).status, 404);
  }
  assert.equal((await invoke(handler, { url: CLAUDE_QUOTA_PATH, headers: {} })).status, 401);
});

test('retired Claude options never read that source or prevent Codex collection', async () => {
  const env = { OCMN_QUOTA_READ_TOKEN_FILE: '/tmp/opencode/synthetic-read-token',
    OCMN_QUOTA_AUTH_FILE: '/tmp/opencode/synthetic-codex-auth.json' };
  for (const retired of [
    { OCMN_CLAUDE_AUTH_FILE: '/tmp/opencode/synthetic-claude-auth.json' },
    { OCMN_CLAUDE_AUTH_FORMAT: 'opencode' },
    { OCMN_CLAUDE_AUTH_FILE: 'invalid-ignored-path', OCMN_CLAUDE_AUTH_FORMAT: 'invalid-ignored-format' },
  ]) {
    const reads = [], calls = [];
    const readFile = async (path) => {
      reads.push(path);
      if (path === env.OCMN_QUOTA_READ_TOKEN_FILE) return Buffer.from(READ_TOKEN);
      if (path === env.OCMN_QUOTA_AUTH_FILE) return Buffer.from(JSON.stringify(codexFile()));
      assert.fail('unexpected credential source');
    };
    const config = await loadConfiguration({ ...env, ...retired }, { readFile });
    assert.equal(config.ignoredClaudeConfiguration, true);
    assert.equal(Object.hasOwn(config, 'claudeFilePath'), false);
    assert.equal(Object.hasOwn(config, 'claudeFormat'), false);
    const collector = createCollector({ readToken: config.readToken, clock: () => NOW,
      authSource: createFileAuthSource({ ...config, readFile, clock: () => NOW }),
      fetchImpl: async (url) => { calls.push(url); return jsonResponse(payload()); } });
    const claudeCollector = createCollector({ readToken: config.readToken, provider: 'claude', clock: () => NOW,
      authSource: () => assert.fail('unexpected Claude auth access'),
      fetchImpl: () => assert.fail('unexpected Claude request') });
    const handler = createRequestHandler({ ...config, collector, claudeCollector });
    assertEmpty((await invoke(handler, { url: CLAUDE_QUOTA_PATH })).body, 'unsupported');
    assert.deepEqual(reads, [env.OCMN_QUOTA_READ_TOKEN_FILE]);
    assert.deepEqual(calls, []);
    const snapshot = (await invoke(handler)).body;
    assert.equal(snapshot.status, 'ok');
    assert.equal(snapshot.provider, 'codex');
    assert.equal(snapshot.windows[0].usedPercent, 25);
    assert.deepEqual(calls, [WHAM_URL]);
    assert.deepEqual(reads, [env.OCMN_QUOTA_READ_TOKEN_FILE, env.OCMN_QUOTA_AUTH_FILE, env.OCMN_QUOTA_AUTH_FILE]);
  }
});
