// Gate 1 inventory: reads Paseo's own protocol schema and writes one sample
// tool_call per step type with a unique marker in every field, so a test can
// check each field reaches the screen. Nothing here is hand-written per field.
//   node tool/coverage/paseo_tool_samples.mjs <paseo protocol dist/messages.js> <out.json>
import { pathToFileURL } from 'node:url';
import { writeFileSync } from 'node:fs';

const [, , protocolPath, outPath] = process.argv;
const { AgentTimelineItemPayloadSchema } = await import(pathToFileURL(protocolPath).href);

const def = (s) => s._zod.def;
function unwrap(s) {
  for (;;) {
    const d = def(s);
    if (['optional', 'nullable', 'default', 'prefault', 'readonly', 'catch'].includes(d.type)) s = d.innerType;
    else if (d.type === 'lazy') s = d.getter();
    else if (d.type === 'pipe') s = d.in;
    else return s;
  }
}
const toolCall = def(AgentTimelineItemPayloadSchema).options
  .map(unwrap)
  .flatMap((o) => (def(o).type === 'union' ? def(o).options.map(unwrap) : [o]))
  .find((o) => def(o).type === 'object' && def(def(o).shape.type).values?.includes('tool_call'));
const detailUnion = unwrap(def(toolCall).shape.detail);

let n = 7000;
const fields = [];
function sample(schema, path) {
  const s = unwrap(schema);
  const d = def(s);
  switch (d.type) {
    case 'string': { const m = `MK${++n}`; fields.push({ path, kind: 'string', marker: m }); return m; }
    case 'number': case 'int': { const m = ++n; fields.push({ path, kind: 'number', marker: String(m) }); return m; }
    case 'boolean': fields.push({ path, kind: 'boolean', marker: null }); return true;
    case 'literal': return d.values[0];
    case 'enum': { const v = Object.values(d.entries).at(-1); fields.push({ path, kind: 'enum', marker: String(v) }); return v; }
    case 'array': return [sample(d.element, `${path}[]`)];
    case 'object': return Object.fromEntries(Object.entries(d.shape).map(([k, v]) => [k, sample(v, path ? `${path}.${k}` : k)]));
    case 'union': return sample(d.options.find((o) => def(unwrap(o)).type === 'string') ?? d.options.at(-1), path);
    default: { const m = `MK${++n}`; fields.push({ path, kind: d.type, marker: m }); return m; }
  }
}
const variants = def(detailUnion).options.map((o) => {
  const type = def(def(o).shape.type).values[0];
  fields.length = 0;
  const detail = sample(o, '');
  return { type, detail, fields: [...fields] };
});
writeFileSync(outPath, JSON.stringify({ source: protocolPath, variants }, null, 2));
console.log(variants.map((v) => `${v.type}: ${v.fields.length} fields`).join('\n'));
