/// Read-only local bridge for catalog lookup. The endpoint and bearer are owned
/// by app setup, never by tool arguments. Errors never include bridge contents.
const String genUiConnectorSearchJavascript = r'''
const genUiConnectorSearchTool = {
  name: 'find_connectors',
  description: 'Search the connector catalogue already loaded in OpenCode Mobile. '
    + 'Use an exact catalogId from these results when suggesting a connector with show. '
    + 'Do not invent catalog IDs. This reads metadata only and never installs, '
    + 'connects, signs in, or grants permission to a connector.',
  inputSchema: {
    type: 'object', additionalProperties: false, required: ['query'],
    properties: {
      query: {type: 'string', minLength: 1, maxLength: 100},
      limit: {type: 'integer', minimum: 1, maximum: 10, default: 5}
    }
  },
  annotations: {readOnlyHint: true, destructiveHint: false,
    idempotentHint: true, openWorldHint: false}
};
const connectorSearchUrl = /\b(?:[a-z][a-z0-9+.-]*:(?:\/\/)?|www\.)[^\s<>]+/i;
function connectorSearchSecret(value) {
  if (/\bBearer\s+[A-Za-z0-9\-._~+/]+=*/i.test(value)
      || /-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----/.test(value)
      || /(?<![A-Za-z0-9_-])(?:sk-ant-[A-Za-z0-9_-]{8,}|sk-proj-[A-Za-z0-9_-]{8,}|sk-[A-Za-z0-9_-]{16,}|AIza[A-Za-z0-9_-]{30,}|github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|xox[abpr]-[A-Za-z0-9-]{10,})/.test(value)) return true;
  const named = /(?<![A-Za-z0-9_-])(?:[A-Za-z0-9_-]*?(?:api[_-]?key|_key|token|secret|password|passwd)|Proxy-Authorization|Authorization|Set-Cookie|Cookie|x-api-key)["']?\s*[:=]\s*["']?([^\s"']+)/gi;
  for (const match of value.matchAll(named)) {
    if (!/^•+$/.test(match[1])) return true;
  }
  for (const match of value.matchAll(/(?<![A-Za-z0-9_-])([A-Za-z0-9_-]{2,})\.([A-Za-z0-9_-]{2,})\.[A-Za-z0-9_-]*(?![A-Za-z0-9_-])/g)) {
    if (match[1].startsWith('eyJ') && match[2].startsWith('eyJ')) return true;
    try {
      const values = [match[1], match[2]].map(part => JSON.parse(Buffer.from(part, 'base64url').toString('utf8')));
      if (values.every(part => part && typeof part === 'object' && !Array.isArray(part))) return true;
    } catch (_) {}
  }
  return false;
}
function validateGenUiConnectorSearchArguments(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)
      || Object.keys(input).some(k => !['query','limit'].includes(k))
      || typeof input.query !== 'string'
      || (Object.hasOwn(input, 'limit') && (!Number.isInteger(input.limit)
          || input.limit < 1 || input.limit > 10))) throw new Error('invalid');
  const query = input.query.trim();
  if (!query || [...query].length > 100 || /[\uD800-\uDFFF]/u.test(query)
      || !query.replace(/[\s\x00-\x1f\x7f-\x9f\u200b-\u200f\u202a-\u202e\u2060-\u206f]+/g, ' ').trim()
      || connectorSearchUrl.test(query) || connectorSearchSecret(query)) throw new Error('invalid');
  return {query, limit: input.limit ?? 5};
}
async function findGenUiConnectors(args) {
  const fs = require('node:fs');
  const http = require('node:http');
  const {TextDecoder} = require('node:util');
  const decode = bytes => new TextDecoder('utf-8', {fatal: true}).decode(bytes);
  const unavailable = () => ({status: 'unavailable',
    message: 'Connector search is unavailable. Try again from Tools.', matches: []});
  const notLoaded = () => ({status: 'catalogue_not_loaded',
    message: 'Catalogue not loaded. Open Tools > MCP to load it.', matches: []});
  const object = (value, keys) => value !== null && typeof value === 'object'
    && !Array.isArray(value) && Object.keys(value).length === keys.length
    && keys.every(key => Object.hasOwn(value, key));
  // Direction controls by code point, as gen_ui_validation_js does.
  const direction = cp => cp === 0x061c || cp === 0x200e || cp === 0x200f
    || (cp >= 0x202a && cp <= 0x202e) || (cp >= 0x2066 && cp <= 0x2069);
  const plain = (value, max, min = 0) => typeof value === 'string'
    && [...value].length >= min && [...value].length <= max
    && !/[\x00-\x1f\x7f-\x9f\uD800-\uDFFF]/u.test(value)
    && ![...value].some(ch => direction(ch.codePointAt(0)))
    && !connectorSearchUrl.test(value) && !connectorSearchSecret(value);
  let bridge;
  let fd;
  try {
    fd = fs.openSync(connectorSearchPath,
      fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW | fs.constants.O_NONBLOCK);
    const stat = fs.fstatSync(fd);
    if (!stat.isFile() || stat.size < 1 || stat.size > 2048) return unavailable();
    const bytes = Buffer.alloc(2049);
    const length = fs.readSync(fd, bytes, 0, bytes.length, 0);
    if (length !== stat.size || length > 2048) return unavailable();
    bridge = JSON.parse(decode(bytes.subarray(0, length)));
    if (!object(bridge, ['endpoint','bearer']) || typeof bridge.endpoint !== 'string'
        || typeof bridge.bearer !== 'string' || !/^[a-fA-F0-9]{64}$/.test(bridge.bearer)) {
      return unavailable();
    }
    const endpoint = /^http:\/\/127\.0\.0\.1:([1-9][0-9]{0,4})\/find-connectors$/.exec(bridge.endpoint);
    if (!endpoint || Number(endpoint[1]) > 65535) return unavailable();
    bridge = {port: Number(endpoint[1]), bearer: bridge.bearer};
  } catch (error) {
    return error && error.code === 'ENOENT' ? notLoaded() : unavailable();
  } finally {
    if (fd !== undefined) fs.closeSync(fd);
  }
  const validate = value => {
    if (!value || typeof value !== 'object') throw new Error('invalid');
    if (value.status === 'catalogue_not_loaded' || value.status === 'unavailable') {
      const expected = value.status === 'catalogue_not_loaded' ? notLoaded() : unavailable();
      if (!object(value, ['status','message','matches']) || value.message !== expected.message
          || !Array.isArray(value.matches) || value.matches.length) throw new Error('invalid');
      return expected;
    }
    if (!object(value, ['status','matches']) || value.status !== 'ok'
        || !Array.isArray(value.matches) || value.matches.length > args.limit) throw new Error('invalid');
    return {status: 'ok', matches: value.matches.map(match => {
      if (!object(match, ['catalogId','name','description','runtime','needsSignIn','connected'])
          || typeof match.catalogId !== 'string' || match.catalogId.length > 256
          || !/^[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+$/.test(match.catalogId)
          || !plain(match.name, 120, 1) || !plain(match.description, 300)
          || !['hosted','npx','uvx','docker','unavailable'].includes(match.runtime)
          || !['unknown','required','not_required'].includes(match.needsSignIn)
          || (match.connected !== null && typeof match.connected !== 'boolean')) throw new Error('invalid');
      return {catalogId: match.catalogId, name: match.name, description: match.description,
        runtime: match.runtime, needsSignIn: match.needsSignIn, connected: match.connected};
    })};
  };
  try {
    const body = JSON.stringify({arguments: args, directory: process.cwd()});
    const bytes = await new Promise((resolve, reject) => {
      let settled = false;
      let timer;
      const finish = (failure, value) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        failure ? reject(Object.assign(new Error('unavailable'), {
          invalidRequest: failure === 'invalid-request'
        })) : resolve(value);
      };
      const request = http.request({hostname: '127.0.0.1', port: bridge.port,
        path: '/find-connectors', method: 'POST', agent: false, headers: {
          Authorization: 'Bearer ' + bridge.bearer, 'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body, 'utf8')
        }}, response => {
          if (response.statusCode !== 200) {
            finish(response.statusCode === 400 ? 'invalid-request' : true);
            response.destroy(); request.destroy(); return;
          }
          let length = 0;
          const chunks = [];
          response.on('data', chunk => {
            length += chunk.length;
            if (length > 16384) { finish(true); response.destroy(); request.destroy(); }
            else chunks.push(chunk);
          });
          response.on('end', () => finish(false, Buffer.concat(chunks, length)));
          response.on('error', () => finish(true));
          response.on('aborted', () => finish(true));
        });
      request.on('error', () => finish(true));
      timer = setTimeout(() => { finish(true); request.destroy(); }, 5000);
      request.end(body);
    });
    return validate(JSON.parse(decode(bytes)));
  } catch (error) {
    if (error?.invalidRequest) throw new Error('invalid');
    return unavailable();
  }
}
''';
