export const meta = {
  name: 'app-revamp-wave',
  description: 'Run one revamp wave (cut v2): build each unit in its own worktree, review it adversarially, fix, then integrate through one FIFO queue',
  whenToUse: 'Owner-approved app revamp (docs/ux-system/revamp/PLAN.md). Pass args {wave, cut: <work-units.json>, merged: [ids already on the base], gates: {"gate-P1.6a": "go" | "no-go"}}.',
  phases: [
    { title: 'Scope', detail: 'wave 3 only: each slice names its write set and answers its gate; slices are packed into dependency-aware, lock-free batches plus one serial CHAT lane' },
    { title: 'Build', detail: 'one agent per unit, own git worktree and branch revamp/<unit id>, branched from the integrated base' },
    { title: 'Review', detail: 'read-only adversarial review against the unit, its frozen spec, STANDARDS.md and the visual language' },
    { title: 'Fix', detail: 'the builder fixes what the review found, in the same worktree' },
    { title: 'Integrate', detail: 'one integrator, one FIFO queue: merges passing branches, regenerates the shared files, runs the gates' },
  ],
}

// args:
//   wave    '1' | '2a' | '2b' (runs 2b and the 2c chain together) | '2c' | '2d' | '3'
//   cut     the whole docs/ux-system/revamp/work-units.json object (preferred), or
//   units   the units (any waves; the wave filter picks the ones to run)
//   merged  unit ids already integrated on the base before this run
//   gates   coordinator decisions, e.g. { 'gate-P1.6a': 'no-go' }
//   base    the integration branch (default feat/phone-setup-v2)
// Units never merge each other's branches (R02): a unit branches from BASE
// after everything it builds on has been integrated there.
const A = args || {}
const CUT = A.cut || {}
const ALL = CUT.units || A.units || []
const WAVE = String(A.wave)
const BASE = A.base || 'feat/phone-setup-v2'
// A.repo: the checkout the integrator merges in (a separate worktree lets two runs integrate side by side).
const REPO = A.repo || '/home/owner/Storage/Code/oc_app'
const HOT = CUT.hotspots || A.hotspots || { exact: [], prefix: [], newUnder: [] }
const LOCK_GROUPS = CUT.lockGroups || A.lockGroups || {}
const SINGLE_LOCKS = CUT.singleFileLocks || A.singleFileLocks || []
const LANE_ORDER = CUT.chatLaneOrder || A.chatLaneOrder || []
const PLANNED = CUT.plannedParts || A.plannedParts || []
const GOLDENS = (CUT.goldenOwners && CUT.goldenOwners.integratorAlways) || []
const GATES = A.gates || {}
// Wider runs: several runs of the same wave may each take a shard of a tier
// ({ index, count }) with integrate: false; the coordinator then integrates
// the tier once (one integrator at a time, R01). tiers limits the run to
// those tier numbers.
const SHARD = A.shard || null
const INTEGRATE = A.integrate !== false
const ONLY_TIERS = A.tiers ? new Set(A.tiers.map(Number)) : null
const WAVE_SETS = { '1': ['1'], '2a': ['2a'], '2b': ['2b', '2c'], '2c': ['2c'], '2d': ['2d'], '3': ['3'] }
if (!WAVE_SETS[WAVE]) throw new Error(`unknown wave ${WAVE}`)
const UNITS = ALL.filter((u) => WAVE_SETS[WAVE].includes(String(u.wave)))
const byId = {}
ALL.forEach((u) => { byId[u.id] = u })
const merged = new Set(A.merged || [])

const deferred = [] // { id, waitingFor }
const refused = [] // { id, reason }
const blocked = [] // { id, blocker }
const coordinatorWork = [] // units the coordinator runs itself (coord-main)
const results = []
const reports = []

const RULES = `
Repository: ${REPO} (Flutter, Android first). Obey AGENTS.md.
Owner decision 2026-09-27 (speed): do not spend time on tests. Write the part's behaviour tests and its gallery once, run ONLY your own new test files once (plus analyze on your files); do not re-run other suites, do not chase unrelated failures (list them), do not regenerate other units' goldens. Reviewers never run tests.
Owner rule 2026-09-27 (rethink, not just restyle): for every item on your pages ask whether it belongs there at all; an action must name what it acts on ("Disconnect from Laptop", never a bare "Disconnect"), say what happens in one line, and live on the page of the thing it acts on. Move or remove stray items instead of restyling them, and list what you moved in your QA record.
Owner rule 2026-09-27 (no state sections): do not split a list into sections by state ("Needs you", "Running", ...). Use one list ordered by urgency (needs you, then running, then the rest newest first); the row's mark plus its worded state carries the meaning.
Owner decision 2026-09-27: Arabic is DROPPED — no Arabic/RTL galleries, no Arabic ARB entries for new copy (app_en.arb only), no RTL review. Galleries: phone 412x915 and one wide size (1280x800) only, light and dark.
Staying alive (the harness kills an agent that shows no progress for 3 minutes):
- No single command may run longer than 2 minutes. Run tests one or two files at a time as
  "timeout 150 tool/qa/machine_lock.sh test -- $F test -j 1 <files>" (F = the pinned flutter below); if it times out
  waiting for a slot, run it again. Never run a whole directory of goldens in one command.
- Read only what you need: your spec docs/ux-system/kit-api/<Part>.md (or your unit's pages in map/all.json), the specs of
  the parts you depend on, and the STANDARDS.md sections your task names — never the whole of STANDARDS.md, kit-v2.md or
  all.json at once (use grep and sed -n ranges).
- If "pub get" stalls, use "$F pub get --offline".
The one rulebook is docs/ux-system/revamp/STANDARDS.md (§1 definition of done, §15 tests and goldens, §16 evidence
template, §17 reviewer checklist, §18 gates). Where it and this list disagree, stop and report; do not work around it.
Contracts, in authority order (R15): owner decisions dated later win; docs/design/visual-language-2026-09-26.md (§6 glass
overrides target-ia §4); docs/ux-system/kit-api/<Part>.md (the frozen API blocks); docs/ux-system/kit-v2.md;
docs/ux-system/target-ia.md; docs/ux-system/map/all.json (your pages' records); docs/ux-system/owner-verdicts-2026-09-26.json.
Hard rules:
- Kit only (R14): outside lib/ui/kit/ construct only the kit-v2.md §9.1 allowlist. For your write set G1, G16, G7 and the look
  patterns reach 0 (showConfirmSheet -> showKitConfirm). Colours from theme roles, type from KitText roles, integer sizes,
  hairlines 1 physical px. Tooltip only through KitIconButton, KitTerm or KitTappable.tooltip; FloatingActionButton becomes
  the KitScreen bottom primary; Theme/DefaultTextStyle overrides only in lib/ui/app_theme.dart; Positioned becomes
  PositionedDirectional (R23).
- Planned kit parts (R13): apart from the part YOUR unit builds or changes, never create a file or class named
  ${PLANNED.join(', ')}. If you need another unit's part that has not merged,
  stop and report it; never build a local substitute. A genuinely new part goes to the coordinator for the contract.
- Additive only (R11): public class names and constructors gain optional parameters; old APIs stay as @Deprecated wrappers.
  A file a kit part replaces becomes a thin @Deprecated wrapper or is git mv-ed into lib/ui/kit with a re-export (R12).
- Shared files you never stage: lib/ui/kit/kit.dart (import parts directly: package:opencode_mobile/ui/kit/<part>.dart; the
  integrator adds exports, R06); test/kit_ratchet_baseline.json and the _baseline map in test/l10n_coverage_test.dart (both
  gates pass when counts drop; the integrator regenerates them, R05); test/design_standard_test.dart (R10);
  docs/design/ui-ledger/{ledger.json,pages.md,navigation.md} (append to docs/design/ui-ledger/parts/<area>.json only, R09);
  integrator-owned goldens (${GOLDENS.join(', ')}): render them to look, then git checkout those PNGs (R07); never stage
  test/**/failures/.
- Tests (R08): edit only the test files in your tests write set; new tests go in your own files
  (test/revamp/<unit id>_test.dart or your part's tests). A shared test you broke goes into sharedTestsBroken with why;
  the integrator fixes it after the merge. A renamed or deleted class listed in _migratedClasses goes there too (R10).
- Copy (R04): lib/l10n/app_en.arb AND app_ar.arb (real Arabic); new keys are prefixed with your part or screen stem; never rename
  or delete a key in waves 1-2d; run gen-l10n once at the end.
- Pinned Flutter: F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter.
  Format changed files with "dart format --language-version=3.10". Run ONLY affected test files with "$F test -j 1 <files>"
  (six agents share one 15 GB machine). Regenerate a golden only after looking at the new image.
- Never: APK builds, Gradle, emulators, adb, pushes, the phone (R19, R20: on-device proof is coordinator work). Stay inside your
  write set, plus a new kit part file, your tests, your own goldens and your QA record. lib/main.dart and
  lib/state/connection.dart: only when your unit says you own them (R18).
- Commit on your branch with messages ending "[skip ci]" and the two trailer lines:
  Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01G7Gr8TYUhyCJ169CtNjJB1
- Write docs/qa/revamp-<unit id>/README.md from the STANDARDS.md §16.2 template.`

const BUILD_SCHEMA = {
  type: 'object',
  properties: {
    branch: { type: 'string' },
    worktree: { type: 'string' },
    commits: { type: 'array', items: { type: 'string' } },
    filesChanged: { type: 'array', items: { type: 'string' } },
    testsRun: { type: 'array', items: { type: 'string' } },
    testsPassed: { type: 'boolean' },
    analyzeClean: { type: 'boolean' },
    ratchetBefore: { type: 'number' },
    ratchetAfter: { type: 'number' },
    newKitParts: { type: 'array', items: { type: 'string' } },
    sharedTestsBroken: {
      type: 'array',
      items: { type: 'object', properties: { file: { type: 'string' }, why: { type: 'string' } }, required: ['file', 'why'] },
    },
    contractProblems: { type: 'array', items: { type: 'string' } },
    gaps: { type: 'array', items: { type: 'string' } },
  },
  required: ['branch', 'worktree', 'commits', 'testsPassed', 'analyzeClean', 'sharedTestsBroken', 'gaps'],
}

const REVIEW_SCHEMA = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['pass', 'fix', 'reject'] },
    issues: {
      type: 'array',
      items: {
        type: 'object',
        properties: { file: { type: 'string' }, line: { type: 'number' }, problem: { type: 'string' }, fix: { type: 'string' } },
        required: ['problem', 'fix'],
      },
    },
  },
  required: ['verdict', 'issues'],
}

const SCOPE_SCHEMA = {
  type: 'object',
  properties: {
    feasible: { type: 'boolean' },
    blocker: { type: 'string' },
    gateAnswer: { type: 'string' },
    write: { type: 'array', items: { type: 'string' } },
    create: { type: 'array', items: { type: 'string' } },
    tests: { type: 'array', items: { type: 'string' } },
  },
  required: ['feasible', 'write', 'create', 'tests'],
}

const INTEGRATE_SCHEMA = {
  type: 'object',
  properties: {
    merged: { type: 'array', items: { type: 'string' } },
    skipped: {
      type: 'array',
      items: { type: 'object', properties: { id: { type: 'string' }, reason: { type: 'string' } }, required: ['id', 'reason'] },
    },
    apkChecks: { type: 'array', items: { type: 'string' } },
    sharedTestsFixed: { type: 'array', items: { type: 'string' } },
    testsFailing: { type: 'array', items: { type: 'string' } },
    ratchetBefore: { type: 'number' },
    ratchetAfter: { type: 'number' },
    notes: { type: 'string' },
  },
  required: ['merged', 'skipped', 'testsFailing', 'notes'],
}

// ------------------------------------------------------------ helpers
function isHotspot(p) {
  return (HOT.exact || []).includes(p) || (HOT.prefix || []).some((x) => p.startsWith(x))
}

function isNewHotspot(p) {
  return isHotspot(p) || (HOT.newUnder || []).some((x) => p.startsWith(x))
}

// C47 (3), R17: expand a write set into lock keys.
function locksOf(paths, hints) {
  const out = new Set(hints || [])
  for (const p of paths) {
    if (isHotspot(p)) continue
    let group = null
    for (const name of Object.keys(LOCK_GROUPS)) {
      const r = LOCK_GROUPS[name]
      const hit = (r.exact || []).includes(p) || (r.prefix || []).some((x) => p.startsWith(x)) ||
        (r.prefixSuffix || []).some((ps) => p.startsWith(ps[0]) && p.endsWith(ps[1]))
      if (hit) { out.add(name); group = name }
    }
    if (SINGLE_LOCKS.includes(p) || !group) out.add(p)
  }
  return [...out].sort()
}

function gateOk(token) {
  const m = /^(gate-[^:]+):go$/.exec(token)
  return Boolean(m && GATES[m[1]] === 'go')
}

// `after` ids, with programme-<P> expanded to every wave-3 slice of P.
function depsOf(u) {
  const out = []
  for (const a of u.after || []) {
    if (a.startsWith('programme-')) {
      const p = a.slice('programme-'.length)
      ALL.filter((x) => x.programme === p && String(x.wave) === '3' && x.id !== u.id).forEach((x) => out.push(x.id))
    } else out.push(a)
  }
  return [...new Set(out)]
}

function missingDeps(u) {
  return depsOf(u).filter((d) => !(d.startsWith('gate-') ? gateOk(d) : merged.has(d)))
}

function isKitUnit(u) {
  return String(u.wave) === '1'
}

// R16: a kit unit builds to a frozen API block or not at all; R22: no unit
// runs while the cut still marks it provisional (visual-language merge pending).
function specProblem(u) {
  if (u.provisional) return `provisional: ${u.provisionalReason || 'regenerate the cut'} (R22)`
  if (!isKitUnit(u)) return null
  if (!u.spec) return 'no spec field (C02)'
  if (u.specFrozen === false) return `spec ${u.spec} is not frozen yet (regenerate the cut after the freeze)`
  return null
}

function owns(u) {
  return (u.write || []).filter((f) => f === 'lib/main.dart' || f === 'lib/state/connection.dart')
}

function describe(u) {
  const lines = [
    `Unit ${u.id} (wave ${u.wave}${u.tier ? `, tier ${u.tier}` : ''}): ${u.title}.`,
    u.fromCut ? `Full definition (title, finishLine, nonGoal, acceptance, gate, proof): read the unit with id "${u.id}" in ${typeof u.fromCut === 'string' ? u.fromCut : 'docs/ux-system/revamp/work-units.json'} and honour all of it.` : '',
    A.commonAcceptance ? `Also:\n- ${A.commonAcceptance.join('\n- ')}` : '',
    A.unitNotes && A.unitNotes[u.id] ? `Notes for this unit:\n- ${A.unitNotes[u.id].join('\n- ')}` : '',
    u.write && u.write.length ? `Write set (only these files, plus a new kit part and your tests): ${u.write.join(', ')}.` : '',
    u.region ? `Region: you edit only ${u.region} of the host, plus call sites anywhere in the chat library (R03).` : '',
    u.tests && u.tests.length ? `Tests write set (R08): ${u.tests.join(', ')}.` : 'Tests write set: only new test files of your own (R08).',
    u.spec ? `Frozen spec (R16): ${u.spec} (source ${u.specSource}). Build exactly that API; a spec problem is reported, not worked around.` : '',
    u.pages && u.pages.length ? `Pages (map records to honour): ${u.pages.join(', ')}.` : '',
    u.finishLine ? `Finish line: ${u.finishLine}` : '',
    u.nonGoal ? `Not in this unit: ${u.nonGoal}` : '',
    u.acceptance && u.acceptance.length ? `Acceptance:\n- ${u.acceptance.join('\n- ')}` : '',
    u.kitReadOnly && u.kitReadOnly.length ? `Kit parts you use read-only (their files are not yours): ${u.kitReadOnly.join(', ')}.` : '',
    owns(u).map((f) => `You own ${f} for this unit; keep the edit minimal (R18).`).join('\n'),
    u.overBudget ? `Budget note: ${u.overBudgetReason}. Work through the file top to bottom and commit in slices.` : '',
  ]
  if (u.kind === 'kit-part') {
    lines.push('Build the part to its frozen spec: API, states, motion, haptics, a11y, RTL, data safety, adaptive kit-v2.md §8.2, ' +
      'galleries at the §8.4 sizes (DPR 3), behaviour tests. Do not edit lib/ui/kit/kit.dart and do not migrate screens.')
  } else if (u.kind === 'kit-change') {
    lines.push('Change the existing part as its frozen spec says, keeping every current caller compiling (R11); update its gallery and tests.')
  } else if (u.kind === 'kit-gate') {
    lines.push('Change the gates exactly as the acceptance says; every other gate test must still pass on the base.')
  } else if (u.kind === 'screen-revamp') {
    lines.push('Revamp every file in the write set: rebuild it from kit parts only, apply the visual language, and fix what the map ' +
      'records ask for these pages (proposal "fix", actionsMissing, statesMissing, whenMissing -> explains/offers-enable). ' +
      'Regenerate the goldens you own and look at them.')
  } else if (u.kind === 'kit-hygiene') {
    lines.push('Delete every @Deprecated wrapper that has no callers left; list the ones still in use with the wave-3 slice that clears them.')
  } else if (u.kind === 'programme-slice') {
    lines.push('Implement the slice end to end (controller -> UI -> persistence/deletion), with tests that fail without it. ' +
      'Its proof field is for the coordinator: describe the exact emulator steps in the QA record.')
  }
  return lines.filter(Boolean).join('\n')
}

// ------------------------------------------------ one FIFO queue (R01)
let queueTail = Promise.resolve()
function enqueueIntegration(label, done, opts) {
  const run = queueTail.then(() => integrate(label, done, opts || {}))
  queueTail = run.catch(() => null)
  return run
}

async function integrate(label, done, opts) {
  const ok = done.filter(Boolean).filter((r) => r.built && r.built.testsPassed && r.built.analyzeClean && (A.review === false ? true : (r.review && r.review.verdict !== 'reject')))
  done.filter(Boolean).filter((r) => !ok.includes(r)).forEach((r) => log(`not ready: ${r.unit}`))
  log(`${label}: ${ok.length}/${done.length} units ready to integrate`)
  if (!ok.length && !opts.goldens) return { label, merged: [], report: null }
  const parts = ok.flatMap((r) => (r.built.newKitParts || []))
  const broken = ok.flatMap((r) => (r.built.sharedTestsBroken || []).map((b) => `${r.unit}: ${b.file} (${b.why})`))
  const report = await agent(
    `You are the integrator for revamp ${label}. Work in the main checkout ${REPO} on branch ${BASE}. Obey AGENTS.md.\n` +
      '0. R01: run "git status --porcelain". If it prints anything, stop: return merged [] and say why. Check that ' +
      '"git config --get merge.arbunion.driver" and "git config --get merge.ours.driver" are set (PLAN.md §8); if not, stop and say so. ' +
      'Never change global git config.\n' +
      `1. Merge these branches one at a time with --no-ff, in this order: ${ok.map((r) => 'revamp/' + r.unit).join(', ') || '(none)'}.\n` +
      '   R19: before merging a branch whose "git diff --name-only ' + BASE + '...<branch>" touches android/**/*.kt, run ' +
      '"$F build apk --release" in that branch\'s worktree (one at a time); a failure at validateSigningRelease is acceptable, ' +
      'a Kotlin or Dart compile error is not (skip the branch with the error).\n' +
      '   Conflicts: lib/l10n/*.arb are merged by the arbunion driver (tool/l10n/arb_merge.py); when it fails, one key got two ' +
      'values: keep both meanings under distinct stem-prefixed keys and record it. Generated app_localizations*.dart and the ' +
      'ledger outputs take ours (they are regenerated). test/kit_ratchet_baseline.json and the l10n _baseline: take ours. ' +
      'lib/ui/kit/kit.dart: take ours (you add the exports). Goldens: unit-owned take theirs, integrator-owned take ours. ' +
      'Any other conflict means the cut was wrong: "git merge --abort", skip the unit with "conflict: <files>".\n' +
      '   After each merge: "$F gen-l10n" and commit if it changed anything.\n' +
      '2. After all merges:\n' +
      `   - R06: add sorted export lines and doc-table rows to lib/ui/kit/kit.dart for every newly merged part file${parts.length ? ` (${parts.join(', ')})` : ''}.\n` +
      `   - R08, R10: fix the shared tests the units reported${broken.length ? `: ${broken.join('; ')}` : ' (none reported)'}, including ` +
      '_migratedClasses in test/design_standard_test.dart. A failing test after a redesign may be a product bug: read it before changing it.\n' +
      '   - R05: regenerate test/kit_ratchet_baseline.json (KIT_RATCHET_WRITE=1 "$F test test/kit_ratchet_test.dart") and the ' +
      '_baseline map in test/l10n_coverage_test.dart (L10N_BASELINE_PRINT=1); counts may only go down; one commit.\n' +
      '   - R09: "python3 docs/design/ui-ledger/build_ledger.py"; commit the outputs.\n' +
      (opts.goldens
        ? `   - R07: regenerate the integrator-owned goldens (${GOLDENS.join(', ')}), look at every changed image against ` +
          'docs/design/visual-language-2026-09-26/*.png, commit them; never stage test/**/failures/.\n'
        : '') +
      '   - "$F analyze" must be clean. Run test/kit_ratchet_test.dart, test/design_standard_test.dart, test/l10n_coverage_test.dart, ' +
      'test/ui_ledger_coverage_test.dart and every test file the merged branches touched, with -j 3.\n' +
      '3. Commit each fix with "[skip ci]" and the trailers (Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com> and ' +
      'Claude-Session: https://claude.ai/code/session_01G7Gr8TYUhyCJ169CtNjJB1). Never push. A branch whose tests fail after the ' +
      'merge and cannot be fixed in the shared files is reverted ("git revert -m 1") and listed as skipped.\n' +
      'Return the unit ids you merged (not branch names), the skipped ones with reasons, the APK checks, failing tests and the ratchet totals.',
    { label: `integrate ${label}`, phase: 'Integrate', schema: INTEGRATE_SCHEMA },
  )
  const wanted = new Set(ok.map((r) => r.unit))
  const got = report ? report.merged.filter((id) => wanted.has(id)) : []
  got.forEach((id) => merged.add(id))
  reports.push({ label, report })
  return { label, merged: got, report }
}

// -------------------------------------------------- build, review, fix
async function buildReviewFix(u) {
  const built = await agent(
    `${describe(u)}\n\nStart: you are in a fresh git worktree. Run "git switch -c revamp/${u.id} ${BASE}". Everything this unit ` +
      `builds on (${depsOf(u).join(', ') || 'nothing'}) is already integrated on ${BASE}; never merge another revamp branch (R02). ` +
      `Then "$F pub get". Work, check, commit.\n${RULES}\nReturn the build record; worktree = your absolute working directory.`,
    { label: `build ${u.id}`, phase: 'Build', schema: BUILD_SCHEMA, isolation: 'worktree', model: undefined },
  )
  if (!built) return null
  if (A.review === false) return { unit: u.id, built, review: null }
  const review = await agent(
    `Adversarially review branch revamp/${u.id} in ${built.worktree} against ${BASE} ("git diff ${BASE}...HEAD").\n${describe(u)}\n` +
      'Use the STANDARDS.md §17 reviewer checklist. Try to find what is wrong: raw framework widgets outside the kit, colours or type ' +
      'not from tokens, soft or half-pixel edges, missing states, broken RTL or 200 % text, copy that uses engine words, lost typed ' +
      'input, map records ignored, an API that differs from the frozen spec, edits outside the write set or tests write set, staged ' +
      'shared files (kit.dart, ratchet or l10n baselines, ledger outputs, integrator goldens, test/**/failures/), removed or renamed ' +
      'public APIs, a local substitute for a planned kit part, tests that assert implementation instead of behaviour, goldens ' +
      'regenerated without matching docs/design/visual-language-2026-09-26/*.png. Read-only: never run tests or builds, never edit. ' +
      'verdict "pass" only if nothing material is wrong; "reject" when the unit must be redone.',
    { label: `review ${u.id}`, phase: 'Review', schema: REVIEW_SCHEMA, effort: 'high' },
  )
  if (!review || review.verdict === 'pass') return { unit: u.id, built, review: review || { verdict: 'pass', issues: [] } }
  if (review.verdict === 'reject') return { unit: u.id, built, review }
  const fixed = await agent(
    `Fix these review findings on branch revamp/${u.id}, working in ${built.worktree} (do not create another worktree):\n` +
      review.issues.map((i, n) => `${n + 1}. ${i.file || ''}:${i.line || ''} ${i.problem} -> ${i.fix}`).join('\n') +
      `\n${describe(u)}\n${RULES}\nReturn the updated build record.`,
    { label: `fix ${u.id}`, phase: 'Fix', schema: BUILD_SCHEMA },
  )
  return { unit: u.id, built: fixed || built, review, fixed: Boolean(fixed) }
}

// -------------------------------------------- tiers (R02) for waves 1-2d
function admit(u) {
  if (u.kind === 'coordinator') {
    coordinatorWork.push({ id: u.id, waitingFor: missingDeps(u) })
    return false
  }
  const why = specProblem(u)
  if (why) {
    refused.push({ id: u.id, reason: why })
    return false
  }
  const miss = missingDeps(u)
  if (miss.length) {
    deferred.push({ id: u.id, waitingFor: miss })
    return false
  }
  return true
}

async function runTiers(units, label, goldensEach) {
  const tiers = [...new Set(units.map((u) => u.tier || 1))].sort((a, b) => a - b)
    .filter((t) => !ONLY_TIERS || ONLY_TIERS.has(Number(t)))
  for (let i = 0; i < tiers.length; i += 1) {
    const t = tiers[i]
    const inTier = units.filter((u) => (u.tier || 1) === t && !merged.has(u.id))
      .filter((u, n) => !SHARD || n % SHARD.count === SHARD.index)
    const ready = inTier.filter(admit)
    log(`${label} tier ${t}${SHARD ? ` shard ${SHARD.index + 1}/${SHARD.count}` : ''}: ${ready.length} units`)
    if (!ready.length) continue
    const done = await pipeline(ready, (u) => buildReviewFix(u))
    results.push(...done.filter(Boolean))
    if (INTEGRATE) await enqueueIntegration(`${label} tier ${t}`, done, { goldens: goldensEach || i === tiers.length - 1 })
  }
}

// 2c: one editor at a time; each link is integrated before the next starts (R03).
async function runChain(links) {
  const ordered = [...links].sort((a, b) => (a.tier || 0) - (b.tier || 0))
  for (let i = 0; i < ordered.length; i += 1) {
    const u = ordered[i]
    if (!admit(u)) {
      ordered.slice(i + 1).forEach((v) => deferred.push({ id: v.id, waitingFor: [u.id] }))
      return
    }
    const r = await buildReviewFix(u)
    results.push(...[r].filter(Boolean))
    await enqueueIntegration(`chat chain ${u.id}`, [r], { goldens: i === ordered.length - 1 })
    if (!merged.has(u.id)) {
      ordered.slice(i + 1).forEach((v) => deferred.push({ id: v.id, waitingFor: [u.id] }))
      return
    }
  }
}

// ------------------------------------------------------------- wave 3
// Mutex per lock key, acquired in sorted order after the slice's
// dependencies resolved, released after its own integration.
const lockTails = {}
async function withLocks(keys, fn) {
  const releases = []
  for (const k of [...new Set(keys)].sort()) {
    const prev = lockTails[k] || Promise.resolve()
    let release
    const mine = new Promise((res) => { release = res })
    lockTails[k] = prev.then(() => mine)
    await prev
    releases.push(release)
  }
  try {
    return await fn()
  } finally {
    releases.forEach((r) => r())
  }
}

async function scopeWave3(slices) {
  phase('Scope')
  // Gate decisions the coordinator took (C44, C33 correction).
  for (const u of slices) {
    const gate = (u.after || []).find((a) => a.startsWith('gate-'))
    if (gate && !gateOk(gate)) {
      blocked.push({ id: u.id, blocker: `${gate.split(':')[0]} is ${GATES[gate.split(':')[0]] || 'undecided'}` })
      if (u.onGateNoGo && GATES[u.onGateNoGo.gate] === 'no-go') {
        for (const [file, target] of Object.entries(u.onGateNoGo.moveWrite || {})) {
          const t = slices.find((x) => x.id === target)
          if (t) t.write = [...new Set([...(t.write || []), file])]
        }
      }
    }
  }
  const todo = slices.filter((u) => !blocked.some((b) => b.id === u.id))
  const scoped = await parallel(todo.map((u) => () =>
    agent(
      `${describe(u)}\nRead-only: decide whether this slice is feasible now (AGENTS.md productivity rule 2)` +
        (u.gate ? ` and answer its go/no-go question: ${u.gate} (no-go means feasible = false, with the answer as the blocker)` : '') +
        `. List the existing files you will edit or delete (write), the new files you will create (create) and the test files you ` +
        `will edit (tests). The seeded files ${(u.write || []).join(', ') || '(none)'} are pre-assigned and must stay in write. ` +
        `Omit HOTSPOTS: ${[...(HOT.exact || []), ...(HOT.prefix || []).map((p) => p + '**')].join(', ')} and new files under lib/ui/kit/. ` +
        `Kit parts built in wave 1 are read-only for you.`,
      { label: `scope ${u.id}`, phase: 'Scope', schema: SCOPE_SCHEMA, effort: 'medium' },
    ).then((s) => {
      if (!s) return null
      const write = [...new Set([...(u.write || []), ...s.write])].filter((p) => !isHotspot(p))
      const create = s.create.filter((p) => !isNewHotspot(p))
      const hints = u.lane === 'CHAT' ? ['CHAT'] : []
      return { ...u, write: [...write, ...create].sort(), tests: [...new Set([...(u.tests || []), ...s.tests])].sort(),
        locks: locksOf([...write, ...create], hints), feasible: s.feasible, blocker: s.blocker, gateAnswer: s.gateAnswer }
    })))
  const out = []
  todo.forEach((u, i) => {
    const s = scoped[i]
    if (!s) blocked.push({ id: u.id, blocker: 'scope agent failed' })
    else if (!s.feasible) blocked.push({ id: u.id, blocker: s.blocker || s.gateAnswer || 'not feasible' })
    else out.push(s)
  })
  return out
}

// C47, R17: batches plus one serial CHAT lane. A slice joins batch i only if
// i > the batch of every dependency (a lane dependency counts at the batch
// its own dependencies were ready), write sets are expanded into locks, and a
// batch holds at most one NATIVE slice. Mirrors build_units.py pack_wave3.
function packWave3(slices) {
  const pool = new Set(slices.map((u) => u.id))
  const get = {}
  slices.forEach((u) => { get[u.id] = u })
  const progOrder = ['P0', 'P9', 'P7', 'P8', 'P4', 'P1', 'P3', 'P10', 'P5', 'P6']
  const laneIdx = (id) => (LANE_ORDER.indexOf(id) < 0 ? 99 : LANE_ORDER.indexOf(id))
  const lane = slices.filter((u) => u.locks.includes('CHAT')).map((u) => u.id)
    .sort((a, b) => laneIdx(a) - laneIdx(b) || (a < b ? -1 : 1))
  const deps = {}
  slices.forEach((u) => { deps[u.id] = new Set(depsOf(u).filter((d) => pool.has(d))) })
  for (let i = 1; i < lane.length; i += 1) deps[lane[i]].add(lane[i - 1])
  const prio = (id) => [progOrder.indexOf(get[id].programme), laneIdx(id), id]
  const cmp = (a, b) => {
    const x = prio(a)
    const y = prio(b)
    return x[0] - y[0] || x[1] - y[1] || (x[2] < y[2] ? -1 : x[2] > y[2] ? 1 : 0)
  }
  const order = []
  const done = new Set()
  while (done.size < slices.length) {
    const ready = slices.map((u) => u.id).filter((id) => !done.has(id) && [...deps[id]].every((d) => done.has(d))).sort(cmp)
    if (!ready.length) {
      // A cycle (only possible if the scope pass contradicts the cut): report instead of hanging.
      slices.filter((u) => !done.has(u.id)).forEach((u) => blocked.push({ id: u.id, blocker: 'dependency cycle after scoping' }))
      break
    }
    order.push(ready[0])
    done.add(ready[0])
  }
  const batches = []
  const where = {}
  const readyAt = {}
  for (const id of order) {
    const need = Math.max(-1, ...[...deps[id]].map((d) => (d in where ? where[d] : d in readyAt ? readyAt[d] : -1)))
    if (lane.includes(id)) { readyAt[id] = need; continue }
    const locks = get[id].locks
    const native = locks.includes('NATIVE')
    let i = need + 1
    while (i < batches.length && (locks.some((k) => batches[i].locks.has(k)) || (native && batches[i].native))) i += 1
    if (i === batches.length) batches.push({ ids: [], locks: new Set(), native: false })
    batches[i].ids.push(id)
    locks.forEach((k) => batches[i].locks.add(k))
    batches[i].native = batches[i].native || native
    where[id] = i
  }
  return { batches: batches.map((b) => b.ids.map((id) => get[id])), lane: lane.map((id) => get[id]).filter((u) => order.includes(u.id)) }
}

async function runWave3(slices) {
  // Earlier-wave dependencies must already be on the base.
  const inWave = new Set(slices.map((u) => u.id))
  const startable = []
  for (const u of slices) {
    const outside = depsOf(u).filter((d) => !inWave.has(d) && !d.startsWith('gate-') && !merged.has(d))
    if (outside.length) deferred.push({ id: u.id, waitingFor: outside })
    else startable.push(u)
  }
  const scoped = await scopeWave3(startable)
  // Blocked or deferred dependencies propagate (C47 (2)).
  const out = new Set([...blocked.map((b) => b.id), ...deferred.map((d) => d.id)])
  let live = scoped
  let changed = true
  while (changed) {
    changed = false
    live = live.filter((u) => {
      // afterMode 'settled' (slice-P9.10): a blocked slice never runs, so it only has to settle.
      const bad = depsOf(u).filter((d) => out.has(d) && !(u.afterMode === 'settled' && blocked.some((b) => b.id === d)))
      if (!bad.length) return true
      blocked.push({ id: u.id, blocker: `depends on ${bad.join(', ')}` })
      out.add(u.id)
      changed = true
      return false
    })
  }
  const plan = packWave3(live)
  log(`wave 3: ${live.length} slices in ${plan.batches.length} batches + a CHAT lane of ${plan.lane.length}; ` +
    `${blocked.length} blocked, ${deferred.length} deferred`)
  const settle = {}
  const doneP = {}
  live.forEach((u) => { doneP[u.id] = new Promise((res) => { settle[u.id] = res }) })
  async function runSlice(u) {
    const deps = depsOf(u).filter((d) => d in doneP)
    const oks = await Promise.all(deps.map((d) => doneP[d]))
    const failed = deps.filter((d, i) => !oks[i])
    if (failed.length && u.afterMode !== 'settled') {
      deferred.push({ id: u.id, waitingFor: failed })
      settle[u.id](false)
      return
    }
    await withLocks(u.locks, async () => {
      const r = await buildReviewFix(u)
      if (r) results.push(r)
      await enqueueIntegration(`wave 3 ${u.id}`, [r], {})
    })
    settle[u.id](merged.has(u.id))
  }
  await parallel([
    async () => {
      for (const u of plan.lane) await runSlice(u)
    },
    async () => {
      for (const batch of plan.batches) await parallel(batch.map((u) => () => runSlice(u)))
    },
  ])
  // Integrator-owned goldens once at the end of the wave (R07).
  await enqueueIntegration('wave 3 goldens', [], { goldens: true })
  return plan
}

// ------------------------------------------------------------------ run
let plan = null
if (WAVE === '3') {
  plan = await runWave3(UNITS)
} else if (WAVE === '2b') {
  // 2c starts after the 2a integration and runs alongside 2b only, through the same queue (R01, R03).
  await parallel([
    () => runTiers(UNITS.filter((u) => String(u.wave) === '2b'), 'wave 2b', false),
    () => runChain(UNITS.filter((u) => String(u.wave) === '2c')),
  ])
} else if (WAVE === '2c') {
  await runChain(UNITS)
} else {
  // Wave 1 integrates (and regenerates integrator goldens) after every tier.
  await runTiers(UNITS, `wave ${WAVE}`, WAVE === '1')
}

const reported = new Set([...deferred.map((d) => d.id), ...refused.map((r) => r.id), ...blocked.map((b) => b.id),
  ...coordinatorWork.map((c) => c.id)])
const unaccounted = UNITS.map((u) => u.id).filter((id) => !merged.has(id) && !reported.has(id))
if (deferred.length) log(`deferred: ${deferred.map((d) => `${d.id} (waiting for ${d.waitingFor.join(', ')})`).join('; ')}`)
if (refused.length) log(`refused: ${refused.map((r) => `${r.id} (${r.reason})`).join('; ')}`)
if (blocked.length) log(`blocked: ${blocked.map((b) => `${b.id} (${b.blocker})`).join('; ')}`)
if (coordinatorWork.length) log(`coordinator work: ${coordinatorWork.map((c) => c.id).join(', ')}`)
if (unaccounted.length) log(`built but not merged (retry): ${unaccounted.join(', ')}`)
return {
  wave: WAVE,
  merged: UNITS.map((u) => u.id).filter((id) => merged.has(id)),
  notMerged: unaccounted,
  deferred,
  refused,
  blocked,
  coordinatorWork,
  wave3Plan: plan && { batches: plan.batches.map((b) => b.map((u) => u.id)), lane: plan.lane.map((u) => u.id) },
  results: results.map((r) => ({ unit: r.unit, verdict: r.review && r.review.verdict, testsPassed: r.built.testsPassed,
    analyzeClean: r.built.analyzeClean, sharedTestsBroken: r.built.sharedTestsBroken || [], contractProblems: r.built.contractProblems || [],
    gaps: r.built.gaps })),
  reports: reports.map((r) => ({ label: r.label, report: r.report })),
}
