// Reads field lists out of an OpenAPI contract (components.schemas) for the
// OpenCode coverage families. Handles $ref, allOf, anyOf/oneOf (objects are
// merged; the single-value `type` discriminator is skipped), nullable unions,
// arrays (`path[]`) and free-form objects (one `any` leaf).
//
// Returns {path: {kind, values?}} where kind is string|number|boolean|enum|any.
import fs from 'node:fs';

export function loadContract(file) {
  const doc = JSON.parse(fs.readFileSync(file, 'utf8'));
  return doc.components.schemas;
}

export function walker(S) {
  const refName = (r) => r.slice(r.lastIndexOf('/') + 1);

  function deref(node) {
    while (node && node.$ref) node = S[refName(node.$ref)];
    return node;
  }

  const isNull = (n) => n && (n.type === 'null' || (n.enum && n.enum.length === 1 && n.enum[0] === null));

  /** Flattens `node` into out, prefixed by path. `stop`: schema names kept as one `any` leaf. */
  function flatten(node, path, out, stop, seen = []) {
    if (!node) return;
    if (node.$ref) {
      const name = refName(node.$ref);
      if (stop.has(name)) { out[path] = { kind: 'any' }; return; }
      if (seen.includes(name)) { out[path] = { kind: 'any' }; return; }
      return flatten(S[name], path, out, stop, [...seen, name]);
    }
    if (node.allOf) { for (const n of node.allOf) flatten(n, path, out, stop, seen); return; }
    const alts = node.anyOf ?? node.oneOf;
    if (alts) {
      const real = alts.filter((a) => !isNull(a));
      const resolved = real.map(deref);
      const objs = resolved.every((r) => r && (r.properties || r.allOf));
      if (real.every((a) => a.$ref && stop.has(refName(a.$ref)))) { out[path] = { kind: 'any' }; return; }
      if (real.length === 1) return flatten(real[0], path, out, stop, seen);
      if (objs) {
        for (const a of real) flatten(a, path, out, stop, seen);
        // A discriminator that tells the variants apart is itself a field.
        const tags = {};
        for (const r of resolved) {
          for (const [k, v] of Object.entries(r.properties ?? {})) {
            const d = deref(v);
            if (d?.enum?.length === 1) (tags[k] ??= new Set()).add(d.enum[0]);
          }
        }
        for (const [k, vals] of Object.entries(tags)) {
          if (vals.size > 1) out[path ? `${path}.${k}` : k] = { kind: 'enum', values: [...vals] };
        }
        return;
      }
      // union of unlike things (string | object ...): one leaf
      const enums = resolved.every((r) => r && r.enum);
      if (enums) { out[path] = { kind: 'enum', values: resolved.flatMap((r) => r.enum) }; return; }
      out[path] = { kind: 'any' };
      return;
    }
    if (node.enum) {
      if (node.enum.length > 1) out[path] = { kind: 'enum', values: node.enum };
      return; // a single value is a discriminator
    }
    if (node.const !== undefined) return;
    if (node.properties) {
      for (const [k, v] of Object.entries(node.properties)) flatten(v, path ? `${path}.${k}` : k, out, stop, seen);
      return;
    }
    if (node.type === 'array') { flatten(node.items, `${path}[]`, out, stop, seen); return; }
    if (node.type === 'object' || node.additionalProperties !== undefined || Object.keys(node).length === 0 || node.type === undefined) {
      out[path] = { kind: 'any' };
      return;
    }
    const kind = node.type === 'integer' ? 'number' : node.type;
    out[path] = { kind };
  }

  /** One group: the named schema's fields. */
  function group(name, stop = []) {
    const out = {};
    flatten(S[name], '', out, new Set(stop), [name]);
    return out;
  }
  return { group, deref, flatten };
}
