import { chmodSync, existsSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';

const [dir, agentRoot, ...candidates] = process.argv.slice(2);
const read = (path) => {
  try {
    const value = JSON.parse(readFileSync(path, 'utf8'));
    return value && typeof value === 'object' ? value : null;
  } catch {
    return null;
  }
};
const docs = candidates.map((path) => [path, read(path)]).filter(([, doc]) => doc);
const apiKey = (ids) => {
  for (const [, doc] of docs) {
    for (const id of ids) {
      const entry = doc[id];
      if (entry && typeof entry.key === 'string' && entry.key.trim()) return entry.key.trim();
    }
  }
  return null;
};
const put = (name, value) => {
  const file = `${dir}/${name}`;
  rmSync(file, { force: true });
  if (!value) return 0;
  writeFileSync(file, `${value}\n`, { mode: 0o600 });
  chmodSync(file, 0o600);
  return 1;
};
const quote = (text) => `'${String(text).replaceAll("'", "'\\''")}'`;

const glm = put('glm.key', apiKey(['zai-coding-plan']));
const minimax = put('minimax.key', apiKey(['minimax-coding-plan', 'minimax']));

let env = '';
let codex = 'none';
const oauth = (entry) => entry && entry.type === 'oauth' && typeof entry.access === 'string';
const own = docs.find(([, doc]) => oauth(doc.openai) || oauth(doc.codex));
if (own) {
  env = `export OCMN_QUOTA_AUTH_FILE=${quote(own[0])}\nexport OCMN_QUOTA_AUTH_FORMAT=opencode\n`;
  codex = 'opencode';
} else if (existsSync(agentRoot)) {
  for (const name of readdirSync(agentRoot).sort()) {
    const file = `${agentRoot}/${name}/codex/auth.json`;
    const doc = read(file);
    if (doc && doc.tokens && doc.tokens.access_token && doc.tokens.account_id) {
      env = `export OCMN_QUOTA_AUTH_FILE=${quote(file)}\nexport OCMN_QUOTA_AUTH_FORMAT=codex\n`;
      codex = 'agent';
      break;
    }
  }
}
rmSync(`${dir}/auth.env`, { force: true });
writeFileSync(`${dir}/auth.env`, env, { mode: 0o600 });
console.log(`qc keys=ok codex=${codex} glm=${glm} minimax=${minimax}`);
