package io.github.eslamasabry.oc.engine.cards

// Generated verbatim from the Flutter sources (lib/domain/genui/gen_ui_validation_js.dart,
// lib/builtin/agents/gen_ui_connector_search_js.dart, lib/builtin/agents/gen_ui_server.dart):
// the card tool server is the same zero-dependency Node.js MCP helper the Flutter app installs.
internal object CardToolJs {
    const val VALIDATION: String = """function normalizeGenUiCard(input) {
  const fail = (code = 'badValue') => { throw new Error(code); };
  let count = 0;
  let stringBytes = 0;
  const boundString = s => { if (s.length > 65536 || scalar(s) > 32768) fail('tooLarge'); stringBytes += Buffer.byteLength(s, 'utf8'); if(stringBytes > 32768) fail('tooLarge'); };
  const seen = new Set();
  const scalar = s => {
    for (let i = 0; i < s.length; i++) {
      const c = s.charCodeAt(i);
      if (c >= 0xd800 && c <= 0xdbff) {
        if (++i >= s.length || s.charCodeAt(i) < 0xdc00 || s.charCodeAt(i) > 0xdfff) fail();
      } else if (c >= 0xdc00 && c <= 0xdfff) fail();
    }
    return [...s].length;
  };
  const bound = (x, depth = 1) => {
    if (++count > 4096 || depth > 8) fail('tooLarge');
    if (x === null || typeof x === 'boolean') return;
    if (typeof x === 'string') { boundString(x); return; }
    if (typeof x === 'number') { if (!Number.isFinite(x)) fail(); return; }
    if (typeof x !== 'object' || seen.has(x)) fail();
    if (!Array.isArray(x) && Object.getPrototypeOf(x) !== Object.prototype && Object.getPrototypeOf(x) !== null) fail();
    seen.add(x);
    for (const [k,v] of Object.entries(x)) { if (!Array.isArray(x)) boundString(k); bound(v, depth + 1); }
    seen.delete(x);
  };
  bound(input);
  if (Buffer.byteLength(JSON.stringify(input), 'utf8') > 32768) fail('tooLarge');
  const obj = (x, allowed, required = []) => {
    if (!x || typeof x !== 'object' || Array.isArray(x)) fail();
    if (Object.keys(x).some(k => !allowed.includes(k))) fail('unknownKey');
    if (required.some(k => !Object.hasOwn(x,k)) || Object.values(x).some(v => v === null)) fail();
    return x;
  };
  const directionalControls = String.fromCharCode(
    0x061c, 0x200e, 0x200f, 0x202a, 0x202b, 0x202c,
    0x202d, 0x202e, 0x2066, 0x2067, 0x2068, 0x2069);
  const controls = new RegExp('[\\x00-\\x08\\x0b-\\x1f\\x7f-\\x9f' + directionalControls + ']', 'g');
  const unsafeLinkCharacters = new RegExp('[\\s\\x00-\\x20\\x7f-\\x9f\\\\' + directionalControls + ']', 'u');
  const text = (x, max = 2000, label = false, min = 0) => {
    if (typeof x !== 'string' || scalar(x) > max) fail();
    let s = x.replace(controls, '');
    if (label) s = s.trim();
    if (scalar(s) < min) fail();
    return s;
  };
  const arr = (x, min, max) => { if (!Array.isArray(x) || x.length < min || x.length > max) fail(); return x; };
  const num = x => { if (typeof x !== 'number' || !Number.isFinite(x)) fail(); return x; };
  const int = (x,min,max) => { num(x); if (!Number.isInteger(x) || x < min || x > max) fail(); return x; };
  const bool = x => { if (typeof x !== 'boolean') fail(); return x; };
  const pick = (x, vals) => { if (!vals.includes(x)) fail(); return x; };
  const id = (x, re) => { if (typeof x !== 'string' || re.exec(x)?.[0] !== x) fail(); return x; };
  const secret = s => { if (/(password|passwd|token|secret|apikey)/i.test(s.replace(/[^a-z0-9]/gi,''))) fail('secretField'); };
  const unique = xs => { if (new Set(xs).size !== xs.length) fail(); };
  const optText = (o,k,max,label=false,min=0) => Object.hasOwn(o,k) ? {[k]:text(o[k],max,label,min)} : {};
  const option = o => {
    obj(o,['id','label','detail'],['id','label']);
    const r = {id:id(o.id,/^[a-zA-Z0-9_-]{1,32}${'$'}/),label:text(o.label,120,true,1),...optText(o,'detail',200)};
    secret(r.id); secret(r.label); return r;
  };
  const options = (v,min,max) => { const r = arr(v,min,max).map(option); unique(r.map(o=>o.id)); return r; };
  const fieldValue = (v,f) => {
    switch(f.type) {
      case 'text': case 'multiline': if (typeof v !== 'string' || scalar(v)>2000 || (f.required && !v.trim())) fail(); return v;
      case 'number': num(v); if ((f.min !== undefined && v<f.min)||(f.max !== undefined && v>f.max)) fail(); return v;
      case 'toggle': return bool(v);
      case 'select': if (typeof v !== 'string' || !f.options.some(o=>o.id===v)) fail(); return v;
      case 'date': {
        if (typeof v !== 'string' || v.length !== 10 || !/^\d{4}-\d{2}-\d{2}${'$'}/.test(v)) fail();
        const [y,m,d]=v.split('-').map(Number); const leap=y%4===0&&(y%100!==0||y%400===0);
        if (y<1 || m<1 || m>12 || d<1 || d>[31,leap?29:28,31,30,31,30,31,31,30,31,30,31][m-1]) fail(); return v;
      }
    }
  };
  const field = o => {
    obj(o,['id','label','type','required','placeholder','default','options','min','max'],['id','label','type']);
    const r={id:id(o.id,/^[a-zA-Z0-9_]{1,32}${'$'}/),label:text(o.label,120,true,1),type:pick(o.type,['text','multiline','number','toggle','select','date']),required:Object.hasOwn(o,'required')?bool(o.required):false,...optText(o,'placeholder',120)};
    secret(r.id); secret(r.label);
    if (r.type==='select') r.options=options(o.options,2,20); else if (Object.hasOwn(o,'options')) fail();
    for (const k of ['min','max']) if (Object.hasOwn(o,k)) { if(r.type!=='number') fail(); r[k]=num(o[k]); }
    if(r.min!==undefined&&r.max!==undefined&&r.min>r.max) fail();
    if(Object.hasOwn(o,'default')) r.default=fieldValue(o.default,r);
    return r;
  };
  const ask = o => {
    obj(o,['kind','options','multi','fields','submitLabel','confirmLabel','cancelLabel','tone','purpose','max'],['kind']);
    switch(o.kind) {
      case 'choice': obj(o,['kind','options','multi'],['kind','options']); return {kind:o.kind,options:options(o.options,2,8),multi:Object.hasOwn(o,'multi')?bool(o.multi):false};
      case 'form': { obj(o,['kind','fields','submitLabel'],['kind','fields']); const fields=arr(o.fields,1,12).map(field); unique(fields.map(f=>f.id)); return {kind:o.kind,fields,...optText(o,'submitLabel',24,true,1)}; }
      case 'confirm': obj(o,['kind','confirmLabel','cancelLabel','tone'],['kind']); return {kind:o.kind,...optText(o,'confirmLabel',24,true,1),...optText(o,'cancelLabel',24,true,1),tone:Object.hasOwn(o,'tone')?pick(o.tone,['normal','danger']):'normal'};
      case 'photo': obj(o,['kind','purpose','max'],['kind','purpose']); return {kind:o.kind,purpose:text(o.purpose,200,true,1),max:Object.hasOwn(o,'max')?int(o.max,1,4):1};
      case 'file': case 'voice': fail('unsupportedAsk'); break;
      default: fail();
    }
  };
  const node = o => {
    if (!o || typeof o.type !== 'string') fail();
    switch(o.type) {
      case 'text': obj(o,['type','text'],['type','text']); return {type:o.type,text:text(o.text)};
      case 'keyValue': obj(o,['type','rows'],['type','rows']); return {type:o.type,rows:arr(o.rows,0,20).map(r=>{obj(r,['key','value'],['key','value']);return {key:text(r.key,60,true,1),value:text(r.value,200)};})};
      case 'list': obj(o,['type','items','style'],['type','items','style']); return {type:o.type,items:arr(o.items,0,30).map(r=>{obj(r,['text','done'],['text']);return {text:text(r.text),done:Object.hasOwn(r,'done')?bool(r.done):false};}),style:pick(o.style,['bullet','check'])};
      case 'table': {obj(o,['type','columns','rows'],['type','columns','rows']);const columns=arr(o.columns,1,6).map(x=>text(x,80,true));return {type:o.type,columns,rows:arr(o.rows,0,20).map(r=>arr(r,columns.length,columns.length).map(x=>text(x,80)))};}
      case 'chart': {obj(o,['type','kind','unit','labels','series'],['type','kind','labels','series']); const labels=arr(o.labels,1,30).map(x=>text(x,80,true));return {type:o.type,kind:pick(o.kind,['bar','line']),...optText(o,'unit',24,true),labels,series:arr(o.series,1,3).map(r=>{obj(r,['name','values'],['name','values']);return {name:text(r.name,80,true),values:arr(r.values,labels.length,labels.length).map(num)};})};}
      case 'code': obj(o,['type','language','text'],['type','text']);return {type:o.type,...optText(o,'language',32,true),text:text(o.text,4000)};
      case 'diffStat': obj(o,['type','files'],['type','files']);return {type:o.type,files:arr(o.files,0,30).map(r=>{obj(r,['path','added','removed'],['path','added','removed']);return {path:text(r.path,256),added:int(r.added,0,9007199254740991),removed:int(r.removed,0,9007199254740991)};})};
      case 'progress': obj(o,['type','label','value'],['type','label','value']);num(o.value);if(o.value<0||o.value>1)fail();return {type:o.type,label:text(o.label,120,true),value:o.value};
      case 'callout': obj(o,['type','tone','text'],['type','tone','text']);return {type:o.type,tone:pick(o.tone,['info','warning','success']),text:text(o.text,500)};
      case 'link': {obj(o,['type','label','url'],['type','label','url']);if(typeof o.url!=='string'||scalar(o.url)>2048||unsafeLinkCharacters.test(o.url)||!/^https:\/\//i.test(o.url))fail(); let u;try {u=new URL(o.url);} catch {fail();} if(u.protocol!=='https:'||!u.hostname||u.username||u.password||o.url.split('/')[2].includes('@'))fail();return {type:o.type,label:text(o.label,120,true,1),url:o.url};}
      default: fail();
    }
  };
  const connector = o => {
    obj(o,['catalogId','reason'],['catalogId','reason']);
    const catalogId=id(o.catalogId,/^[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+${'$'}/);
    if(catalogId.length>256) fail();
    const reason=text(o.reason,32768,true,1);
    if(scalar(reason)>500) fail();
    return {catalogId,reason};
  };
  obj(input,['v','id','title','body','ask','connector'],['v','id','title','body']);
  if(input.v!==1) fail('version');
  if(Object.hasOwn(input,'connector')&&Object.hasOwn(input,'ask')) fail();
  return {v:1,id:id(input.id,/^[a-z0-9-]{1,48}${'$'}/),title:text(input.title,120,true,1),body:arr(input.body,0,40).map(node),...(Object.hasOwn(input,'ask')?{ask:ask(input.ask)}:{}),...(Object.hasOwn(input,'connector')?{connector:connector(input.connector)}:{})};
}
"""

    const val CONNECTOR_SEARCH: String = """const genUiConnectorSearchTool = {
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
    if (!/^•+${'$'}/.test(match[1])) return true;
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
    message: 'The connector catalogue could not be loaded right now. Try again in a moment.', matches: []});
  const catalogueOff = () => ({status: 'catalogue_off',
    message: 'The connector catalogue is off. Ask the person to tap Turn on in this step, then search again.', matches: []});
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
        || typeof bridge.bearer !== 'string' || !/^[a-fA-F0-9]{64}${'$'}/.test(bridge.bearer)) {
      return unavailable();
    }
    const endpoint = /^http:\/\/127\.0\.0\.1:([1-9][0-9]{0,4})\/find-connectors${'$'}/.exec(bridge.endpoint);
    if (!endpoint || Number(endpoint[1]) > 65535) return unavailable();
    bridge = {port: Number(endpoint[1]), bearer: bridge.bearer};
  } catch (error) {
    return error && error.code === 'ENOENT' ? notLoaded() : unavailable();
  } finally {
    if (fd !== undefined) fs.closeSync(fd);
  }
  const validate = value => {
    if (!value || typeof value !== 'object') throw new Error('invalid');
    if (value.status === 'catalogue_not_loaded' || value.status === 'catalogue_off'
        || value.status === 'unavailable') {
      const expected = value.status === 'catalogue_not_loaded' ? notLoaded()
        : value.status === 'catalogue_off' ? catalogueOff() : unavailable();
      if (!object(value, ['status','message','matches']) || value.message !== expected.message
          || !Array.isArray(value.matches) || value.matches.length) throw new Error('invalid');
      return expected;
    }
    if (!object(value, ['status','matches']) || value.status !== 'ok'
        || !Array.isArray(value.matches) || value.matches.length > args.limit) throw new Error('invalid');
    return {status: 'ok', matches: value.matches.map(match => {
      if (!object(match, ['catalogId','name','description','runtime','needsSignIn','connected'])
          || typeof match.catalogId !== 'string' || match.catalogId.length > 256
          || !/^[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+${'$'}/.test(match.catalogId)
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
"""

    const val SERVER: String = """;(() => {
const fs = require('node:fs');
const { TextDecoder } = require('node:util');
const decoder = new TextDecoder('utf-8', { fatal: true });
const versions = ['2025-11-25', '2025-06-18', '2025-03-26', '2024-11-05'];
const maxFrameBytes = 65536;
let initialized = false;
let negotiated = false;

const str = (maxLength, minLength = 0) => ({type: 'string', minLength, maxLength});
const enumeration = (...values) => ({type: 'string', enum: values});
const obj = (properties, required = Object.keys(properties)) =>
  ({type: 'object', properties, required, additionalProperties: false});
const arr = (items, maxItems, minItems = 0) =>
  ({type: 'array', items, minItems, maxItems});
const num = {type: 'number'};
const bool = {type: 'boolean'};
const option = obj({id: {...str(32, 1), pattern: '^[a-zA-Z0-9_-]{1,32}${'$'}'},
  label: str(120, 1), detail: str(200)}, ['id', 'label']);
const fieldProperties = {
  id: {...str(32, 1), pattern: '^[a-zA-Z0-9_]{1,32}${'$'}'},
  label: str(120, 1), required: bool, placeholder: str(120)
};
const field = {oneOf: [
  ...['text', 'multiline'].map(type => obj({...fieldProperties,
    type: enumeration(type), default: str(2000)}, ['id', 'label', 'type'])),
  obj({...fieldProperties, type: enumeration('number'), default: num,
    min: num, max: num}, ['id', 'label', 'type']),
  obj({...fieldProperties, type: enumeration('toggle'), default: bool},
    ['id', 'label', 'type']),
  obj({...fieldProperties, type: enumeration('select'), default: str(32, 1),
    options: arr(option, 20, 2)}, ['id', 'label', 'type', 'options']),
  obj({...fieldProperties, type: enumeration('date'),
    default: {...str(10, 10), pattern: '^\\d{4}-\\d{2}-\\d{2}${'$'}'}},
    ['id', 'label', 'type'])
]};
const node = {oneOf: [
  obj({type: enumeration('text'), text: str(2000)}),
  obj({type: enumeration('keyValue'), rows: arr(obj({key: str(60, 1),
    value: str(200)}), 20)}),
  obj({type: enumeration('list'), style: enumeration('bullet', 'check'),
    items: arr(obj({text: str(2000), done: bool}, ['text']), 30)}),
  obj({type: enumeration('table'), columns: arr(str(80), 6, 1),
    rows: arr(arr(str(80), 6, 1), 20)}),
  obj({type: enumeration('chart'), kind: enumeration('bar', 'line'), unit: str(24),
    labels: arr(str(80), 30, 1), series: arr(obj({name: str(80),
      values: arr(num, 30, 1)}), 3, 1)}, ['type', 'kind', 'labels', 'series']),
  obj({type: enumeration('code'), language: str(32), text: str(4000)},
    ['type', 'text']),
  obj({type: enumeration('diffStat'), files: arr(obj({path: str(256),
    added: {type: 'integer', minimum: 0, maximum: 9007199254740991},
    removed: {type: 'integer', minimum: 0, maximum: 9007199254740991}}), 30)}),
  obj({type: enumeration('progress'), label: str(120),
    value: {type: 'number', minimum: 0, maximum: 1}}),
  obj({type: enumeration('callout'), tone: enumeration('info', 'warning', 'success'),
    text: str(500)}),
  obj({type: enumeration('link'), label: str(120, 1),
    url: {...str(2048, 1), pattern: '^https://'}})
]};
const inputSchema = {...obj({v: {type: 'integer', const: 1},
  id: {...str(48, 1), pattern: '^[a-z0-9-]{1,48}${'$'}'}, title: str(120, 1),
  body: arr(node, 40), ask: {oneOf: [
    obj({kind: enumeration('choice'), options: arr(option, 8, 2), multi: bool},
      ['kind', 'options']),
    obj({kind: enumeration('form'), fields: arr(field, 12, 1), submitLabel: str(24)},
      ['kind', 'fields']),
    obj({kind: enumeration('confirm'), confirmLabel: str(24), cancelLabel: str(24),
      tone: enumeration('normal', 'danger')}, ['kind']),
    obj({kind: enumeration('photo'), purpose: str(200, 1),
      max: {type: 'integer', minimum: 1, maximum: 4}}, ['kind', 'purpose'])
  ]}, connector: obj({
    catalogId: {...str(256, 1), pattern: '^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+${'$'}'},
    reason: str(500, 1)
  })}, ['v', 'id', 'title', 'body']), not: {required: ['connector', 'ask']}};
const tool = {
  name: 'show',
  description: 'Offer a native card in OpenCode Mobile. Plain text only. '
    + 'Do not request credentials or secrets. IDs must be unique within a card. '
    + 'Table rows must match column count and chart values must match labels. '
    + 'Maximum compact JSON is 32768 UTF-8 bytes. This returns immediately; '
    + 'for a question, end your turn and wait for a normal user answer message. '
    + 'Card confirmation is not permission to execute commands. '
    + 'First call find_connectors to get a real catalogId; never invent IDs. '
    + 'To suggest an MCP connector, use connector with that catalogId '
    + '(namespace/name) and a plain reason, without ask. Never supply connector '
    + 'URLs, commands, headers or credentials. A suggestion does not connect it; '
    + 'the person must review the catalog entry and choose Connect.',
  inputSchema,
  annotations: {readOnlyHint: true, destructiveHint: false, idempotentHint: false,
    openWorldHint: false}
};
const record = value => value !== null && typeof value === 'object'
  && !Array.isArray(value);
function enabled() {
  let fd;
  try {
    fd = fs.openSync(enabledMarkerPath, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW
      | fs.constants.O_NONBLOCK);
    const stat = fs.fstatSync(fd);
    if (!stat.isFile() || stat.size !== 8) return false;
    const bytes = Buffer.alloc(9);
    return fs.readSync(fd, bytes, 0, 9, 0) === 8
      && bytes.subarray(0, 8).equals(Buffer.from('enabled\n'));
  } catch (_) {
    return false;
  } finally {
    if (fd !== undefined) fs.closeSync(fd);
  }
}
const error = (id, code, message) => ({jsonrpc: '2.0', id, error: {code, message}});
const result = (id, value) => ({jsonrpc: '2.0', id, result: value});
const failedCall = id => result(id, {isError: true, content: [{type: 'text',
  text: 'Agent card unavailable or invalid.'}]});
async function send(value) {
  await new Promise((resolve, reject) => {
    process.stdout.write(JSON.stringify(value) + '\n', err => err ? reject(err) : resolve());
  });
}
async function frame(bytes) {
  let request;
  try { request = JSON.parse(decoder.decode(bytes)); }
  catch (_) { await send(error(null, -32700, 'Parse error')); return; }
  if (!record(request) || request.jsonrpc !== '2.0'
      || typeof request.method !== 'string'
      || (request.params !== undefined && !record(request.params))) {
    await send(error(null, -32600, 'Invalid request')); return;
  }
  const hasId = Object.hasOwn(request, 'id');
  if (!hasId) {
    if (request.method === 'notifications/initialized' && negotiated) initialized = true;
    return;
  }
  const id = request.id;
  if (!((typeof id === 'string' && id.length <= 256)
      || (typeof id === 'number' && Number.isFinite(id)))) {
    await send(error(null, -32600, 'Invalid request')); return;
  }
  const params = request.params;
  if (request.method === 'initialize') {
    if (negotiated || !record(params) || typeof params.protocolVersion !== 'string'
        || !record(params.capabilities) || !record(params.clientInfo)
        || typeof params.clientInfo.name !== 'string'
        || typeof params.clientInfo.version !== 'string') {
      await send(error(id, -32602, 'Invalid params')); return;
    }
    negotiated = true;
    await send(result(id, {
      protocolVersion: versions.includes(params.protocolVersion)
        ? params.protocolVersion : versions[0],
      capabilities: {tools: {listChanged: false}},
      serverInfo: {name: 'oc-ui', version: '1.0.0'},
      instructions: 'Cards return immediately. End your turn after asking and wait for '
        + 'a normal user message tagged [oc-ui answer <id>]. Never ask for secrets.'
    }));
  } else if (request.method === 'ping') {
    await send(result(id, {}));
  } else if (!initialized) {
    await send(error(id, -32000, 'Server not initialized'));
  } else if (request.method === 'tools/list') {
    await send(result(id, {tools: [tool, genUiConnectorSearchTool]}));
  } else if (request.method === 'tools/call') {
    if (!record(params) || !['show', 'find_connectors'].includes(params.name)
        || !record(params.arguments)) {
      await send(failedCall(id)); return;
    }
    if (!enabled()) { await send(failedCall(id)); return; }
    if (params.name === 'find_connectors') {
      let args;
      try { args = validateGenUiConnectorSearchArguments(params.arguments); }
      catch (_) { await send(failedCall(id)); return; }
      let found;
      try { found = await findGenUiConnectors(args); }
      catch (_) { await send(failedCall(id)); return; }
      await send(result(id, {content: [{type: 'text', text: JSON.stringify(found)}],
        structuredContent: found}));
      return;
    }
    try { normalizeGenUiCard(params.arguments); }
    catch (_) { await send(failedCall(id)); return; }
    await send(result(id, {content: [{type: 'text', text:
      'Card accepted for display in OpenCode Mobile. If it asks a question, end your turn '
      + 'and wait for the person\'s next message tagged [oc-ui answer <id>]. '
      + 'This call does not return their answer.'}]}));
  } else {
    await send(error(id, -32601, 'Method not found'));
  }
}
async function main() {
  let chunks = [];
  let length = 0;
  let overflow = false;
  for await (const chunk of process.stdin) {
    let start = 0;
    while (start < chunk.length) {
      const newline = chunk.indexOf(10, start);
      const end = newline === -1 ? chunk.length : newline;
      if (!overflow) {
        const size = end - start;
        if (length + size > maxFrameBytes) {
          overflow = true; chunks = []; length = 0;
        } else if (size !== 0) {
          chunks.push(chunk.subarray(start, end)); length += size;
        }
      }
      if (newline === -1) break;
      if (overflow) await send(error(null, -32700, 'Frame too large'));
      else await frame(Buffer.concat(chunks, length));
      chunks = []; length = 0; overflow = false; start = newline + 1;
    }
  }
  if (overflow) await send(error(null, -32700, 'Frame too large'));
  else if (length) await frame(Buffer.concat(chunks, length));
}
// Protocol-only stdout and no raw exceptions/input on stderr, including EPIPE.
process.stdout.on('error', () => process.exit(1));
main().catch(() => process.exit(1));
})();
"""
}
