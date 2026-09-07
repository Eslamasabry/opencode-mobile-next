// Optional deployment extension. This is not an upstream OpenCode endpoint.
// Importing this module performs no I/O and starts no listener.
import { createHash, createHmac, timingSafeEqual } from 'node:crypto';
import { constants } from 'node:fs';
import { open } from 'node:fs/promises';
import { createServer } from 'node:http';
import { isAbsolute, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const QUOTA_PATH = '/ocmn/quota/v1';
export const CLAUDE_QUOTA_PATH = '/ocmn/quota/v1/claude';
export const MINIMAX_QUOTA_PATH = '/ocmn/quota/v1/minimax';
export const MINIMAX_QUOTA_URL = 'https://www.minimax.io/v1/token_plan/remains';
export const WHAM_URL = 'https://chatgpt.com/backend-api/wham/usage';
export const MAX_BYTES = 64 * 1024;
export const PROVIDER_TIMEOUT_MS = 10_000;
export const CACHE_MS = 60_000;
const MAX_TIMESTAMP = 8_640_000_000_000_000;
const PLANS = new Set(['free', 'go', 'plus', 'pro', 'team', 'business', 'enterprise', 'edu']);
const FORMATS = new Set(['codex', 'opencode']);
const TOKEN_PATTERN = /^[A-Za-z0-9._~+/-]{32,4096}={0,2}$/;
const AUTH_STATUSES = new Set(['unconfigured', 'unsupported', 'authRequired']);

export class ConfigurationError extends Error {
  constructor(code) {
    super(code);
    this.name = 'ConfigurationError';
    this.code = code;
  }
}

class ProviderFailure extends Error {
  constructor(status) {
    super(status);
    this.status = status;
  }
}

const object = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const identifier = (value) => typeof value === 'string' && /^[\x21-\x7e]{1,1024}$/.test(value);
const accessToken = (value) => typeof value === 'string' && /^[A-Za-z0-9._~+/-]+=*$/.test(value)
  && value.length <= MAX_BYTES;
const timestamp = (value) => Number.isSafeInteger(value) && value >= 0 && value <= MAX_TIMESTAMP;
const invalid = () => { throw new ProviderFailure('invalidResponse'); };

function validateReadToken(token) {
  if (typeof token !== 'string' || !TOKEN_PATTERN.test(token)) {
    throw new ConfigurationError('QUOTA_READ_TOKEN_INVALID');
  }
  return token;
}

// Compare fixed-length digests, not a variable-length/prefix string comparison.
export function createReadTokenVerifier(token) {
  const expected = createHash('sha256').update(validateReadToken(token)).digest();
  return (authorization) => {
    if (typeof authorization !== 'string') return false;
    const match = /^Bearer ([A-Za-z0-9._~+/-]+={0,2})$/i.exec(authorization);
    if (!match || !TOKEN_PATTERN.test(match[1])) return false;
    return timingSafeEqual(createHash('sha256').update(match[1]).digest(), expected);
  };
}

// Explicit regular files only: no path discovery, symlink following, FIFO reads,
// credential refresh, writes, keyring access, or provider-specific defaults.
export async function readBoundedFile(filePath) {
  if (typeof filePath !== 'string' || !isAbsolute(filePath)) {
    throw new ConfigurationError('QUOTA_SOURCE_FILE_INVALID');
  }
  try {
    const file = await open(filePath, constants.O_RDONLY
      | (constants.O_NOFOLLOW ?? 0) | (constants.O_NONBLOCK ?? 0));
    try {
      const stat = await file.stat();
      if (!stat.isFile() || stat.size > MAX_BYTES) {
        throw new ConfigurationError('QUOTA_SOURCE_FILE_INVALID');
      }
      const buffer = Buffer.alloc(MAX_BYTES + 1);
      let length = 0;
      while (length < buffer.length) {
        const { bytesRead } = await file.read(buffer, length, buffer.length - length, null);
        if (!bytesRead) break;
        length += bytesRead;
      }
      if (length > MAX_BYTES) throw new ConfigurationError('QUOTA_SOURCE_FILE_INVALID');
      return buffer.subarray(0, length);
    } finally {
      await file.close();
    }
  } catch {
    throw new ConfigurationError('QUOTA_SOURCE_FILE_INVALID');
  }
}

function decodeBytes(bytes) {
  if (!(bytes instanceof Uint8Array) || bytes.byteLength > MAX_BYTES) {
    throw new ConfigurationError('QUOTA_SOURCE_FILE_INVALID');
  }
  return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
}

export async function loadReadToken(filePath, { readFile = readBoundedFile } = {}) {
  if (!filePath) throw new ConfigurationError('QUOTA_READ_TOKEN_FILE_REQUIRED');
  try {
    // One conventional trailing newline is accepted; other whitespace is not.
    return validateReadToken(decodeBytes(await readFile(filePath)).replace(/\r?\n$/, ''));
  } catch {
    throw new ConfigurationError('QUOTA_READ_TOKEN_FILE_INVALID');
  }
}

// JWTs here supply local identity hints/expiry, not proof of authentication.
// WHAM must independently return the selected identity before showing windows.
function jwtClaims(token) {
  if (typeof token !== 'string' || token.length > MAX_BYTES) return {};
  const parts = token.split('.');
  if (parts.length !== 3 || parts.some((part) => !/^[A-Za-z0-9_-]+$/.test(part))) return {};
  try {
    const claims = JSON.parse(decodeBytes(Buffer.from(parts[1], 'base64url')));
    return object(claims) ? claims : {};
  } catch {
    return {};
  }
}

function identityClaims(claims) {
  const auth = claims['https://api.openai.com/auth'];
  return object(auth) ? auth : {};
}

export function parseAuthDocument(document, { format = 'codex', nowMs = Date.now() } = {}) {
  if (!FORMATS.has(format)) return { status: 'unsupported' };
  if (!object(document)) return { status: 'authRequired' };
  let token, accountId, idToken, expiresAtMs;
  if (format === 'codex') {
    if ((document.auth_mode != null && document.auth_mode !== 'chatgpt')
      || document.OPENAI_API_KEY != null || document.personal_access_token != null
      || document.agent_identity != null || document.bedrock_api_key != null
      || document.bedrock_access_keys != null) return { status: 'unsupported' };
    if (!object(document.tokens)) return { status: 'authRequired' };
    token = document.tokens.access_token;
    accountId = document.tokens.account_id;
    idToken = document.tokens.id_token;
  } else {
    const entry = document.openai;
    if (!object(entry)) return { status: 'authRequired' };
    if (entry.type !== 'oauth') return { status: 'unsupported' };
    token = entry.access;
    accountId = entry.accountId;
    expiresAtMs = entry.expires;
    if (!timestamp(expiresAtMs)) return { status: 'authRequired' };
  }
  if (!accessToken(token) || !identifier(accountId)) return { status: 'authRequired' };
  const claims = jwtClaims(token);
  if (claims.exp != null) {
    const expiry = claims.exp * 1000;
    if (!Number.isSafeInteger(claims.exp) || !timestamp(expiry)) return { status: 'authRequired' };
    expiresAtMs = expiresAtMs == null ? expiry : Math.min(expiry, expiresAtMs);
  }
  if (expiresAtMs != null && expiresAtMs <= nowMs) return { status: 'authRequired' };
  const id = identityClaims(jwtClaims(idToken));
  const access = identityClaims(claims);
  if (id.chatgpt_account_is_fedramp === true || access.chatgpt_account_is_fedramp === true) {
    return { status: 'unsupported' }; // No routing/header guess for a different provider edge.
  }
  const userId = id.chatgpt_user_id ?? id.user_id ?? access.chatgpt_user_id ?? access.user_id;
  if (userId != null && !identifier(userId)) return { status: 'authRequired' };
  return { status: 'ok', accessToken: token, accountId, userId, expiresAtMs };
}

export function createFileAuthSource({ filePath, format = 'codex', readFile = readBoundedFile,
  clock = Date.now } = {}) {
  return async () => {
    if (!filePath) return { status: 'unconfigured' };
    try {
      return parseAuthDocument(JSON.parse(decodeBytes(await readFile(filePath))), {
        format, nowMs: clock(),
      });
    } catch {
      return { status: 'authRequired' };
    }
  };
}

// Historical schema research only; no live Claude auth-source adapter is wired.
export function parseClaudeAuthDocument(document, { format = 'claude', nowMs = Date.now() } = {}) {
  if (!['claude', 'opencode'].includes(format)) return { status: 'unsupported' };
  if (!object(document)) return { status: 'authRequired' };
  const entry = format === 'claude' ? document.claudeAiOauth : document.anthropic;
  if (!object(entry)) return { status: 'authRequired' };
  if (format === 'opencode' && entry.type !== 'oauth') return { status: 'unsupported' };
  const token = format === 'claude' ? entry.accessToken : entry.access;
  const expiresAtMs = format === 'claude' ? entry.expiresAt : entry.expires;
  if (!accessToken(token) || (expiresAtMs != null &&
    (!timestamp(expiresAtMs) || expiresAtMs <= nowMs))) return { status: 'authRequired' };
  return { status: 'ok', accessToken: token, expiresAtMs: expiresAtMs ?? null };
}

function normalizeAuth(auth, now) {
  if (object(auth) && AUTH_STATUSES.has(auth.status)) return { status: auth.status };
  if (!object(auth) || auth.status !== 'ok' || !accessToken(auth.accessToken)
    || !identifier(auth.accountId) || (auth.userId != null && !identifier(auth.userId))
    || (auth.expiresAtMs != null && (!timestamp(auth.expiresAtMs) || auth.expiresAtMs <= now))) {
    return { status: 'authRequired' };
  }
  return { status: 'ok', accessToken: auth.accessToken, accountId: auth.accountId,
    userId: auth.userId ?? null, expiresAtMs: auth.expiresAtMs ?? null };
}

function emptySnapshot(status, now, account = { status: 'unverified' }, provider = 'codex') {
  return { schemaVersion: 1, provider, source: provider === 'claude' ? 'claude.oauth'
    : provider === 'minimax' ? 'minimax.tokenPlan' : 'codex.wham', status,
    freshness: 'none', fetchedAtMs: now, expiresAtMs: now, account,
    ordinaryUsageAllowed: null, windows: [] };
}

function mapWindow(value, id) {
  if (value == null) return { id, status: 'missing' };
  if (!object(value) || typeof value.used_percent !== 'number'
    || !Number.isFinite(value.used_percent) || value.used_percent < 0 || value.used_percent > 100) invalid();
  const window = { id, status: 'reported', usedPercent: value.used_percent };
  if (value.limit_window_seconds != null) {
    if (!Number.isSafeInteger(value.limit_window_seconds) || value.limit_window_seconds <= 0
      || value.limit_window_seconds > 315_360_000) invalid();
    window.durationSeconds = value.limit_window_seconds;
  }
  if (value.reset_at != null) {
    if (!Number.isSafeInteger(value.reset_at) || !timestamp(value.reset_at * 1000)) invalid();
    window.resetsAtMs = value.reset_at * 1000;
  }
  if (value.reset_after_seconds != null && (!Number.isSafeInteger(value.reset_after_seconds)
    || value.reset_after_seconds < 0 || value.reset_after_seconds > MAX_TIMESTAMP / 1000)) invalid();
  return window;
}

function mapWham(payload, auth, ref, now) {
  if (!object(payload) || typeof payload.plan_type !== 'string' || !payload.plan_type) invalid();
  for (const field of ['account_id', 'user_id']) {
    if (payload[field] != null && !identifier(payload[field])) invalid();
  }
  const rate = payload.rate_limit;
  if (rate != null && (!object(rate) || typeof rate.allowed !== 'boolean'
    || typeof rate.limit_reached !== 'boolean')) invalid();
  const windows = [mapWindow(rate?.primary_window, 'primary'), mapWindow(rate?.secondary_window, 'secondary')];
  const account = { ref, status: 'unverified' };
  if ((payload.account_id != null && payload.account_id !== auth.accountId)
    || (auth.userId != null && payload.user_id != null && payload.user_id !== auth.userId)) {
    account.status = 'mismatch';
  } else if (payload.account_id === auth.accountId
    && (auth.userId == null || payload.user_id === auth.userId)) {
    account.status = 'matched';
  }
  if (account.status !== 'matched') return emptySnapshot('ok', now, account);
  if (PLANS.has(payload.plan_type)) account.plan = payload.plan_type;
  return { ...emptySnapshot('ok', now, account), freshness: 'fresh', expiresAtMs: now + CACHE_MS,
    // Follow Codex's validated account+user decision, never percent arithmetic.
    ordinaryUsageAllowed: auth.userId != null && payload.user_id === auth.userId
      ? rate?.allowed ?? null : null,
    windows };
}

function isoReset(value) {
  if (typeof value !== 'string' || value.length > 64) invalid();
  const parts = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$/.exec(value);
  if (!parts) invalid();
  const [year, month, day, hour, minute, second] = parts.slice(1, 7).map(Number);
  const calendar = new Date(Date.UTC(year, month - 1, day));
  if (year < 1970 || calendar.getUTCFullYear() !== year || calendar.getUTCMonth() !== month - 1
    || calendar.getUTCDate() !== day || hour > 23 || minute > 59 || second > 59) invalid();
  const zone = parts[7];
  if (zone !== 'Z' && (Number(zone.slice(1, 3)) > 23 || Number(zone.slice(4)) > 59)) invalid();
  const parsed = Date.parse(value);
  if (!timestamp(parsed)) invalid();
  return parsed;
}

function claudeWindow(value, id, field, durationSeconds) {
  if (value == null) return { id, status: 'missing' };
  if (!object(value) || typeof value[field] !== 'number' || !Number.isFinite(value[field])
    || value[field] < 0 || value[field] > 100) invalid();
  return { id, status: 'reported', usedPercent: value[field],
    ...(durationSeconds == null ? {} : { durationSeconds }),
    ...(value.resets_at == null ? {} : { resetsAtMs: isoReset(value.resets_at) }) };
}

// Retained as a pure research mapper. Normal collection below is disabled.
export function mapClaudeUsage(payload, ref, now) {
  if (!object(payload) || !['limits', 'five_hour', 'seven_day'].some((key) => Object.hasOwn(payload, key))) invalid();
  let windows;
  if (payload.limits != null) {
    if (!Array.isArray(payload.limits) || payload.limits.length > 64) invalid();
    if (payload.limits.some((limit) => !object(limit))) invalid();
    const core = (kind) => {
      const matches = payload.limits.filter((limit) => limit.kind === kind);
      if (matches.length > 1) invalid();
      return matches[0] ?? null;
    };
    // Prefer the structured schema when present, including an empty list.
    // Do not merge legacy copies or guess duration from a generic "session".
    windows = [claudeWindow(core('session'), 'primary', 'percent'),
      claudeWindow(core('weekly_all'), 'secondary', 'percent')];
  } else {
    windows = [claudeWindow(payload.five_hour, 'primary', 'utilization', 18_000),
      claudeWindow(payload.seven_day, 'secondary', 'utilization', 604_800)];
  }
  return { ...emptySnapshot('ok', now, { ref, status: 'sourceBound' }, 'claude'),
    freshness: 'fresh', expiresAtMs: now + CACHE_MS, windows };
}

// Explicit operator-provisioned Subscription Key only. Pay-as-you-go API keys
// represent balance, not subscription quota; no CLI OAuth store is discovered.
export function createMiniMaxKeySource({ filePath, readFile = readBoundedFile } = {}) {
  return async () => {
    if (!filePath) return { status: 'unconfigured' };
    try {
      const token = decodeBytes(await readFile(filePath)).replace(/\r?\n$/, '');
      if (!accessToken(token)) return { status: 'authRequired' };
      if (token.startsWith('sk-api-')) return { status: 'unsupported' };
      return { status: 'ok', accessToken: token };
    } catch { return { status: 'authRequired' }; }
  };
}

function normalizeMiniMaxAuth(auth) {
  if (object(auth) && AUTH_STATUSES.has(auth.status)) return { status: auth.status };
  if (!object(auth) || auth.status !== 'ok' || !accessToken(auth.accessToken)) {
    return { status: 'authRequired' };
  }
  if (auth.accessToken.startsWith('sk-api-')) return { status: 'unsupported' };
  // An opaque reference binds this snapshot to the configured key, never to a
  // claimed account identity. Key rotation deliberately changes that reference.
  return { status: 'ok', accessToken: auth.accessToken,
    accountId: `minimax:${auth.accessToken}`, userId: null, expiresAtMs: null };
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
  // The current mobile contract cannot express unlimited or boosted capacity.
  // Preserve unknown instead of coercing either into a 0–100% used window.
  if (status === 3 || remaining == null || (weekly && boost != null && boost !== 1000)) {
    return { id, status: 'missing' };
  }
  if (status === 2 && remaining !== 0) invalid();
  const start = value[weekly ? 'weekly_start_time' : 'start_time'];
  const end = value[weekly ? 'weekly_end_time' : 'end_time'];
  if (!timestamp(start) || !timestamp(end) || end <= start
    || (end - start) % 1000 !== 0 || end - start > 315_360_000_000) invalid();
  return { id, status: 'reported', usedPercent: 100 - remaining,
    durationSeconds: (end - start) / 1000, resetsAtMs: end };
}

// Contract: MiniMax-AI/cli bfbb4cb75ec343149eaccfd668c5011aa27bcf2b,
// src/types/api.ts and src/output/quota-table.ts. Only the shared general pool;
// never sum model buckets or infer remaining capacity from ambiguous counts.
export function mapMiniMaxUsage(payload, ref, now) {
  if (!object(payload) || !Array.isArray(payload.model_remains)
    || payload.model_remains.length > 64 || payload.model_remains.some((row) => !object(row))) invalid();
  if (payload.base_resp != null) {
    if (!object(payload.base_resp) || !Number.isInteger(payload.base_resp.status_code)) invalid();
    if (payload.base_resp.status_code !== 0) throw new ProviderFailure('unavailable');
  }
  const general = payload.model_remains.filter((row) => row.model_name === 'general');
  if (general.length > 1) invalid();
  const account = { ref, status: 'sourceBound' };
  if (general.length === 0) return emptySnapshot('unsupported', now, account, 'minimax');
  return { ...emptySnapshot('ok', now, account, 'minimax'), freshness: 'fresh',
    expiresAtMs: now + CACHE_MS,
    windows: [miniMaxWindow(general[0], 'primary'), miniMaxWindow(general[0], 'secondary')] };
}

async function readProviderBody(response, signal) {
  const declared = response.headers.get('content-length');
  if (declared != null && (!/^\d+$/.test(declared) || Number(declared) > MAX_BYTES)) invalid();
  if (!/^application\/json(?:\s*;|$)/i.test(response.headers.get('content-type') ?? '')) invalid();
  if (!response.body) invalid();
  const reader = response.body.getReader();
  const cancel = () => { void reader.cancel().catch(() => {}); };
  signal.addEventListener('abort', cancel, { once: true });
  let done = false;
  try {
    const chunks = [];
    let size = 0;
    while (!done) {
      if (signal.aborted) throw new ProviderFailure('unavailable');
      const part = await reader.read();
      done = part.done;
      if (!done) {
        size += part.value.byteLength;
        if (size > MAX_BYTES) invalid();
        chunks.push(part.value);
      }
    }
    return JSON.parse(decodeBytes(Buffer.concat(chunks, size)));
  } catch (error) {
    if (error instanceof ProviderFailure) throw error;
    throw new ProviderFailure('invalidResponse');
  } finally {
    signal.removeEventListener('abort', cancel);
    if (!done) cancel();
    reader.releaseLock();
  }
}

export function createCollector({ readToken, authSource = async () => ({ status: 'unconfigured' }),
  fetchImpl = globalThis.fetch, clock = Date.now, timeoutMs = PROVIDER_TIMEOUT_MS, provider = 'codex' } = {}) {
  validateReadToken(readToken);
  if (!['codex', 'claude', 'minimax'].includes(provider)) throw new ConfigurationError('QUOTA_PROVIDER_INVALID');
  const empty = (status, time, account) => emptySnapshot(status, time, account, provider);
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > PROVIDER_TIMEOUT_MS) {
    throw new ConfigurationError('QUOTA_TIMEOUT_INVALID');
  }
  const now = () => {
    const value = clock();
    if (!timestamp(value) || value > MAX_TIMESTAMP - CACHE_MS) {
      throw new ConfigurationError('QUOTA_CLOCK_INVALID');
    }
    return value;
  };
  if (provider === 'claude') {
    // An undocumented usage endpoint and readable OAuth file do not establish
    // a supported/permitted third-party subscription integration. Do not even
    // invoke authSource; no credential read or provider request is permitted.
    return { async readSnapshot() { return empty('unsupported', now()); } };
  }
  const hmac = (text) => createHmac('sha256', readToken).update(text).digest('hex');
  let generation = 0, activeKey = null, cache = null, flight = null;
  let authQueue = Promise.resolve();

  // Serialize file reads so an older, slow credential read cannot overtake a
  // newer one and restore a previous account. Recheck again before publication.
  function selectAuth() {
    const selection = authQueue.then(async () => {
      let auth;
      try {
        const source = await authSource();
        auth = provider === 'minimax' ? normalizeMiniMaxAuth(source) : normalizeAuth(source, now());
      }
      catch { auth = { status: 'authRequired' }; }
      const key = auth.status === 'ok'
        ? hmac(JSON.stringify([auth.accountId, auth.userId, auth.accessToken, auth.expiresAtMs])) : null;
      if (key !== activeKey) {
        activeKey = key;
        generation++;
        cache = null;
        flight?.controller.abort();
        flight = null;
      }
      return { auth, generation };
    });
    authQueue = selection.then(() => {}, () => {});
    return selection;
  }

  async function collect(auth, controller) {
    const ref = hmac(auth.accountId);
    let timer;
    const aborted = new Promise((_, reject) => {
      const fail = () => reject(new ProviderFailure('unavailable'));
      controller.signal.addEventListener('abort', fail, { once: true });
      timer = setTimeout(() => controller.abort(), timeoutMs);
    });
    const request = async () => {
      const url = provider === 'minimax' ? MINIMAX_QUOTA_URL : WHAM_URL;
      const response = await fetchImpl(url, { method: 'GET', redirect: 'error', credentials: 'omit',
        signal: controller.signal, headers: { Authorization: `Bearer ${auth.accessToken}`,
          ...(provider === 'codex' ? { 'ChatGPT-Account-Id': auth.accountId } : {}),
          Accept: 'application/json', 'User-Agent': 'ocmn-quota/1' } });
      try {
        if (controller.signal.aborted) throw new ProviderFailure('unavailable');
        if (response.redirected || (response.url && response.url !== url)) invalid();
        if (response.status !== 200) {
          const status = response.status === 401 ? 'authRequired'
            : response.status === 429 ? 'rateLimited'
            : [404, 405, 501].includes(response.status) ? 'unsupported'
            : response.status === 204 ? 'invalidResponse' : 'unavailable';
          throw new ProviderFailure(status);
        }
        const payload = await readProviderBody(response, controller.signal);
        return provider === 'minimax' ? mapMiniMaxUsage(payload, ref, now())
          : mapWham(payload, auth, ref, now());
      } finally {
        // Also cancel unread bodies rejected by status, URL or size headers.
        // Never wait on an uncooperative peer's cancellation promise.
        void response.body?.cancel().catch(() => {});
      }
    };
    try {
      return await Promise.race([request(), aborted]);
    } catch (error) {
      return empty(error instanceof ProviderFailure ? error.status : 'unavailable', now(),
        { ref, status: 'unverified' });
    } finally {
      clearTimeout(timer);
      controller.abort();
    }
  }

  return {
    async readSnapshot() {
      const selected = await selectAuth();
      if (selected.generation !== generation) return empty('unavailable', now());
      if (selected.auth.status !== 'ok') return empty(selected.auth.status, now());
      const time = now();
      if (cache && time >= cache.fetchedAtMs && time < cache.expiresAtMs) return structuredClone(cache);
      if (flight?.generation === generation) return structuredClone(await flight.promise);
      cache = null;
      const pending = { generation, controller: new AbortController() };
      flight = pending;
      pending.promise = (async () => {
        const snapshot = await collect(selected.auth, pending.controller);
        await selectAuth();
        if (pending.generation !== generation) return empty('unavailable', now());
        if (snapshot.status === 'ok' && (snapshot.account.status === 'matched'
          || (provider === 'minimax' && snapshot.account.status === 'sourceBound'))
          && snapshot.freshness === 'fresh') cache = snapshot;
        return snapshot;
      })().finally(() => { if (flight === pending) flight = null; });
      return structuredClone(await pending.promise);
    },
  };
}

export function createRequestHandler({ readToken, collector, claudeCollector, minimaxCollector }) {
  const authorized = createReadTokenVerifier(readToken);
  return async (request, response) => {
    const send = (status, body) => {
      response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff', ...(status !== 200 ? { Connection: 'close' } : {}) });
      response.end(JSON.stringify(body));
    };
    if (!['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(request.socket?.remoteAddress)) {
      send(403, { error: 'forbidden' }); return;
    }
    const duplicateAuth = (request.rawHeaders ?? []).filter((value, index) =>
      index % 2 === 0 && value.toLowerCase() === 'authorization').length > 1;
    if (duplicateAuth || !authorized(request.headers.authorization)) {
      send(401, { error: 'collectorAuth' }); return;
    }
    // Exact request-target comparison rejects query strings and absolute URLs.
    const selected = request.url === QUOTA_PATH ? collector
      : request.url === CLAUDE_QUOTA_PATH ? claudeCollector
      : request.url === MINIMAX_QUOTA_PATH ? minimaxCollector : null;
    if (!selected) { send(404, { error: 'unsupported' }); return; }
    if (request.method !== 'GET') { send(405, { error: 'unsupported' }); return; }
    if (request.headers['transfer-encoding'] != null
      || (request.headers['content-length'] != null && request.headers['content-length'] !== '0')) {
      send(403, { error: 'forbidden' }); return;
    }
    try { send(200, await selected.readSnapshot()); }
    catch { send(503, { error: 'unavailable' }); }
  };
}

export async function loadConfiguration(env, { readFile = readBoundedFile } = {}) {
  // Retired options must not disable a valid Codex deployment, be retained in
  // config, or become file reads. The CLI emits only a fixed deprecation code.
  const ignoredClaudeConfiguration = env.OCMN_CLAUDE_AUTH_FILE != null
    || env.OCMN_CLAUDE_AUTH_FORMAT != null;
  const port = env.OCMN_QUOTA_PORT ?? '4195';
  if (!/^\d{4,5}$/.test(port) || Number(port) < 1024 || Number(port) > 65535) {
    throw new ConfigurationError('QUOTA_PORT_INVALID');
  }
  const format = env.OCMN_QUOTA_AUTH_FORMAT ?? 'codex';
  if (!FORMATS.has(format)) throw new ConfigurationError('QUOTA_AUTH_FORMAT_INVALID');
  const filePath = env.OCMN_QUOTA_AUTH_FILE;
  if (filePath != null && (!filePath || !isAbsolute(filePath))) {
    throw new ConfigurationError('QUOTA_AUTH_FILE_INVALID');
  }
  const minimaxKeyFile = env.OCMN_MINIMAX_KEY_FILE;
  if (minimaxKeyFile != null && (!minimaxKeyFile || !isAbsolute(minimaxKeyFile))) {
    throw new ConfigurationError('QUOTA_MINIMAX_KEY_FILE_INVALID');
  }
  const tokenPath = env.OCMN_QUOTA_READ_TOKEN_FILE;
  if (!tokenPath) throw new ConfigurationError('QUOTA_READ_TOKEN_FILE_REQUIRED');
  if (!isAbsolute(tokenPath)) throw new ConfigurationError('QUOTA_READ_TOKEN_FILE_INVALID');
  return { host: '127.0.0.1', port: Number(port), format, filePath, ignoredClaudeConfiguration,
    ...(minimaxKeyFile == null ? {} : { minimaxKeyFile }),
    readToken: await loadReadToken(tokenPath, { readFile }) };
}

async function main() {
  if (process.argv.length !== 2) throw new ConfigurationError('QUOTA_ARGUMENTS_UNSUPPORTED');
  const config = await loadConfiguration(process.env);
  if (config.ignoredClaudeConfiguration) process.stderr.write('QUOTA_CLAUDE_COLLECTION_UNAVAILABLE\n');
  const collector = createCollector({ readToken: config.readToken,
    authSource: createFileAuthSource(config) });
  const claudeCollector = createCollector({ readToken: config.readToken, provider: 'claude' });
  const minimaxCollector = createCollector({ readToken: config.readToken, provider: 'minimax',
    authSource: createMiniMaxKeySource({ filePath: config.minimaxKeyFile }) });
  const server = createServer({ maxHeaderSize: 8192, requestTimeout: 10_000,
    headersTimeout: 5000, keepAliveTimeout: 1000 }, createRequestHandler({ ...config, collector, claudeCollector, minimaxCollector }));
  server.maxRequestsPerSocket = 100;
  server.on('clientError', (_error, socket) => socket.destroy());
  server.on('error', () => { process.stderr.write('QUOTA_LISTENER_FAILED\n'); process.exitCode = 1; });
  server.listen(config.port, config.host);
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  main().catch((error) => {
    // Never print native errors, paths, configuration values, or causes.
    const code = error instanceof ConfigurationError ? error.code : 'QUOTA_STARTUP_FAILED';
    process.stderr.write(`${code}\n`);
    process.exitCode = 1;
  });
}
