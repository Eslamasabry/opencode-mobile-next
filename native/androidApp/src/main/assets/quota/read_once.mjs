// OpenCode Mobile one-shot usage reader. Read-only. Runs once, prints ONE line
//   OCMN_USAGE_V1 <base64 of JSON>
// and exits. It opens no port, installs nothing, writes no file, and never
// prints a token, key, email, path or provider error text. The app passes this
// whole file as ONE `node --input-type=module -e <source>` argument, so it must
// stay self-contained and small (Windows caps a command line near 32 KB).
//
// The mappers below are copies of tool/quota/collector.mjs (mapWham,
// mapGlmUsage, mapMiniMaxUsage). read_once.test.mjs compares them with the
// collector's own on the same payloads, so a drift fails the test.
import { createHmac } from 'node:crypto';
import { readFileSync, statSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

const MAX_BYTES = 64 * 1024;
const TIMEOUT_MS = 10_000;
const CACHE_MS = 60_000;
const MAX_TS = 8_640_000_000_000_000;
const PLANS = new Set(['free', 'go', 'plus', 'pro', 'team', 'business', 'enterprise', 'edu']);
const URLS = {
  codex: 'https://chatgpt.com/backend-api/wham/usage',
  glm: 'https://api.z.ai/api/monitor/usage/quota/limit',
  minimax: 'https://www.minimax.io/v1/token_plan/remains',
};
const SOURCES = { codex: 'codex.wham', glm: 'glm.codingPlan', minimax: 'minimax.tokenPlan' };
const REF_KEY = 'ocmn-usage-ref-v1';
const MARKER = 'OCMN_USAGE_V1';

class Fail extends Error { constructor(status) { super(status); this.status = status; } }
const obj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const ident = (v) => typeof v === 'string' && /^[\x21-\x7e]{1,1024}$/.test(v);
const token = (v) => typeof v === 'string' && /^[A-Za-z0-9._~+/-]+=*$/.test(v) && v.length <= MAX_BYTES;
const ts = (v) => Number.isSafeInteger(v) && v >= 0 && v <= MAX_TS;
const invalid = () => { throw new Fail('invalidResponse'); };

function empty(provider, status, now, account = { status: 'unverified' }) {
  return { schemaVersion: 1, provider, source: SOURCES[provider], status, freshness: 'none',
    fetchedAtMs: now, expiresAtMs: now, account, ordinaryUsageAllowed: null, windows: [] };
}

function mapWindow(value, id) {
  if (value == null) return { id, status: 'missing' };
  if (!obj(value) || typeof value.used_percent !== 'number' || !Number.isFinite(value.used_percent)
    || value.used_percent < 0 || value.used_percent > 100) invalid();
  const w = { id, status: 'reported', usedPercent: value.used_percent };
  if (value.limit_window_seconds != null) {
    if (!Number.isSafeInteger(value.limit_window_seconds) || value.limit_window_seconds <= 0
      || value.limit_window_seconds > 315_360_000) invalid();
    w.durationSeconds = value.limit_window_seconds;
  }
  if (value.reset_at != null) {
    if (!Number.isSafeInteger(value.reset_at) || !ts(value.reset_at * 1000)) invalid();
    w.resetsAtMs = value.reset_at * 1000;
  }
  if (value.reset_after_seconds != null && (!Number.isSafeInteger(value.reset_after_seconds)
    || value.reset_after_seconds < 0 || value.reset_after_seconds > MAX_TS / 1000)) invalid();
  return w;
}

export function mapWham(payload, auth, ref, now) {
  if (!obj(payload) || typeof payload.plan_type !== 'string' || !payload.plan_type) invalid();
  for (const f of ['account_id', 'user_id']) if (payload[f] != null && !ident(payload[f])) invalid();
  const rate = payload.rate_limit;
  if (rate != null && (!obj(rate) || typeof rate.allowed !== 'boolean'
    || typeof rate.limit_reached !== 'boolean')) invalid();
  const windows = [mapWindow(rate?.primary_window, 'primary'), mapWindow(rate?.secondary_window, 'secondary')];
  const account = { ref, status: 'unverified' };
  if ((payload.account_id != null && payload.account_id !== auth.accountId)
    || (auth.userId != null && payload.user_id != null && payload.user_id !== auth.userId)) {
    account.status = 'mismatch';
  } else if (payload.account_id === auth.accountId
    && (auth.userId == null || payload.user_id === auth.userId)) account.status = 'matched';
  if (account.status !== 'matched') return empty('codex', 'ok', now, account);
  if (PLANS.has(payload.plan_type)) account.plan = payload.plan_type;
  return { ...empty('codex', 'ok', now, account), freshness: 'fresh', expiresAtMs: now + CACHE_MS,
    ordinaryUsageAllowed: auth.userId != null && payload.user_id === auth.userId
      ? rate?.allowed ?? null : null, windows };
}

const GLM_SECONDS = { 3: 3600, 6: 604_800 };
const GLM_LABELS = { 3: 'h', 5: 'mo', 6: 'w' };

function glmSpan(limit) {
  if (limit.unit == null && limit.number == null) return null;
  if (!Number.isSafeInteger(limit.unit) || !Number.isSafeInteger(limit.number)
    || limit.unit < 0 || limit.unit > 99 || limit.number <= 0 || limit.number > 9999) invalid();
  const unit = GLM_SECONDS[limit.unit];
  return { label: `${limit.number}${GLM_LABELS[limit.unit] ?? `u${limit.unit}`}`,
    seconds: unit == null ? null : unit * limit.number };
}

export function mapGlmUsage(payload, ref, now) {
  if (!obj(payload)) invalid();
  if (payload.success === false) return empty('glm', 'unavailable', now);
  if (payload.success != null && payload.success !== true) invalid();
  if (payload.code != null && payload.code !== 200 && payload.code !== 0) return empty('glm', 'unavailable', now);
  const data = payload.data ?? payload;
  if (!obj(data) || !Array.isArray(data.limits) || data.limits.length > 64) invalid();
  const windows = [], ids = new Set();
  for (const limit of data.limits) {
    if (!obj(limit)) invalid();
    if (limit.type !== 'TOKENS_LIMIT' && limit.type !== 'TIME_LIMIT') return empty('glm', 'unsupported', now);
    const span = glmSpan(limit);
    const id = limit.type === 'TIME_LIMIT' ? 'mcp' : span == null ? 'tokens' : `tokens.${span.label}`;
    if (ids.has(id)) invalid();
    ids.add(id);
    let w;
    if (limit.percentage == null) w = { id, status: 'missing' };
    else {
      if (typeof limit.percentage !== 'number' || !Number.isFinite(limit.percentage)
        || limit.percentage < 0 || limit.percentage > 100) invalid();
      w = { id, status: 'reported', usedPercent: limit.percentage };
    }
    if (limit.type === 'TOKENS_LIMIT' && span?.seconds != null) w.durationSeconds = span.seconds;
    if (limit.nextResetTime != null) {
      if (!Number.isSafeInteger(limit.nextResetTime) || !ts(limit.nextResetTime)) invalid();
      w.resetsAtMs = limit.nextResetTime;
    }
    windows.push(w);
  }
  return { ...empty('glm', 'ok', now, { ref, status: 'sourceBound' }), freshness: 'fresh',
    expiresAtMs: now + CACHE_MS, windows };
}

function miniMaxWindow(value, id) {
  const weekly = id === 'secondary';
  const prefix = weekly ? 'current_weekly' : 'current_interval';
  const status = value[`${prefix}_status`];
  if (status != null && ![1, 2, 3].includes(status)) invalid();
  const remaining = value[`${prefix}_remaining_percent`];
  if (remaining != null && (typeof remaining !== 'number' || !Number.isFinite(remaining)
    || remaining < 0 || remaining > 100)) invalid();
  const boost = value.weekly_boost_permille;
  if (weekly && boost != null && (!Number.isSafeInteger(boost) || boost < 0)) invalid();
  if (status === 3 || remaining == null || (weekly && boost != null && boost !== 1000)) return { id, status: 'missing' };
  if (status === 2 && remaining !== 0) invalid();
  const start = value[weekly ? 'weekly_start_time' : 'start_time'];
  const end = value[weekly ? 'weekly_end_time' : 'end_time'];
  if (!ts(start) || !ts(end) || end <= start || (end - start) % 1000 !== 0 || end - start > 315_360_000_000) invalid();
  return { id, status: 'reported', usedPercent: 100 - remaining, durationSeconds: (end - start) / 1000, resetsAtMs: end };
}

export function mapMiniMaxUsage(payload, ref, now) {
  if (!obj(payload) || !Array.isArray(payload.model_remains) || payload.model_remains.length > 64
    || payload.model_remains.some((row) => !obj(row))) invalid();
  if (payload.base_resp != null) {
    if (!obj(payload.base_resp) || !Number.isInteger(payload.base_resp.status_code)) invalid();
    if (payload.base_resp.status_code !== 0) throw new Fail('unavailable');
  }
  const general = payload.model_remains.filter((row) => row.model_name === 'general');
  if (general.length > 1) invalid();
  const account = { ref, status: 'sourceBound' };
  if (general.length === 0) return empty('minimax', 'unsupported', now, account);
  return { ...empty('minimax', 'ok', now, account), freshness: 'fresh', expiresAtMs: now + CACHE_MS,
    windows: [miniMaxWindow(general[0], 'primary'), miniMaxWindow(general[0], 'secondary')] };
}

// ---- sign-in files (read only, bounded, never refreshed) ------------------

function readJson(path, fs) {
  try {
    if (fs.size(path) > MAX_BYTES) return null;
    const value = JSON.parse(fs.read(path));
    return obj(value) ? value : null;
  } catch { return null; }
}

// Where OpenCode (1 and 2, both use xdg-basedir) keeps its sign-ins.
export function authPaths(env, home, platform) {
  const out = [];
  if (env.XDG_DATA_HOME) out.push(join(env.XDG_DATA_HOME, 'opencode', 'auth.json'));
  if (home) {
    out.push(join(home, '.local', 'share', 'opencode', 'auth.json'));
    if (platform === 'darwin') out.push(join(home, 'Library', 'Application Support', 'opencode', 'auth.json'));
  }
  if (platform === 'win32') {
    if (env.LOCALAPPDATA) out.push(join(env.LOCALAPPDATA, 'opencode', 'auth.json'));
    if (env.APPDATA) out.push(join(env.APPDATA, 'opencode', 'auth.json'));
  }
  return [...new Set(out)];
}

export function codexPaths(env, home) {
  const dir = env.CODEX_HOME || (home ? join(home, '.codex') : null);
  return dir ? [join(dir, 'auth.json')] : [];
}

function jwtClaims(value) {
  if (typeof value !== 'string' || value.length > MAX_BYTES) return {};
  const parts = value.split('.');
  if (parts.length !== 3 || parts.some((p) => !/^[A-Za-z0-9_-]+$/.test(p))) return {};
  try {
    const c = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    return obj(c) ? c : {};
  } catch { return {}; }
}

const claimsOf = (c) => (obj(c['https://api.openai.com/auth']) ? c['https://api.openai.com/auth'] : {});

// One Codex sign-in -> { type, auth } where auth is what mapWham needs, or a
// status when it cannot be used. `type` is 'oauth' or 'api'.
export function codexFromEntry(entry, format, now) {
  if (format === 'opencode') {
    if (!obj(entry)) return null;
    if (entry.type !== 'oauth') return { type: 'api', status: 'unsupported' };
  } else {
    if (!obj(entry)) return null;
    if (entry.OPENAI_API_KEY != null || (entry.auth_mode != null && entry.auth_mode !== 'chatgpt')) {
      return { type: 'api', status: 'unsupported' };
    }
    if (!obj(entry.tokens)) return null;
  }
  const t = format === 'opencode' ? entry : entry.tokens;
  const access = format === 'opencode' ? t.access : t.access_token;
  const accountId = format === 'opencode' ? t.accountId : t.account_id;
  let expires = format === 'opencode' ? t.expires : undefined;
  if (!token(access) || !ident(accountId) || (format === 'opencode' && !ts(expires))) {
    return { type: 'oauth', status: 'authRequired' };
  }
  const claims = jwtClaims(access);
  if (claims.exp != null) {
    const e = claims.exp * 1000;
    if (!Number.isSafeInteger(claims.exp) || !ts(e)) return { type: 'oauth', status: 'authRequired' };
    expires = expires == null ? e : Math.min(e, expires);
  }
  if (expires != null && expires <= now) return { type: 'oauth', status: 'authRequired' };
  const id = claimsOf(jwtClaims(format === 'opencode' ? undefined : t.id_token));
  const acc = claimsOf(claims);
  if (id.chatgpt_account_is_fedramp === true || acc.chatgpt_account_is_fedramp === true) {
    return { type: 'oauth', status: 'unsupported' };
  }
  const userId = id.chatgpt_user_id ?? id.user_id ?? acc.chatgpt_user_id ?? acc.user_id;
  if (userId != null && !ident(userId)) return { type: 'oauth', status: 'authRequired' };
  return { type: 'oauth', auth: { accessToken: access, accountId, userId: userId ?? null } };
}

// Finds each provider's sign-in. Returns { codex, glm, minimax }, each
// { type: 'oauth'|'api'|null, ... }. Never returns a value to print.
export function findSignIns(env, home, platform, fs, now) {
  const docs = authPaths(env, home, platform).map((p) => readJson(p, fs)).filter(Boolean);
  const out = { codex: { type: null }, glm: { type: null }, minimax: { type: null } };
  const key = (ids) => {
    for (const doc of docs) {
      for (const id of ids) {
        const e = doc[id];
        if (obj(e) && typeof e.key === 'string' && e.key.trim()) return e.key.trim();
      }
    }
    return null;
  };
  // Every Codex sign-in on this computer, OpenCode's first, then the Codex
  // CLI's. The first one that can be used wins: OpenCode renews its own
  // sign-in only when Codex is next used there, so an expired OpenCode entry
  // must not hide a live Codex CLI one. This never renews a sign-in itself.
  const candidates = [];
  for (const doc of docs) {
    for (const id of ['openai', 'codex']) {
      const found = codexFromEntry(doc[id], 'opencode', now);
      if (found) candidates.push(found);
    }
  }
  for (const path of codexPaths(env, home)) {
    const found = codexFromEntry(readJson(path, fs), 'codex', now);
    if (found) candidates.push(found);
  }
  out.codex = candidates.find((c) => c.type === 'oauth' && c.auth)
    ?? candidates.find((c) => c.type === 'oauth')
    ?? candidates[0]
    ?? { type: null };
  const glm = key(['zai-coding-plan']);
  if (glm) out.glm = { type: 'api', key: glm };
  const minimax = key(['minimax-coding-plan', 'minimax']);
  if (minimax) out.minimax = { type: 'api', key: minimax };
  return out;
}

// ---- provider calls ------------------------------------------------------

async function readBody(response, signal) {
  const declared = response.headers.get('content-length');
  if (declared != null && (!/^\d+$/.test(declared) || Number(declared) > MAX_BYTES)) invalid();
  if (!/^application\/json(?:\s*;|$)/i.test(response.headers.get('content-type') ?? '')) invalid();
  if (!response.body) invalid();
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0, done = false;
  try {
    while (!done) {
      if (signal.aborted) throw new Fail('unavailable');
      const part = await reader.read();
      done = part.done;
      if (!done) {
        size += part.value.byteLength;
        if (size > MAX_BYTES) invalid();
        chunks.push(part.value);
      }
    }
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, size)));
  } catch (e) {
    throw e instanceof Fail ? e : new Fail('invalidResponse');
  } finally {
    if (!done) void reader.cancel().catch(() => {});
  }
}

async function readProvider(provider, signIn, { fetchImpl, now, timeoutMs }) {
  const t0 = now();
  // An API key is pay-as-you-go: reported as its type, never read for limits.
  if (signIn.type == null || (provider === 'codex' && signIn.type === 'api')) return null;
  if (signIn.status) return empty(provider, signIn.status, t0);
  if (provider === 'minimax' && signIn.key.startsWith('sk-api-')) return empty(provider, 'unsupported', t0);
  if (provider === 'glm' && (!token(signIn.key) || signIn.key.startsWith('Bearer '))) return empty(provider, 'authRequired', t0);
  if (provider === 'minimax' && !token(signIn.key)) return empty(provider, 'authRequired', t0);
  const accountId = provider === 'codex' ? signIn.auth.accountId : `${provider}:${signIn.key}`;
  const ref = createHmac('sha256', REF_KEY).update(accountId).digest('hex');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const url = URLS[provider];
    const secret = provider === 'codex' ? signIn.auth.accessToken : signIn.key;
    const headers = { Authorization: provider === 'glm' ? secret : `Bearer ${secret}`,
      ...(provider === 'codex' ? { 'ChatGPT-Account-Id': signIn.auth.accountId } : {}),
      Accept: 'application/json', 'User-Agent': 'ocmn-quota/1' };
    const response = await fetchImpl(url, { method: 'GET', redirect: 'error', credentials: 'omit',
      signal: controller.signal, headers });
    try {
      if (response.redirected || (response.url && response.url !== url)) invalid();
      if (response.status !== 200) {
        throw new Fail(response.status === 401 ? 'authRequired' : response.status === 429 ? 'rateLimited'
          : [404, 405, 501].includes(response.status) ? 'unsupported'
          : response.status === 204 ? 'invalidResponse' : 'unavailable');
      }
      const payload = await readBody(response, controller.signal);
      const at = now();
      return provider === 'minimax' ? mapMiniMaxUsage(payload, ref, at)
        : provider === 'glm' ? mapGlmUsage(payload, ref, at)
        : mapWham(payload, signIn.auth, ref, at);
    } finally {
      void response.body?.cancel().catch(() => {});
    }
  } catch (e) {
    return empty(provider, e instanceof Fail ? e.status : 'unavailable', now(), { ref, status: 'unverified' });
  } finally {
    clearTimeout(timer);
    controller.abort();
  }
}

// The whole read. Returns the JSON the line carries; never throws.
export async function readUsage({
  env = process.env, home = null, platform = process.platform, nodeVersion = process.versions.node,
  fetchImpl = globalThis.fetch, clock = Date.now, timeoutMs = TIMEOUT_MS,
  fs = { read: (p) => readFileSync(p, 'utf8'), size: (p) => statSync(p).size },
} = {}) {
  const none = { codex: null, glm: null, minimax: null };
  if (Number(String(nodeVersion).split('.')[0]) < 20 || typeof fetchImpl !== 'function') {
    return { v: 1, error: 'nodeTooOld', providers: {}, auth: none };
  }
  try {
    const found = findSignIns(env, home ?? safeHome(), platform, fs, clock());
    const names = ['codex', 'glm', 'minimax'];
    const snaps = await Promise.all(names.map((n) => readProvider(n, found[n], { fetchImpl, now: clock, timeoutMs })));
    const providers = {}, auth = { ...none };
    names.forEach((n, i) => {
      auth[n] = found[n].type;
      if (snaps[i]) providers[n] = snaps[i];
    });
    return { v: 1, providers, auth };
  } catch {
    return { v: 1, error: 'failed', providers: {}, auth: none };
  }
}

function safeHome() { try { return homedir(); } catch { return null; } }

export function encodeLine(result) {
  return `${MARKER} ${Buffer.from(JSON.stringify(result), 'utf8').toString('base64')}`;
}

async function main() {
  let result;
  try { result = await readUsage(); } catch { result = { v: 1, error: 'failed', providers: {}, auth: {} }; }
  process.stdout.write(`${encodeLine(result)}\n`);
  // Keep the terminal alive a moment so the app can attach to a fast answer;
  // the app removes it as soon as it has the line.
  await new Promise((done) => setTimeout(done, 1500));
  process.exit(0);
}

if (!globalThis.__OCMN_READER_NO_RUN) await main();
