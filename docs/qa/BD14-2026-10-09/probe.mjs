// Offline shipped-package probe. No daemon, account, project or network access.
// Usage: node probe.mjs /absolute/path/to/node_modules
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, rmSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { pathToFileURL } from 'node:url';

const root = resolve(process.argv[2]);
const load = (relative) => import(pathToFileURL(join(root, relative)).href);
const serverRoot = join(root, '@getpaseo/server');
assert.equal(JSON.parse(readFileSync(join(serverRoot, 'package.json'))).version, '0.9.2');
const protocol = await load('@getpaseo/protocol/dist/messages.js');
const image = await load('@getpaseo/server/dist/server/server/agent/providers/provider-image-output.js');
const files = await load('@getpaseo/server/dist/server/server/file-explorer/service.js');
const types = protocol.SessionInboundMessageSchema.options.map((option) => option.shape.type.value);
assert(types.every((type) => typeof type === 'string'));
const relevant = types.filter((type) => /attachment|image|file|^fs\./i.test(type));
console.log('Inbound attachment/image/file operations:', JSON.stringify(relevant));
assert(!types.some((type) => /attachment|image/i.test(type)));
for (const type of ['read_attachment_request', 'attachment.read.request']) {
  assert.equal(protocol.SessionInboundMessageSchema.safeParse({type, id: 'a'.repeat(64), requestId: 'fixture'}).success, false);
}
assert(types.includes('file_explorer_request'));
assert(types.includes('file_download_token_request'));
assert.equal(protocol.FileExplorerRequestSchema.safeParse({
  type: 'file_explorer_request', mode: 'file', requestId: 'fixture', id: 'a'.repeat(64),
}).success, false); // cwd/path are not an attachment ID lookup.

const bytes = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jxioAAAAASUVORK5CYII=', 'base64');
const hash = createHash('sha256').update(bytes).digest('hex');
const materialized = image.materializeProviderImage({data: bytes.toString('base64'), mimeType: 'image/png'});
const owned = dirname(materialized.path);
try {
  assert.equal(dirname(owned), tmpdir());
  assert(basename(owned).startsWith('paseo-attachments-'));
  assert.equal(basename(materialized.path), `${hash}.png`);
  assert.deepEqual(readFileSync(materialized.path), bytes);
  const markdown = image.renderProviderImageOutputAsAssistantMarkdown(materialized).text;
  assert(markdown.startsWith('![Image](file://'));
  assert(image.isProviderImageMarkdown(markdown));
  assert(!image.isProviderImageMarkdown(`![Image](${hash})`));
  const file = await files.readExplorerFile({root: owned, relativePath: `${hash}.png`});
  assert.equal(file.kind, 'image');
  assert.deepEqual(Buffer.from(file.content, 'base64'), bytes);
  await assert.rejects(files.readExplorerFile({root: owned, relativePath: hash}));
  console.log('Image filename: SHA-256(bytes) + .png; markdown target: full file:// path.');
  console.log('Known complete path reads fixture image; bare hash does not resolve.');
  console.log('PASS: no attachment-ID read operation in the shipped inbound union.');
} finally {
  // Delete only the private directory created by this fixture invocation.
  rmSync(owned, {recursive: true, force: true});
}
