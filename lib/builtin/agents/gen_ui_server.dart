import 'dart:convert';

import '../../domain/genui/gen_ui_validation_js.dart';

/// Zero-dependency MCP stdio helper, installed only at app-owned fixed paths.
///
/// The marker is an operational switch, not a boundary against a process already
/// allowed to write this account's files. Installation owns atomic marker writes.
String genUiServerScript({required String enabledMarkerPath}) {
  if (!enabledMarkerPath.startsWith('/') ||
      enabledMarkerPath.contains('\u0000') ||
      enabledMarkerPath.contains('\n') ||
      enabledMarkerPath.contains('\r')) {
    throw ArgumentError('Agent cards require an absolute marker path.');
  }
  return ''''use strict';
const enabledMarkerPath = ${jsonEncode(enabledMarkerPath)};
$genUiValidationJavascript
$_server
''';
}

const _server = r'''
;(() => {
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
const option = obj({id: {...str(32, 1), pattern: '^[a-zA-Z0-9_-]{1,32}$'},
  label: str(120, 1), detail: str(200)}, ['id', 'label']);
const fieldProperties = {
  id: {...str(32, 1), pattern: '^[a-zA-Z0-9_]{1,32}$'},
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
    default: {...str(10, 10), pattern: '^\\d{4}-\\d{2}-\\d{2}$'}},
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
const inputSchema = obj({v: {type: 'integer', const: 1},
  id: {...str(48, 1), pattern: '^[a-z0-9-]{1,48}$'}, title: str(120, 1),
  body: arr(node, 40), ask: {oneOf: [
    obj({kind: enumeration('choice'), options: arr(option, 8, 2), multi: bool},
      ['kind', 'options']),
    obj({kind: enumeration('form'), fields: arr(field, 12, 1), submitLabel: str(24)},
      ['kind', 'fields']),
    obj({kind: enumeration('confirm'), confirmLabel: str(24), cancelLabel: str(24),
      tone: enumeration('normal', 'danger')}, ['kind']),
    obj({kind: enumeration('photo'), purpose: str(200, 1),
      max: {type: 'integer', minimum: 1, maximum: 4}}, ['kind', 'purpose'])
  ]}}, ['v', 'id', 'title', 'body']);
const tool = {
  name: 'show',
  description: 'Offer a native card in OpenCode Mobile. Plain text only. '
    + 'Do not request credentials or secrets. IDs must be unique within a card. '
    + 'Table rows must match column count and chart values must match labels. '
    + 'Maximum compact JSON is 32768 UTF-8 bytes. This returns immediately; '
    + 'for a question, end your turn and wait for a normal user answer message. '
    + 'Card confirmation is not permission to execute commands.',
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
    await send(result(id, {tools: [tool]}));
  } else if (request.method === 'tools/call') {
    if (!record(params) || params.name !== 'show' || !record(params.arguments)) {
      await send(failedCall(id)); return;
    }
    if (!enabled()) { await send(failedCall(id)); return; }
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
''';
