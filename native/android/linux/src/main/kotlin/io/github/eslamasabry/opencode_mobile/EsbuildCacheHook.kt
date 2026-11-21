package io.github.eslamasabry.opencode_mobile

/**
 * The app's own preload for the agents' helper (`node --require`). It remembers what esbuild built for the helper's
 * built-in plug-ins, so the next start skips that work. The bytes are ours: written into the phone's Linux at every
 * launch, never downloaded, and no package file is edited. It stores only esbuild's output, and every step in it is
 * guarded, so a failure inside it falls back to the real esbuild call. It works only inside the helper's own
 * processes, caches only builds of the pinned install's built-in plug-ins, and takes itself out of NODE_OPTIONS at
 * once so no agent or project build inherits it. Kept free of the dollar sign so the text is the same everywhere it
 * is copied (the launch script writes it from a quoted here-document).
 */
internal object EsbuildCacheHook {
    const val SOURCE: String = """'use strict';
// OpenCode Mobile preload for the agents' helper. It remembers what esbuild built for the helper's own built-in
// plug-ins, on disk, so the next start does not build them again. Written by the app at launch; never downloaded.
// It stores ONLY esbuild's output (code and metafile) and checksums. No environment, prompt, credential or log.
// Total by design: every step is guarded, and any failure falls back to the real call, untouched.
// Scope: it works only inside the helper's own processes (its entry script lies in the pinned install), it caches only
// esbuild builds whose entry lies in that install's built-in plug-ins folder, and it removes itself from NODE_OPTIONS
// at once, putting itself back only for the helper's own node scripts and worker threads. Nothing else inherits it.
try {
  start();
} catch (ignored) {
  // A cache that cannot start must never stop the helper from starting.
}

function start() {
  const fs = require('node:fs');
  const path = require('node:path');
  const wt = require('node:worker_threads');
  const hookFile = __filename;
  const home = __dirname;
  const token = '--require "' + hookFile + '"';

  // 1. Take our own option out of the environment: restore exactly what was there before us.
  const ours = /(?: )?--require "[^"]*[/\\]oc-esbuild[/\\][^"/\\]+[/\\]hook\.cjs"(?: )?/;
  const current = process.env.NODE_OPTIONS;
  if (typeof current === 'string' && ours.test(current)) {
    const rest = current.replace(ours, function (all) { return all.length > 1 && all[0] === ' ' && all[all.length - 1] === ' ' ? ' ' : ''; });
    if (rest.trim() === '') delete process.env.NODE_OPTIONS;
    else process.env.NODE_OPTIONS = rest;
  }

  // 2. Only the helper's own processes go on: the pin's install folder is named in a file the app wrote beside us.
  const pin = fs.realpathSync(String(fs.readFileSync(path.join(home, 'pin-root'), 'utf8')).trim());
  const inside = function (root, file) { return file === root || file.indexOf(root + path.sep) === 0; };
  if (wt.isMainThread) {
    if (!process.argv[1] || !inside(pin, fs.realpathSync(process.argv[1]))) return;
  }
  // A worker thread only has this hook when one of the helper's own processes started it.
  install(home, pin, hookFile, token, inside);
}

function install(home, pin, hookFile, token, inside) {
  const Module = require('node:module');
  const crypto = require('node:crypto');
  const fs = require('node:fs');
  const path = require('node:path');
  const cp = require('node:child_process');
  const wt = require('node:worker_threads');
  const dir = path.join(home, 'cache');
  const salt = path.basename(home) + '|' + process.version + '|' + process.arch;
  const sha = function () {
    const h = crypto.createHash('sha256');
    for (let i = 0; i < arguments.length; i++) { h.update(arguments[i]); h.update('\0'); }
    return h.digest('hex');
  };

  // ---- the cache -------------------------------------------------------------------------------------------
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
  const lookup = function (file) {
    let text;
    try { text = fs.readFileSync(file, 'utf8'); } catch (ignored) { return null; }
    try {
      const hit = JSON.parse(text);
      if (hit && typeof hit.text === 'string' && hit.sum === sha(hit.text) && hit.metafile && typeof hit.metafile === 'object'
        && typeof hit.path === 'string') return hit;
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
  // The built-in plug-ins folder an entry lies in, or null: only a plug-in the pinned install itself ships is cached.
  const builtinRoot = function (options) {
    if (!options || options.write !== false || !Array.isArray(options.entryPoints) || options.entryPoints.length !== 1) return null;
    const given = options.entryPoints[0];
    if (typeof given !== 'string' || !path.isAbsolute(given)) return null;
    const entry = fs.realpathSync(given);
    const marker = path.sep + 'builtin-plugins' + path.sep;
    const at = entry.indexOf(marker, pin.length);
    if (!inside(pin, entry) || at < 0) return null;
    const root = entry.slice(0, at + marker.length - 1);
    if (options.absWorkingDir !== undefined) {
      if (typeof options.absWorkingDir !== 'string' || !path.isAbsolute(options.absWorkingDir)) return null;
      if (!inside(root, fs.realpathSync(options.absWorkingDir))) return null;
    }
    return root;
  };
  const buildKey = function (options) {
    try {
      const root = builtinRoot(options);
      if (!root) return null;
      return sha(salt, 'build', optionsKey(options), options.absWorkingDir || process.cwd(), tree(root));
    } catch (ignored) { /* no key: build for real */ }
    return null;
  };
  // Every file the build read must lie in the pinned install, which the pin covers; anything else is not stored.
  const inputsPinned = function (result, options) {
    const base = options.absWorkingDir || process.cwd();
    for (const name of Object.keys(result.metafile.inputs)) {
      if (!inside(pin, path.resolve(base, name))) return false;
    }
    return true;
  };
  const wrap = function (real) {
    const build = async function (options) {
      const key = buildKey(options);
      const file = key ? path.join(dir, 'b-' + key + '.json') : null;
      if (file) {
        try {
          const hit = lookup(file);
          if (hit) {
            return { errors: [], warnings: [], metafile: hit.metafile, outputFiles: [{ path: hit.path, text: hit.text, contents: Buffer.from(hit.text) }] };
          }
        } catch (ignored) { /* fall through to the real build */ }
      }
      const result = await real.build.apply(real, arguments);
      try {
        if (file && result.outputFiles && result.outputFiles.length === 1 && !result.errors.length && !result.warnings.length
          && inputsPinned(result, options)) {
          const text = result.outputFiles[0].text;
          store(file, { path: result.outputFiles[0].path, text: text, metafile: result.metafile, sum: sha(text) });
        }
      } catch (ignored) { /* the build is still good */ }
      return result;
    };
    return Object.assign({}, real, { build: build });
  };
  const wrappers = new WeakMap();
  const originalLoad = Module._load;
  Module._load = function (request) {
    const real = originalLoad.apply(this, arguments);
    if (request !== 'esbuild') return real;
    try {
      if (!real || typeof real.build !== 'function') return real;
      if (!wrappers.has(real)) wrappers.set(real, wrap(real));
      return wrappers.get(real);
    } catch (ignored) {
      return real;
    }
  };

  // ---- who inherits us: only the helper's own node scripts and its worker threads ------------------------------
  const withHook = function (env) {
    const out = Object.assign({}, env || process.env);
    out.NODE_OPTIONS = typeof out.NODE_OPTIONS === 'string' && out.NODE_OPTIONS !== '' ? token + ' ' + out.NODE_OPTIONS : token;
    return out;
  };
  const ownScript = function (file) {
    try {
      return typeof file === 'string' && ['.js', '.cjs', '.mjs'].some(function (e) { return file.endsWith(e); }) && inside(pin, fs.realpathSync(file));
    } catch (ignored) { return false; }
  };
  // [index of the options argument, or -1 when it must be appended]
  const optionsAt = function (args, from) {
    for (let i = from; i < args.length; i++) {
      if (args[i] !== null && typeof args[i] === 'object' && !Array.isArray(args[i])) return i;
    }
    return -1;
  };
  const armed = function (args, isFork) {
    const first = args[0];
    let script = null;
    if (isFork) script = typeof first === 'string' ? first : null;
    else if (first === process.execPath && Array.isArray(args[1])) script = args[1].find(function (a) { return ownScript(a); }) || null;
    if (!ownScript(script)) return null;
    const copy = Array.prototype.slice.call(args);
    const at = optionsAt(copy, 1);
    if (at < 0) {
      if (copy.length < 2) copy.push([]);
      copy.push({ env: withHook() });
    } else {
      copy[at] = Object.assign({}, copy[at], { env: withHook(copy[at].env) });
    }
    return copy;
  };
  const wrapChild = function (name, isFork) {
    const original = cp[name];
    cp[name] = function () {
      let changed = null;
      try { changed = armed(arguments, isFork); } catch (ignored) { changed = null; }
      return original.apply(this, changed || arguments);
    };
  };
  wrapChild('fork', true);
  wrapChild('spawn', false);

  const OriginalWorker = wt.Worker;
  class HelperWorker extends OriginalWorker {
    constructor(file, options) {
      let given = options;
      try {
        const execArgv = options && Array.isArray(options.execArgv) ? options.execArgv : process.execArgv;
        given = Object.assign({}, options, { execArgv: execArgv.concat(['--require', hookFile]) });
      } catch (ignored) { given = options; }
      super(file, given);
    }
  }
  wt.Worker = HelperWorker;
  try { Module.syncBuiltinESMExports(); } catch (ignored) { /* ES imports see the originals */ }
}
"""
}
