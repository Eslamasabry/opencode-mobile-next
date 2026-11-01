# Chats review fixes — 2026-11-01

Baseline: `066a00be` on `native/fix-chats-2026-11-01`. The requested review file is absent from this checkout;
its source was recovered read-only with `git show 3bb7632e:docs/native/reviews/chats.md`. Each finding was checked
against this baseline and the flat core contracts in `INTEGRATE-W1.md` and `INTEGRATE-W2.md` before editing.

All six MAJOR findings still applied and are fixed. The review contains no BLOCKER. None of its MAJOR findings
was already fixed by integration. The two MINOR findings were excluded from the requested BLOCKER/MAJOR pass.

## Per finding

| # | Finding | Disposition and regression test |
|---|---|---|
| 1 | Brief can open another profile's conversation | **Fixed.** `ChatsStateTest` — `B-NAV-030 finding 1 brief joins the complete profile source and folder identity`. The join checks profile, source, captured location, session and folder; missing scope/location and ambiguous matches fail closed. The test includes colliding profiles, sources, folders and locations. `ChatFeedTest` — `B-NAV-030 finding 1 sources retain the location used by brief identities` also verifies source propagation and distinct list keys. |
| 2 | Continue acknowledges rows never displayed | **Fixed.** `ChatsViewModelTest` — `B-CHAT-094 finding 2 Continue uses the rendered token and excludes missing rows`. The screen passes its immutable acknowledgement snapshot, containing only matched/rendered runs and requests, with the original profile/location. Updating the live brief before the tap cannot acknowledge the new rows. Overflow is excluded. The `ReturnBriefFeed.dismiss` contract explicitly requires dispatch using this captured scope. |
| 3 | Reads bypass store serialization | **Fixed.** `ReturnBriefTest` — `B-STATE-082 finding 3 delayed reads serialize with dismissals and deletion`. Reads, loading, writes and profile deletion now acquire the same per-profile mutex. A delayed initial read cannot overlap a save and overwrite its cache. Existing `B-STATE-083 deleting a profile drains pending dismissals and clears both copies` remains green. |
| 4 | Cancellation can lose committed dismissals | **Fixed.** `ReturnBriefTest` — `B-STATE-082 finding 4 cancelled committed dismissal survives the next merge`. An admitted preference commit and cache publication form one non-cancellable step inside the lane. The regression cancels a caller during a dispatched, non-cancellable platform commit, then verifies a subsequent dismissal retains both committed identities after restart. Profile removal and cache invalidation use the same protection. |
| 5 | Unknown and overflow brief states disappear | **Fixed.** `ChatsScreenTest` — `B-CHAT-095 finding 5 unsupported read state is visibly distinct from empty`, `B-CHAT-095 finding 5 partial inventory explains uncertainty alongside known rows`, and `B-CHAT-093 finding 5 overflow has a visible filtered-list action`. Unknown/partial state renders a quiet line, including when known rows exist. Overflow renders “and N more”; `ChatsViewModelTest` — `B-CHAT-093 finding 5 overflow opens only its unacknowledged conversations` verifies that this action narrows the existing list to exactly the hidden identities, folds away the brief and acknowledges nothing. Clear filters restores the ordinary list. |
| 6 | Destructive confirmation survives recovery | **Fixed.** `OpeningGateTest` — `SCREENS-NAV-01 finding 6 recovery revokes destructive confirmation`, `SCREENS-NAV-01 finding 6 a later failure does not resurrect consent`, and `SCREENS-NAV-01 finding 6 rapid recovery cannot reuse a conflated failed phase`. Recovery clears consent; callbacks carry a question id and are checked against the current atomic bootstrap phase/generation both before admission and immediately before reset dispatch. A previous callback cannot answer a new question. The port requires a generation advance for each transition or replacement attempt, so StateFlow conflation cannot hide recovery followed by a new failure. |
| 7 | Clear filters leaves stale search text | **Rejected for this pass:** MINOR, outside the requested BLOCKER/MAJOR scope. The finding remains valid; no claim that it was fixed. |
| 8 | Attention filters are mutually exclusive | **Rejected for this pass:** MINOR, outside the requested BLOCKER/MAJOR scope. The finding remains valid; no claim that it was fixed. |

## Scope and contracts

Code stays in `feature/chats`, `feature/onboarding`, and `engine/agents/**/feed/**`. The only shared-file exception
is two required brief strings in `ui/words/**/strings_chats.xml`, supplied in all seven existing locales for finding 5.
No core, protocol, kit, shell, route or transcript changes were needed. Existing flat `Failure.StorageRefused` remains
unchanged; no stand-in failure family was introduced.

The read-side ports now carry the missing safety information: `BriefScope` and feed `locationKey` identify the
origin/location; `BriefUi.acknowledgement` contains only rendered identities; `OpeningSnapshot` carries an atomic
phase/generation. Unknown origin information refuses a brief-row join rather than guessing a target.

## Failing-first evidence and validation

The original store failed both new B-STATE-082 regressions. The original gate failed both recovery regressions.
After the brief API scaffolding compiled, the unfixed implementation failed all five initial finding 1/2/5
assertions: the duplicate-id join, latest-brief dismissal, unknown-state rendering, overflow rendering and filtered
list action. A further failing-first gate test exposed rapid Ready → Failed conflation, which led to the atomic
bootstrap generation contract. Every listed test passes on the final files.

Final command, from `native/`, under the PC's shared build lock (`/home/eslam/Storage/tmp/oc-build.lock`):

```bash
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 \
ANDROID_HOME=/home/eslam/Android/Sdk \
./gradlew --no-daemon -Pkotlin.incremental=false \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8' \
  --max-workers=2 -I /tmp/chats-test-environment.gradle \
  :engine:agents:desktopTest :feature:chats:desktopTest \
  :feature:onboarding:desktopTest :ui:words:desktopTest \
  :engine:agents:compileAndroidMain :feature:chats:compileAndroidMain \
  :feature:onboarding:compileAndroidMain :ui:words:compileAndroidMain
```

**BUILD SUCCESSFUL**, 1 min 27 s. All four touched modules compiled for desktop and Android.

| Module | Desktop tests | Failures / errors / skipped |
|---|---:|---:|
| `engine:agents` | 18 | 0 / 0 / 0 |
| `feature:chats` | 23 | 0 / 0 / 0 |
| `feature:onboarding` | 12 | 0 / 0 / 0 |
| `ui:words` | 4 | 0 / 0 / 0 |
| **Total** | **57** | **0 / 0 / 0** |

The machine's global Gradle settings request a larger heap, so the command explicitly overrides them to 2 GB and
two workers. Only one Gradle invocation held the build slot at a time. An environment-only init script sets
`Test.systemProperty('skiko.library.path', '/tmp/chats-skiko')`; that directory contains `libskiko-linux-x64.so` and
`icudtl.dat` extracted from the pinned Skiko 0.156.0 runtime JAR. This avoids writing the sandbox's read-only home
cache. The first cached library tried was incompatible; its generated JVM error report was removed. Neither
workaround changes repository build files.

`tool/format-changed.sh` is absent, so only touched Kotlin files were formatted with the available ktfmt 0.64
Kotlin style formatter. `git diff --check` passes. Gradle still emits warnings in existing kit code; no claim of a
warning-free build. No APK, emulator, live-server or iOS validation was performed.

## Local commits and sandbox limitation

Ordinary staging and committing were attempted but the sandbox denies the shared worktree metadata at
`/home/eslam/Storage/Code/cloud-launch/.git/worktrees/codexfix-chats/index.lock` as read-only. The checkout branch
therefore remains at `066a00be`, with the fixes present in its working tree.

To preserve the requested small commits without changing protected metadata, a separate local Git artifact repository
at `/tmp/chats-fixes-history.git` contains the exact changed files, based on `066a00be`, on `refs/heads/chats-fixes`:

- `19584e86` — serialize return brief acknowledgements safely.
- `b15a6220` — revoke stale opening reset confirmations.
- `0c112dc7` — scope and acknowledge only rendered return briefs.
- A final documentation commit records this report.

Each subject ends in `[skip ci]` and each commit has both requested trailers. The complete series is exported to
`/tmp/chats-fixes.bundle` and verified locally. Commit-policy and privacy scans pass on the series. Nothing was pushed.
