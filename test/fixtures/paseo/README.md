# Native question wire fixtures

`native_questions_0_9_2.json` is a sanitized, source-derived projection of the
installed Paseo 0.9.2 provider requests. The question text, choices, IDs, plan and
paths are synthetic. This is not a claim of a newly captured agent run.

The coordinator's pulled package is under `scratchpad/paseo-pkg/server/dist/`
for job `87a8d964-900c-48f3-a841-cd593d87ac4c`:

- `server/server/agent/providers/claude/agent.js`: question normalization at
  46–116; plan actions at 731–758; request creation at 1555–1590;
  answer handling at 2048–2076.
- `server/server/agent/providers/pi/agent.js`: response/comment headers at
  31–35; combined ask-user request at 756–791; string answer handling at
  794–847. OMP uses the same shape at
  `server/server/agent/providers/omp/agent.js:456–547`.
- `server/web-ui/_expo/static/js/web/index-8868523fc6d311c5da524c067d1cd426.js:16308`:
  `parseQuestionFormQuestions` and `buildQuestionFormAnswers` specify header
  keys, strings joined with `, `, option-free custom input, `isOther` as an alias
  of `allowOther`, and empty optional text.

The tests deliberately send full question-text keys to Claude because its
normalizer accepts them directly and this preserves distinct questions that
share a header. Pi/OMP require their original header keys. Optional comments
stay visible with `optional: true` and an empty selection serializes as `""`.
Plans use only `implement` (which selects `acceptEdits`) or `reject`; the
`implement_resume` action may restore bypass permissions and is never selected.
