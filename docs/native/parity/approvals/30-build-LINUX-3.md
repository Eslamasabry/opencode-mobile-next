RESULT: APPROVED WITH FIXES

# Approval: job 30-build-LINUX-3 (LINUX migration, part 3 of 3)

Branch `native/p12x/job-30-build-LINUX-3`. Reviewed: the job's non-merge commits over
`origin/native/p12-head` (`a1e37cd0` code + report, `fefa4e17`..`6bf93cd1` report only).

## Merge

`git merge --no-ff origin/native/p12-head` was a no-op ("Already up to date"): the branch was cut
from the current P12 head tip `cde0afb4`, so there was no merge commit and no conflict to resolve.

## Review

The 57 rows are accounted for (17 built now / 12 already there / 28 blocked). The blocked rows are
honest: Termux setup/runtime management, the general terminal and the Termux migration journey
have no reachable native owner, and D7 (migrate-out only) is respected; nothing deprecated was
re-enabled. Spot-checked "already there" citations (InstallPrelude activation recovery, wired cold
recovery in BuiltinLinux, finisher host/runtime refusal, SetupEngine prelude injection, planning
and record parsing) hold.

The built-now work is real and reachable: `NativeLinuxTools` is contributed as `LinuxToolsSource`,
`LinuxToolsViewModel` is in the view-model map, and Linux Home shows the Installed tools panel with
a guarded, confirmed Python-tools removal sharing the setup lane. Device preflight (ABI → memory →
space, unknown readings permit trying) gates the native start; setup shows the weighted kit
progress bar, component rows and typed error sentences; setup timings go to `PerfTrace`; cleanup
runs on one-use scan receipts. UI is kit-only (KitRow/KitNotice/KitConfirm/KitProgressView/
KitDetailsFold), copy is in `strings_linux_tools.xml` in English and Arabic, details pass the
Redactor. No placeholder, stub, TODO or dead path was found in the new code.

## Findings and fixes (commit `e8195572`)

1. **B-LINUX-208 not effective at start.** `SetupRunner.start` throws `SetupPersistenceException`,
   but `classifyRuntime` mapped it to `Generic`, so the engine rendered `HostRefused`. Fixed:
   mapped to `RuntimeProblem.SetupPersistence`.
2. **B-LINUX-193 diverged from Flutter.** Unknown ownership offered Continue setup (which cannot
   repair it) and showed the raw `runtime_ownership_unknown` code as copy. Fixed: recovery body
   only, with Open app settings (new `openAppSettings` on the tools source/actions, Android intent
   in `NativeLinuxTools`); `linuxSetupOwnershipCode` removed.
3. **B-LINUX-199 gap.** The lane-bypassing cancel was a silent no-op when no engine had been built
   yet. Fixed: falls back to the lane command, which builds the engine and cancels.
4. **B-LINUX-210 edge.** A `totalMem` of 0 became "0 MB, too little memory". Fixed: unknown.
5. **B-LINUX-211 / 188 / 201 copy.** Byte figures were raw ("Free 230000000 bytes"). Fixed: Flutter's
   `phoneSetupPreflightLowSpaceBody` with the LTR-isolated Linux size and Flutter's Open Storage
   settings action; formatted sizes in the removal consequence and component byte rows. The unused
   `linuxToolsFreeBytes` string was removed; English and Arabic updated together.
6. **Cleanup receipts piled up.** Each storage page load wrote four receipt files under
   `/root/.oc-builtin` and only a used one was deleted. Fixed: a new scan removes the previous
   scan's receipts (exact mktemp names only).

The report rows were corrected to match.

## Remaining (not blocking)

- The preflight notice appears after a refused start, on Linux Home; Flutter also shows it on its
  welcome page before starting. The refused start also shows the generic start-failure sentence
  under it.
- A file name containing a newline under `/tmp` could split into path fragments in the cleanup
  scan; fragments still pass the category, owner and identity checks, so the worst case is removing
  a different own temp file.

## Verification

Review only. Following the owner's order (code first, tests later), no tests were written or run
and no APK was built. The job reported `:androidApp:compilePreviewDebugKotlin compileKotlinDesktop`
passing for `a1e37cd0`; the approval fixes were **not compiled** here (Google Maven blocked) and
need the build VPS compile.
