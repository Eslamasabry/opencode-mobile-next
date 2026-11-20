package io.github.eslamasabry.opencode_mobile

/**
 * The app's own preload for the agents' helper (`node --require`). It remembers what esbuild built for the helper's
 * built-in plug-ins, so the next start skips that work. The bytes are ours: written into the phone's Linux at every
 * launch, never downloaded, and no package file is edited. It stores only esbuild's output, and every step in it is
 * guarded, so a failure inside it falls back to the real esbuild call. Kept free of the dollar sign so the text is
 * the same everywhere it is copied (the launch script writes it from a quoted here-document).
 */
internal object EsbuildCacheHook {
    const val SOURCE: String = """'use strict';
// OpenCode Mobile preload: remembers what esbuild built, on disk, so the next start of the agents' helper
// does not build the same built-in plug-ins again. Written by the app at launch; never downloaded.
// It stores ONLY esbuild's output (code, map, metafile). No environment, prompt, credential or log.
// Total by design: every step is guarded, and any failure falls back to the real esbuild call.
try {
  if (process.env.OC_ESBUILD_CACHE_DIR) install(String(process.env.OC_ESBUILD_CACHE_DIR));
} catch (ignored) {
  // A cache that cannot start must never stop the helper from starting.
}

function install(dir) {
  const Module = require('node:module');
  const crypto = require('node:crypto');
  const fs = require('node:fs');
  const path = require('node:path');
  const salt = String(process.env.OC_ESBUILD_CACHE_SALT || '') + '|' + process.version + '|' + process.arch;
  const sha = function () {
    const h = crypto.createHash('sha256');
    for (let i = 0; i < arguments.length; i++) { h.update(arguments[i]); h.update('\0'); }
    return h.digest('hex');
  };
  const trees = new Map();
  const tree = function (root) {
    if (trees.has(root)) return trees.get(root);
    const h = crypto.createHash('sha256');
    const walk = function (d) {
      const names = fs.readdirSync(d, { withFileTypes: true }).sort(function (a, b) { return a.name < b.name ? -1 : 1; });
      for (const e of names) {
        if (e.name === 'node_modules') continue;
        const p = path.join(d, e.name);
        if (e.isDirectory()) walk(p);
        else if (e.isFile()) { h.update(path.relative(root, p)); h.update('\0'); h.update(fs.readFileSync(p)); h.update('\0'); }
      }
    };
    walk(root);
    const out = h.digest('hex');
    trees.set(root, out);
    return out;
  };
  const drop = function (file) { try { fs.unlinkSync(file); } catch (ignored) { /* already gone */ } };
  // A stored entry, or null. An entry that cannot be read, parsed or verified is deleted and ignored.
  const lookup = function (file, field) {
    let text;
    try { text = fs.readFileSync(file, 'utf8'); } catch (ignored) { return null; }
    try {
      const hit = JSON.parse(text);
      if (hit && typeof hit[field] === 'string' && hit.sum === sha(hit[field])) return hit;
    } catch (ignored) { /* corrupt or half written */ }
    drop(file);
    return null;
  };
  const store = function (file, value) {
    const tmp = file + '.' + process.pid + '.' + Date.now() + '.tmp';
    try {
      fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
      fs.writeFileSync(tmp, JSON.stringify(value), { mode: 0o600 });
      fs.renameSync(tmp, file);
    } catch (ignored) {
      drop(tmp);
    }
  };
  const optionsKey = function (options) {
    return JSON.stringify(options, function (k, v) { return k === 'plugins' ? undefined : v; });
  };
  const buildKey = function (options) {
    try {
      if (options && options.write === false && Array.isArray(options.entryPoints) && options.entryPoints.length === 1) {
        const entry = String(options.entryPoints[0]);
        return sha(salt, 'build', optionsKey(options), tree(path.dirname(path.dirname(entry))));
      }
    } catch (ignored) { /* no key: build for real */ }
    return null;
  };
  const wrap = function (real) {
    const build = async function (options) {
      const key = buildKey(options);
      let file = null;
      if (key) {
        try {
          file = path.join(dir, 'b-' + key + '.json');
          const hit = lookup(file, 'text');
          if (hit && hit.metafile && typeof hit.metafile === 'object' && typeof hit.path === 'string') {
            return { errors: [], warnings: [], metafile: hit.metafile, outputFiles: [{ path: hit.path, text: hit.text, contents: Buffer.from(hit.text) }] };
          }
        } catch (ignored) { /* fall through to the real build */ }
      }
      const result = await real.build.apply(real, arguments);
      try {
        if (key && file && result.outputFiles && result.outputFiles.length === 1 && !result.errors.length && !result.warnings.length) {
          const text = result.outputFiles[0].text;
          store(file, { path: result.outputFiles[0].path, text: text, metafile: result.metafile, sum: sha(text) });
        }
      } catch (ignored) { /* the build is still good */ }
      return result;
    };
    const transform = async function (code, options) {
      let file = null;
      try {
        if (typeof code === 'string') {
          file = path.join(dir, 't-' + sha(salt, 'transform', optionsKey(options || {}), code) + '.json');
          const hit = lookup(file, 'code');
          if (hit) return { code: hit.code, map: hit.map || '', warnings: [], legalComments: hit.legalComments };
        }
      } catch (ignored) { file = null; }
      const result = await real.transform.apply(real, arguments);
      try {
        if (file && !result.warnings.length && typeof result.code === 'string') {
          store(file, { code: result.code, map: result.map, legalComments: result.legalComments, sum: sha(result.code) });
        }
      } catch (ignored) { /* the transform is still good */ }
      return result;
    };
    return Object.assign({}, real, { build: build, transform: transform });
  };
  const wrappers = new WeakMap();
  const originalLoad = Module._load;
  Module._load = function (request) {
    const real = originalLoad.apply(this, arguments);
    if (request !== 'esbuild') return real;
    try {
      if (!real || typeof real.build !== 'function' || typeof real.transform !== 'function') return real;
      if (!wrappers.has(real)) wrappers.set(real, wrap(real));
      return wrappers.get(real);
    } catch (ignored) {
      return real;
    }
  };
}
"""
}
