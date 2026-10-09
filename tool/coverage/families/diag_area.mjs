// Area "diag": the app's own diagnostics records and what leaves the phone.
// Families (each a schema plus cases):
//
//   diag_exit    Android's exit history over the oc/lifecycle channel, read by
//                the app's real bridge and drawn by the exit-history section.
//   diag_error   the errors the app kept (AppDiagnosticsController), drawn on
//                Report a problem (the list and the report text).
//   diag_crash   the opt-in crash store (fixed categories only) plus the
//                native crash record, drawn on Report a problem.
//   diag_perf    the performance timings, drawn by the Performance section.
//
// Timestamps in a case are written as "milliseconds before the test's now"
// (negative numbers) so the day words ("Today") stay true when it runs.
const none = (o) => Object.fromEntries(Object.keys(o).map((k) => [k, []]));
const flatPaths = (v, prefix = '', o = {}) => {
  for (const [k, x] of Object.entries(v)) {
    if (Array.isArray(x)) o[`${prefix}${k}[]`] = 1;
    else if (x && typeof x === 'object') flatPaths(x, `${prefix}${k}.`, o);
    else o[`${prefix}${k}`] = 1;
  }
  return o;
};
const mk = (id, kind, payload, parts) => ({
  id, kind, payload,
  parts: parts.map(([group, value, probes = {}]) => {
    const flat = Object.fromEntries(Object.keys(flatPaths(value)).map((k) => [k, []]));
    return { group, value, probes: { ...flat, ...probes } };
  }),
});

export function diagFamilies() {
  const out = {};

  // ---------------------------------------------------------------- exits
  const exitSchema = {
    wire: { supported: { kind: 'boolean' }, error: { kind: 'string' } },
    entry: {
      reason: { kind: 'number' }, subReason: { kind: 'number' }, importance: { kind: 'number' }, timestamp: { kind: 'number' },
      status: { kind: 'number' }, description: { kind: 'string' },
    },
  };
  const ex = (reason, importance, ago, description) => ({ reason, subReason: -1, importance, timestamp: -ago, status: 0, description });
  const exitCase = (id, wire, entries, wireProbes, entryProbes = []) =>
    mk(id, 'exit', { wire, entries }, [['wire', wire, wireProbes], ...entries.map((e, i) => ['entry', e, entryProbes[i] ?? {}])]);
  out.diag_exit = {
    schema: exitSchema, excluded: {}, source: 'android AppLifecycle.exitHistory (oc/lifecycle) read by lib/platform/app_exit.dart',
    cases: [
      exitCase('exit_problems', { supported: true },
        [ex(4, 100, 3600000, 'MARKER-EXIT-TEXT-1'), ex(3, 400, 7200000, 'MARKER-EXIT-TEXT-2'), ex(10, 100, 10800000, 'MARKER-EXIT-TEXT-3')],
        {},
        [
          { reason: ['Reason code 4'], importance: ['Importance 100'], timestamp: ['Today'] },
          { reason: ['Reason code 3'], importance: ['Importance 400'] },
          {},
        ]),
      exitCase('exit_none', { supported: true }, [], { supported: ['No unexpected closes recently.'] }),
      exitCase('exit_unsupported', { supported: false }, [], { supported: ['Not available on this phone'] }),
      exitCase('exit_error', { supported: true, error: 'unavailable' }, [], { error: ["Couldn't read recent app exits"] }),
    ],
  };

  // --------------------------------------------------------------- errors
  const errSchema = {
    entry: {
      source: { kind: 'string' }, message: { kind: 'string' }, stack: { kind: 'string' },
      occurrences: { kind: 'number' }, timestamp: { kind: 'number' },
    },
  };
  const er = (id, value, probes) => mk(id, 'error', value, [['entry', value, probes]]);
  out.diag_error = {
    schema: errSchema, excluded: {}, source: 'lib/diagnostics/app_diagnostics.dart (AppDiagnosticEntry)',
    cases: [
      er('error_screen', { source: 'flutter', message: 'Bad state: render failed in ChatList', stack: '#0 build (lib/chat.dart:42:3)', occurrences: 3, timestamp: -600000 },
        { source: ["A screen couldn't be drawn"], message: ['render failed in ChatList'], stack: ['lib/chat.dart:42:3'], occurrences: ['3 occurrences'], timestamp: ['{date}'] }),
      er('error_connection', { source: 'sse', message: 'Connection closed while reading events', timestamp: -3600000 },
        { source: ['Lost the live connection to the server'], message: ['Connection closed while reading events'] }),
    ],
  };

  // ---------------------------------------------------------------- crash
  const crashSchema = {
    record: { source: { kind: 'string' }, category: { kind: 'string' }, time: { kind: 'number' } },
    input: { errorMessage: { kind: 'string' }, errorStack: { kind: 'string' }, nativeText: { kind: 'string' } },
  };
  const cr = (id, how, record, input, recordProbes, inputProbes = {}) =>
    mk(id, 'crash', { how, record, input }, [['record', record, recordProbes], ['input', input, inputProbes]]);
  out.diag_crash = {
    schema: crashSchema, excluded: {}, source: 'lib/diagnostics/crash_diagnostics.dart (the opt-in store) and the native crash record',
    cases: [
      cr('crash_flutter', 'flutter', { source: 'flutter', category: 'Invalid state', time: 0 }, { errorMessage: 'MARKER-CRASH-MESSAGE-1', errorStack: '#0 secretFunction (package:app/secret.dart:1:1)' },
        { source: ['crash.flutter'], category: ['Invalid state'], time: ['{date}'] }, {}),
      cr('crash_anr', 'anr', { source: 'anr', category: 'Android reported that the app stopped responding', time: 0 }, { errorMessage: 'MARKER-CRASH-MESSAGE-2' },
        { source: ['crash.anr'], category: ['Android reported that the app stopped responding'], time: ['{date}'] }, {}),
      cr('crash_native', 'native', { source: 'native', category: 'Native application error', time: 0 }, { nativeText: 'MARKER-NATIVE-TEXT-1' },
        { source: ['crash.native'], category: ['Native application error'], time: ['{date}'] }, {}),
    ],
  };

  // ----------------------------------------------------------------- perf
  const perfSchema = {
    span: {
      name: { kind: 'string' }, durationMs: { kind: 'number' }, 'attrs.status': { kind: 'string' }, parent: { kind: 'string' },
      outcome: { kind: 'enum', values: ['ok', 'error'] }, isMark: { kind: 'boolean' }, startMicros: { kind: 'number' }, wallStart: { kind: 'string' }, id: { kind: 'number' },
    },
  };
  const pf = (id, value, probes) => mk(id, 'perf', value, [['span', value, probes]]);
  out.diag_perf = {
    schema: perfSchema, excluded: {}, source: 'lib/diagnostics/perf_trace.dart (PerfSpan)',
    cases: [
      pf('perf_ok', { name: 'GET /api/session', durationMs: 120, attrs: { status: '200' }, parent: 'open-chat', outcome: 'ok', isMark: false, id: 1, startMicros: 5000, wallStart: '2026-10-09T10:00:00Z' },
        { name: ['GET /api/session'], durationMs: ['120ms'], 'attrs.status': ['status=200'], parent: ['in open-chat'] }),
      pf('perf_failed', { name: 'GET /api/files', durationMs: 80, outcome: 'error', isMark: false, id: 2, startMicros: 6000, wallStart: '2026-10-09T10:00:01Z' }, { name: ['GET /api/files'], outcome: ['1 failed'], durationMs: ['80ms'] }),
      pf('perf_mark', { name: 'first-frame', durationMs: 0, attrs: { status: 'ready' }, outcome: 'ok', isMark: true, id: 3, startMicros: 7000, wallStart: '2026-10-09T10:00:02Z' }, { name: ['first-frame'], isMark: ['at '], startMicros: ['at '], 'attrs.status': ['status=ready'] }),
    ],
  };
  return out;
}
