// Deterministic 16-bit WAV SFX kit — zero downloads, per the skill's
// no-silent-delivery rule. Regenerate any time with `npm run sfx`.
import { writeFileSync, mkdirSync } from "node:fs";

const SR = 44100;
const wav = (samples) => {
  const data = Buffer.alloc(samples.length * 2);
  samples.forEach((s, i) =>
    data.writeInt16LE(Math.max(-1, Math.min(1, s)) * 32767, i * 2),
  );
  const h = Buffer.alloc(44);
  h.write("RIFF", 0); h.writeUInt32LE(36 + data.length, 4); h.write("WAVE", 8);
  h.write("fmt ", 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20);
  h.writeUInt16LE(1, 22); h.writeUInt32LE(SR, 24); h.writeUInt32LE(SR * 2, 28);
  h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34);
  h.write("data", 36); h.writeUInt32LE(data.length, 40);
  return Buffer.concat([h, data]);
};
const seconds = (s) => Math.floor(SR * s);
const env = (i, n, a = 0.01, r = 0.6) => {
  const t = i / n, atk = a, rel = 1 - r;
  return t < atk ? t / atk : t > rel ? (1 - t) / (1 - rel) : 1;
};
let seed = 42;
const rand = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff) * 2 - 1;

const make = {
  "whoosh-in": () => { const n = seconds(0.5); let lp = 0; return Array.from({ length: n }, (_, i) => { const cut = 0.02 + 0.5 * (i / n); lp += cut * (rand() - lp); return lp * env(i, n, 0.5, 0.85) * 0.8; }); },
  "whoosh-out": () => { const n = seconds(0.45); let lp = 0; return Array.from({ length: n }, (_, i) => { const cut = 0.5 - 0.45 * (i / n); lp += cut * (rand() - lp); return lp * env(i, n, 0.02, 0.5) * 0.7; }); },
  click: () => { const n = seconds(0.06); return Array.from({ length: n }, (_, i) => Math.sin(2 * Math.PI * 1800 * (i / SR)) * env(i, n, 0.005, 0.15) * 0.5); },
  tick: () => { const n = seconds(0.04); return Array.from({ length: n }, (_, i) => Math.sin(2 * Math.PI * 2600 * (i / SR)) * env(i, n, 0.003, 0.1) * 0.35); },
  pop: () => { const n = seconds(0.12); return Array.from({ length: n }, (_, i) => { const f = 900 - 600 * (i / n); return Math.sin(2 * Math.PI * f * (i / SR)) * env(i, n, 0.01, 0.3) * 0.6; }); },
  "bass-hit": () => { const n = seconds(0.6); return Array.from({ length: n }, (_, i) => { const f = 110 - 60 * (i / n); return (Math.sin(2 * Math.PI * f * (i / SR)) + 0.3 * Math.sin(2 * Math.PI * f * 2 * (i / SR))) * env(i, n, 0.005, 0.85) * 0.8; }); },
  riser: () => { const n = seconds(1.2); let ph = 0; return Array.from({ length: n }, (_, i) => { const f = 180 + 640 * (i / n) ** 2; ph += (2 * Math.PI * f) / SR; return (Math.sin(ph) * 0.5 + rand() * 0.12) * (i / n) * 0.7; }); },
};

mkdirSync("public/sfx", { recursive: true });
for (const [name, fn] of Object.entries(make)) {
  writeFileSync(`public/sfx/${name}.wav`, wav(fn()));
  console.log(`public/sfx/${name}.wav`);
}
