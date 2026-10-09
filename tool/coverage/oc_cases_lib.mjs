// Case building for the OpenCode families (oc1_*, oc2_*): a case is the wire
// payload a server sends, assembled from group values. `use(group, value)`
// records that the value fills the named schema group and returns the value
// with the group's discriminator (`type`, `role`, `status`, `name` ...) added,
// so a case reads like the wire JSON while the generator knows which fields
// each case covers.
import { loadContract, walker } from './openapi_walk.mjs';
import { buildFamily } from './paseo_cases_lib.mjs';

export function familyBuilder(contractFile, groupSpecs) {
  const S = loadContract(contractFile);
  const w = walker(S);
  const schema = {};
  const discriminators = {};
  for (const spec of groupSpecs) {
    const { name, from = name, stop = [], extra } = spec;
    if (extra) { schema[name] = extra; continue; }
    if (!S[from]) throw new Error(`no schema ${from} in ${contractFile}`);
    schema[name] = w.group(from, stop);
    const props = w.deref(S[from])?.properties ?? {};
    discriminators[name] = Object.fromEntries(
      Object.entries(props)
        .map(([k, v]) => [k, w.deref(v)])
        .filter(([, v]) => v?.enum?.length === 1)
        .map(([k, v]) => [k, v.enum[0]]),
    );
  }
  function kase(id, build, meta = {}) {
    const parts = [];
    const use = (group, value, opts = {}) => {
      if (!schema[group]) throw new Error(`case ${id}: unknown group ${group}`);
      parts.push({ group, value, ...opts });
      return { ...discriminators[group], ...value };
    };
    const payload = build(use);
    return { id, payload, parts, ...meta };
  }
  return { schema, kase, S };
}

export function finishFamily({ name, outDir, schema, cases, problems, source, writeFileSync, mkdirSync, excluded = {} }) {
  const cut = Object.fromEntries(Object.entries(schema).map(([g, f]) => [g, Object.fromEntries(Object.entries(f).filter(([p]) => !(`${g}.${p}` in excluded)))]));
  const family = buildFamily({ schema: cut, cases, problems });
  mkdirSync(outDir, { recursive: true });
  const out = Object.fromEntries(Object.entries(schema).map(([g, f]) => [g, Object.entries(f).map(([path, v]) => ({ path, kind: v.kind }))]));
  writeFileSync(`${outDir}/${name}_samples.json`, JSON.stringify({ source, schema: out, excluded, cases: family.cases }, null, 2));
  console.log(`${name}: ${family.cases.length} cases, ${Object.values(out).flat().length} schema fields`);
}
