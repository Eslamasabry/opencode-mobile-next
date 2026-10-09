// Shared by the coverage families (tools, items, permissions): reads field
// lists out of Paseo's zod protocol schema and validates hand-written cases
// against them. The field list always comes from the schema, never from the
// case files; the build FAILS when
//   - a schema field appears in no case (a new Paseo field must get a case),
//   - a case sets a field the schema does not have, or an enum value it lacks.
export const def = (s) => s._zod.def;

export function unwrap(s) {
  for (;;) {
    const d = def(s);
    if (['optional', 'nullable', 'default', 'prefault', 'readonly', 'catch'].includes(d.type)) s = d.innerType;
    else if (d.type === 'lazy') s = d.getter();
    else if (d.type === 'pipe') s = d.in;
    else return s;
  }
}

/** Flattens a union of unions into its object members. */
export function members(s) {
  const u = unwrap(s);
  return def(u).type === 'union' ? def(u).options.flatMap(members) : [u];
}

/** The literal a member's `field` is fixed to, if any. */
export function literalOf(obj, field) {
  const shape = def(obj).shape;
  return shape?.[field] ? def(unwrap(shape[field])).values?.[0] : undefined;
}

/** path -> {kind, values?}; unions and free records are one `any` leaf. */
export function walk(s, path, out) {
  const u = unwrap(s);
  const d = def(u);
  switch (d.type) {
    case 'object':
      for (const [k, v] of Object.entries(d.shape)) walk(v, path ? `${path}.${k}` : k, out);
      return;
    case 'array': walk(d.element, `${path}[]`, out); return;
    case 'literal': return;
    case 'enum': out[path] = { kind: 'enum', values: Object.values(d.entries) }; return;
    case 'union': out[path] = { kind: 'any' }; return;
    case 'record': case 'unknown': case 'any': out[path] = { kind: 'any' }; return;
    case 'int': out[path] = { kind: 'number' }; return;
    default: out[path] = { kind: d.type };
  }
}

export function objectFields(obj, skip = ['type']) {
  const fields = {};
  for (const [k, v] of Object.entries(def(obj).shape)) if (!skip.includes(k)) walk(v, k, fields);
  return fields;
}

function probeOf(v) {
  if (typeof v === 'number') return [String(v)];
  if (typeof v !== 'string') return [];
  const line = v.split('\n').map((l) => l.trim()).find((l) => l) ?? '';
  return line ? [line] : [];
}

/**
 * schema: group -> {path: {kind, values?}}
 * cases:  [{id, payload, parts: [{group, value, probes?}], ...meta}]
 * A part's value holds the group's fields (without `type`); `probes` maps a
 * path to what a person should read for a field whose value is reworded
 * (counts, enums); fields without an entry probe their own first line.
 * `ids` marks fields whose value is an id: they are checked as ids.
 */
export function buildFamily({ schema, cases, problems }) {
  const covered = new Set();
  const out = cases.map((c) => {
    const fields = [];
    for (const part of c.parts) {
      const spec = schema[part.group];
      if (!spec) { problems.push(`case ${c.id}: no group "${part.group}"`); continue; }
      const flat = [];
      const visit = (value, path) => {
        if (spec[path]?.kind === 'any') { flat.push({ path, value }); return; }
        if (Array.isArray(value)) { for (const v of value) visit(v, `${path}[]`); return; }
        if (value !== null && typeof value === 'object') {
          for (const [k, v] of Object.entries(value)) visit(v, path ? `${path}.${k}` : k);
          return;
        }
        flat.push({ path, value });
      };
      visit(part.value, '');
      const byPath = new Map();
      for (const { path, value } of flat) {
        if (!(path in spec)) { problems.push(`case ${c.id}: "${part.group}.${path}" is not in Paseo's schema`); continue; }
        const kind = spec[path].kind;
        if (kind === 'enum' && !spec[path].values.includes(value)) problems.push(`case ${c.id}: ${path}=${value} is not one of ${spec[path].values}`);
        covered.add(`${part.group}.${path}`);
        const entry = byPath.get(path) ?? { key: `${part.group}.${path}`, path, kind, probes: [], primary: (part.primary ?? []).includes(path) };
        if (kind === 'any' && value !== null && typeof value === 'object') {
          for (const [, v] of Object.entries(value)) if (typeof v === 'string') entry.probes.push(...probeOf(v));
        } else if (kind === 'any' && typeof value === 'string') entry.probes.push(...probeOf(value));
        else if (kind !== 'enum' && kind !== 'boolean') entry.probes.push(...probeOf(value));
        byPath.set(path, entry);
      }
      for (const path of part.primary ?? []) {
        if (!byPath.has(path)) problems.push(`case ${c.id}: "${part.group}.${path}" is marked primary but the case does not set it`);
      }
      for (const [path, probes] of Object.entries(part.probes ?? {})) {
        const entry = byPath.get(path);
        if (!entry) { problems.push(`case ${c.id}: probes for "${part.group}.${path}", which the case does not set`); continue; }
        entry.probes = probes;
      }
      fields.push(...byPath.values());
    }
    const { parts, ...rest } = c;
    return { ...rest, fields };
  });
  for (const [group, spec] of Object.entries(schema)) {
    for (const path of Object.keys(spec)) {
      if (!covered.has(`${group}.${path}`)) problems.push(`schema field "${group}.${path}" is in no case: add it to a case in tool/coverage/`);
    }
  }
  return {
    schema: Object.fromEntries(Object.entries(schema).map(([g, f]) => [g, Object.entries(f).map(([path, v]) => ({ path, kind: v.kind }))])),
    cases: out,
  };
}
