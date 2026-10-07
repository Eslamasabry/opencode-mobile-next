BLOCKED: coordinator-owned catalog Claude integration, BB installer quiescence and stale-lock recovery, native cold-start integration, and full app next-start proof remain required.

# BC4 — pinned component update recovery contract

The BC branch implements shell journaling for Paseo, legacy `/opt/oc-claude/claude`, and native OpenCode. The current phone catalog uses `AgentPhoneScripts` instead of the legacy Claude installer. BC4 is therefore incomplete until the coordinator applies the two proposed patches below and qualifies the actual app startup path. The patches are **unapplied and uncompiled**. `git apply --check` checks their applicability only; shell/emulator helper evidence in `docs/qa/BC4-2026-10-07/README.md` does not prove native cold-start recovery.

## Coordinator dependency and admission

BB must establish that the previous installer and every descendant have stopped before calling `BuiltinLinux.admitComponentUpdateRecoveryAfterInstallerQuiescence()`. This internal method checks tracked children but cannot prove that an installer orphaned by app/PRoot death is absent. The patch intentionally refuses pending recovery without that admission. Do not infer quiescence from a new app PID, missing parent, `--kill-on-exit`, a stale `setup.json`, or an empty in-memory process list. Do not kill by command pattern.

BB must supply durable installer ownership with exact PID/start-time or an equivalent lifecycle receipt and prove descendant quiescence before rollback. The existing catalog `.lock-claude` is a directory without a PID/owner receipt and survives SIGKILL. Its presence cannot distinguish a live installer from a stale lock. The coordinator must replace it with a qualified lock/ownership mechanism, migrate legacy locks only after the same quiescence proof, and never casually delete the directory. The proposed Claude check acquires the existing lock too, so a check cannot race an ordinary installer; unresolved legacy locks deliberately keep both checks and installs unavailable.

Cold recovery runs once before the first `prootCommand` of a `BuiltinLinux` instance; it must not run before each in-process launch while an installer owns a pending transaction. `configure()` resets recovery/admission because it creates a new rootfs generation. BB must integrate admission before the first pending-recovery attempt; the proposed patch does not add an unproven admission call. Update the fixed native target list whenever shipped versions/installation paths change, retaining coverage for any version that may still have a pending receipt.

## States, copy, and receipt semantics

No new gateway or UI methods are required. Setup uses its existing pending/running/done/failed states; recovering receipts never turns a component into “verified.” Unknown quiescence, unsafe ancestry, an invalid receipt, missing last-good plus missing active, or failed restore blocks launch. Return plain words and a Setup/Retry action; technical filesystem text never becomes the error. Preserve these fixed projections through coordinator-owned channel/error boundaries:

| Condition | Plain copy |
| --- | --- |
| Restore unavailable or failed | A component update could not be restored. Run setup again. |
| Claude active-command probe fails after restore succeeds | Claude could not finish updating. Run setup again. |
| Claude lock unavailable | Another Claude install may still be running. Wait and try again. |

`component_updates.dart` is the receipt authority: sibling `<target>.oc-pending` contains `existing\n` or `new\n`; `<target>.oc-good` retains one prior generation. Activate writes and syncs the receipt before moving active to good and candidate to active. A pending `existing` restores good; if good is absent but active exists, it accepts the already-restored/not-yet-moved active idempotently. A pending `new` removes the incomplete first install. Code is recovered before links; successful activation probes the active command and commits code before link. Native recovery reads at most 16 receipt bytes with `O_NOFOLLOW`, validates ancestor/code-root types, unlinks symlink objects without following their guest targets, and bounds recursive deletion. It follows the process-interruption semantics; power-loss durability still needs qualification.

## Storage, migration, and removal

This adds private sibling receipts/backups; it changes no profile credential or transcript format. Existing successful installs need no conversion until their next update. One retained installed generation consumes extra storage; preserve the BC2 pre-install guard and account for actual backup/candidate space when changing size estimates. A failed first install has no last-good generation and returns to absent. OpenCode removes its previous npm wrappers only after both commits. Temporary candidates and probe/download logs can be cleaned on failure; no successful path deletes `.oc-good`.

Backups are shared installed programs, not per-profile account data. Profile deletion must continue to delete its own credentials/homes without deleting shared rollback programs. Any component/rootfs removal implementation must remove that component's active, `.new`, `.oc-good`, `.oc-pending`, and `.oc-pending.new` only under verified writer quiescence and safe fixed ancestry. Old catalog `.old.<pid>` directories are legacy leftovers, not valid journal backups; do not silently adopt or delete them without qualification.

## Exact coordinator patch

Extract this single `diff` block to a temporary `.patch` and run `git apply --check <patch>` from the repository root before coordinator application. This proposal modifies only coordinator/BB-owned `BuiltinLinux.kt` and coordinator-owned `agent_scripts.dart`; BC did not edit either source. Other catalog agents retain their prior installation branch. After application, the coordinator must format/analyze, run focused native/catalog regressions, then prove interrupted Claude/Paseo/OpenCode updates through an actual cold app start on `emulator-5554` under its shared lock. Include orphan-installer and SIGKILL stale-lock cases; do not mark BC4 done from helper-only proof.

```diff
--- a/android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt
+++ b/android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt
@@ -269,6 +269,161 @@
             .start().also { processes.add(it); processConfinement[it] = prootIsConfined }
     }

+    private var componentUpdatesRecovered = false
+    private var componentUpdateRecoveryQuiescent = false
+    private val componentUpdateFailure = "A component update could not be restored. Run setup again."
+
+    /** BB must prove that every previous installer descendant has stopped first. */
+    @Synchronized
+    internal fun admitComponentUpdateRecoveryAfterInstallerQuiescence() {
+        check(processes.none { it.isAlive } && agentProcessProfiles.keys.none { it.isAlive }) {
+            componentUpdateFailure
+        }
+        componentUpdateRecoveryQuiescent = true
+    }
+
+    private data class ComponentUpdateTarget(val guest: String, val directory: Boolean, val link: Boolean = false)
+
+    private fun componentUpdateStat(file: File): android.system.StructStat? = try {
+        Os.lstat(file.absolutePath)
+    } catch (error: ErrnoException) {
+        if (error.errno == OsConstants.ENOENT) null else throw error
+    }
+
+    /** All ancestors are fixed guest paths; neither traversal nor deletion follows links. */
+    private fun componentUpdatePath(guest: String): File? {
+        check(guest.startsWith('/') && guest.split('/').none { it == ".." })
+        for (ancestor in listOf(context.filesDir, home)) {
+            val stat = componentUpdateStat(ancestor) ?: return null
+            check(OsConstants.S_ISDIR(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode))
+        }
+        var parent = rootfs
+        val segments = guest.removePrefix("/").split('/')
+        for (segment in segments.dropLast(1)) {
+            val stat = componentUpdateStat(parent) ?: return null
+            check(OsConstants.S_ISDIR(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode))
+            parent = File(parent, segment)
+        }
+        val stat = componentUpdateStat(parent) ?: return null
+        check(OsConstants.S_ISDIR(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode))
+        return File(parent, segments.last())
+    }
+
+    private fun componentUpdateReceipt(file: File): String {
+        val before = componentUpdateStat(file) ?: error(componentUpdateFailure)
+        check(OsConstants.S_ISREG(before.st_mode) && before.st_size in 1L..16L)
+        val descriptor = Os.open(file.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
+        val bytes = ByteArray(17)
+        val length = java.io.FileInputStream(descriptor).use { input ->
+            val opened = Os.fstat(descriptor)
+            check(OsConstants.S_ISREG(opened.st_mode) && opened.st_size in 1L..16L &&
+                opened.st_ino == before.st_ino && opened.st_dev == before.st_dev)
+            var total = 0
+            while (total < bytes.size) {
+                val count = input.read(bytes, total, bytes.size - total)
+                if (count < 0) break
+                total += count
+            }
+            total
+        }
+        check(length in 1..16)
+        return when (String(bytes, 0, length, Charsets.US_ASCII)) {
+            "existing", "existing\n" -> "existing"
+            "new", "new\n" -> "new"
+            else -> error(componentUpdateFailure)
+        }
+    }
+
+    private fun componentUpdateSync(directory: File) {
+        val descriptor = Os.open(directory.absolutePath,
+            OsConstants.O_RDONLY or OsConstants.O_DIRECTORY or OsConstants.O_NOFOLLOW, 0)
+        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
+    }
+
+    private fun componentUpdateDelete(file: File, depth: Int = 0, budget: IntArray = intArrayOf(100_000)) {
+        val stat = componentUpdateStat(file) ?: return
+        check(depth <= 64 && --budget[0] >= 0)
+        if (OsConstants.S_ISDIR(stat.st_mode)) {
+            val children = file.listFiles() ?: error(componentUpdateFailure)
+            for (child in children) componentUpdateDelete(child, depth + 1, budget)
+            Os.rmdir(file.absolutePath)
+        } else {
+            // This unlinks a symlink itself, including a dangling command link.
+            Os.unlink(file.absolutePath)
+        }
+    }
+
+    private fun componentUpdateKind(file: File, target: ComponentUpdateTarget): Boolean {
+        val stat = componentUpdateStat(file) ?: return false
+        return if (target.link) OsConstants.S_ISLNK(stat.st_mode) || OsConstants.S_ISREG(stat.st_mode)
+        else if (target.directory) OsConstants.S_ISDIR(stat.st_mode)
+        else OsConstants.S_ISREG(stat.st_mode)
+    }
+
+    private fun recoverComponentUpdate(target: ComponentUpdateTarget) {
+        val active = componentUpdatePath(target.guest) ?: return
+        val pending = File(active.path + ".oc-pending")
+        if (componentUpdateStat(pending) == null) return
+        val receipt = componentUpdateReceipt(pending)
+        val good = File(active.path + ".oc-good")
+        when (receipt) {
+            "existing" -> if (componentUpdateStat(good) != null) {
+                check(componentUpdateKind(good, target))
+                componentUpdateDelete(active)
+                Os.rename(good.absolutePath, active.absolutePath)
+            } else {
+                // Preparation did not move the old generation, or a prior
+                // recovery restored it but was killed before clearing receipt.
+                check(componentUpdateKind(active, target))
+            }
+            "new" -> componentUpdateDelete(active)
+        }
+        val parent = active.parentFile ?: error(componentUpdateFailure)
+        componentUpdateSync(parent)
+        Os.unlink(pending.absolutePath)
+        try { componentUpdateSync(parent) } catch (_: Exception) { }
+    }
+
+    /** Runs once per cold instance; never rolls back a live in-process installer. */
+    private fun recoverColdComponentUpdates() {
+        if (componentUpdatesRecovered) return
+        try {
+            // Current app-owned pins only. Extend this list when a catalog pin
+            // changes; do not scan arbitrary user or network-authored paths.
+            val code = listOf(
+                ComponentUpdateTarget("/opt/opencode", true),
+                ComponentUpdateTarget("/opt/opencode2", true),
+                ComponentUpdateTarget("/opt/oc-claude/claude", false),
+                ComponentUpdateTarget("/home/oc/.local/share/oc-paseo/0.9.2-82d16f9c432d", true),
+                ComponentUpdateTarget("/home/oc/.local/share/oc-agents/claude/2.1.283", true),
+            )
+            val links = listOf("/usr/local/bin/opencode", "/usr/local/bin/opencode2",
+                "/usr/local/bin/claude", "/home/oc/.local/bin/paseo", "/home/oc/.local/bin/claude")
+                .map { ComponentUpdateTarget(it, false, link = true) }
+            for (target in code) {
+                componentUpdatePath(target.guest)?.let { active ->
+                    if (componentUpdateStat(active) != null) check(componentUpdateKind(active, target))
+                }
+            }
+            val targets = code + links
+            val pending = targets.any { target ->
+                componentUpdatePath(target.guest)?.let { active ->
+                    componentUpdateStat(File(active.path + ".oc-pending")) != null
+                } == true
+            }
+            if (pending) {
+                check(componentUpdateRecoveryQuiescent && processes.none { it.isAlive } &&
+                    agentProcessProfiles.keys.none { it.isAlive })
+                // Restore every code generation before any command link.
+                for (target in code) recoverComponentUpdate(target)
+                for (target in links) recoverComponentUpdate(target)
+            }
+            componentUpdatesRecovered = true
+        } catch (_: Exception) {
+            throw IllegalStateException(componentUpdateFailure)
+        }
+    }
+
     /** proot's own path: the program a terminal session (LocalTerminal.kt) starts. */
     val prootPath: String get() = "$nativeDir/libproot.so"

@@ -280,6 +435,7 @@
     @Synchronized
     fun prootCommand(program: List<String>, agentUser: Boolean = false): List<String> {
         check(!installingRuntime) { "Runtime installation is still running" }
+        recoverColdComponentUpdates()
         projectStorage.prepare()
         val agentRoot = if (agentUser) PhoneAgentPaths.prepare(context.filesDir, "linux/agent-root-view").apply {
             PhoneAgentPaths.prepare(this, "projects")
@@ -1866,6 +2022,8 @@

     /** What proot-distro does after unpacking, trimmed to what Ubuntu needs. */
     private fun configure() {
+        componentUpdatesRecovered = false
+        componentUpdateRecoveryQuiescent = false
         val etc = File(rootfs, "etc")
         File(etc, "resolv.conf").apply {
             delete()
--- a/lib/builtin/agents/agent_scripts.dart
+++ b/lib/builtin/agents/agent_scripts.dart
@@ -1,4 +1,5 @@
 import '../../domain/agent_catalog.dart';
+import '../setup/component_updates.dart';
 import 'paseo_scripts.dart';

 /// Installs catalog pins as Linux user oc. Does not sign in, launch agents, or
@@ -99,9 +100,48 @@
   static String _versionPattern(String version) =>
       _quote('(^|[^0-9A-Za-z.])${RegExp.escape(version)}([^0-9A-Za-z.]|\$)');

+  static const _claudeUpdateProbe = r'''
+oc_claude_probe_active() {
+  oc_probe=$(mktemp -d /home/oc/.local/share/oc-agents/.probe.XXXXXX)
+  oc_output=$(cd "$oc_probe" && timeout 30s env -i HOME="$oc_probe" XDG_CONFIG_HOME="$oc_probe" PATH="$PATH" \
+    "$oc_bin" --version 2>/dev/null) || oc_output=
+  rm -rf "$oc_probe"
+  printf '%s' "$oc_output" | grep -Eq "$oc_version_pattern"
+}
+oc_claude_update_failed() {
+  oc_restore_failed=0
+  oc_update_recover "$oc_final" || oc_restore_failed=1
+  oc_update_recover "$oc_bin" || oc_restore_failed=1
+  if [ "$oc_restore_failed" != 0 ]; then
+    echo '[oc] A component update could not be restored. Run setup again.' >&2
+  else
+    echo '[oc] Claude could not finish updating. Run setup again.' >&2
+  fi
+  exit 1
+}
+''';
+
   static String check(AgentDescriptor agent) {
     final recipe = _recipe(agent);
+    final recovery = agent.id == 'claude' && recipe.executable == 'claude'
+        ? '''$componentUpdatePrelude
+# Checks and installers hold the same lock across recovery and probes.
+# BB owns stale-lock recovery; a successful mkdir proves only our admission.
+oc_check_lock=/home/oc/.local/share/oc-agents/.lock-claude
+mkdir "\$oc_check_lock" 2>/dev/null || {
+  echo '[oc] Another Claude install may still be running. Wait and try again.' >&2
+  exit 1
+}
+trap 'rmdir "\$oc_check_lock" >/dev/null 2>&1 || true' EXIT
+trap 'exit 129' HUP
+trap 'exit 130' INT
+trap 'exit 143' TERM
+oc_update_recover "\$oc_final"
+oc_update_recover "\$oc_bin"
+'''
+        : '';
     return '''${_selection(agent, recipe)}
+$recovery
 oc_version_pattern=${_versionPattern(recipe.version)}
 $_probe
 [ -f "\$oc_final/.oc-pin" ] && [ ! -L "\$oc_final/.oc-pin" ]
@@ -115,9 +155,53 @@
   static String install(AgentDescriptor agent) {
     final recipe = _recipe(agent);
     final version = _quote(recipe.version);
+    final claudeUpdate = agent.id == 'claude' && recipe.executable == 'claude';
+    final updatePrelude = claudeUpdate
+        ? '$componentUpdatePrelude\n$_claudeUpdateProbe'
+        : '';
+    final recoverAfterLock = claudeUpdate
+        ? r'''
+oc_update_recover "$oc_final"
+oc_update_recover "$oc_bin"
+'''
+        : '';
+    final fastLinkUpdate = claudeUpdate
+        ? r'''
+  ln -s "$oc_final/launch" "$oc_link" || oc_claude_update_failed
+  oc_update_activate "$oc_bin" "$oc_link" || oc_claude_update_failed
+  oc_claude_probe_active || oc_claude_update_failed
+  oc_update_commit "$oc_bin" || oc_claude_update_failed
+'''
+        : r'''
+  ln -s "$oc_final/launch" "$oc_link"
+  mv -Tf "$oc_link" "$oc_bin"
+''';
+    final activation = claudeUpdate
+        ? r'''
+ln -s "$oc_final/launch" "$oc_link" || oc_claude_update_failed
+oc_update_activate "$oc_final" "$oc_stage" || oc_claude_update_failed
+oc_update_activate "$oc_bin" "$oc_link" || oc_claude_update_failed
+oc_claude_probe_active || oc_claude_update_failed
+oc_update_commit "$oc_final" || oc_claude_update_failed
+oc_update_commit "$oc_bin" || oc_claude_update_failed
+'''
+        : r'''
+if [ -e "$oc_final" ]; then mv "$oc_final" "$oc_old"; fi
+if ! mv "$oc_stage" "$oc_final"; then
+  [ ! -d "$oc_old" ] || mv "$oc_old" "$oc_final"
+  exit 1
+fi
+if ! ln -s "$oc_final/launch" "$oc_link" || ! mv -Tf "$oc_link" "$oc_bin"; then
+  rm -rf "$oc_final"
+  [ ! -d "$oc_old" ] || mv "$oc_old" "$oc_final"
+  exit 1
+fi
+rm -rf "$oc_old"
+''';
     return '''${_selection(agent, recipe)}
 oc_version_pattern=${_versionPattern(recipe.version)}
 $_probe
+$updatePrelude
 command -v python3 >/dev/null && command -v timeout >/dev/null || {
   echo '[oc] Agent setup needs the Linux tools first' >&2; exit 1
 }
@@ -137,11 +221,11 @@
 trap 'exit 129' HUP
 trap 'exit 130' INT
 trap 'exit 143' TERM
+$recoverAfterLock
 [ ! -L "\$oc_final" ] && [ ! -L "\$oc_stage" ] || exit 1
 if [ -f "\$oc_final/.oc-pin" ] && [ ! -L "\$oc_final/.oc-pin" ] &&
    [ "\$(cat "\$oc_final/.oc-pin")" = "\$oc_pin" ] && oc_probe_version "\$oc_final"; then
-  ln -s "\$oc_final/launch" "\$oc_link"
-  mv -Tf "\$oc_link" "\$oc_bin"
+$fastLinkUpdate
   oc_version $version
   exit 0
 fi
@@ -172,17 +256,7 @@
   printf '#!/bin/sh\\nexec "%s/payload/%s" "\$@"\\n' "\$oc_final" "\$oc_member" > "\$oc_stage/launch"
 fi
 chmod 700 "\$oc_stage/launch"
-if [ -e "\$oc_final" ]; then mv "\$oc_final" "\$oc_old"; fi
-if ! mv "\$oc_stage" "\$oc_final"; then
-  [ ! -d "\$oc_old" ] || mv "\$oc_old" "\$oc_final"
-  exit 1
-fi
-if ! ln -s "\$oc_final/launch" "\$oc_link" || ! mv -Tf "\$oc_link" "\$oc_bin"; then
-  rm -rf "\$oc_final"
-  [ ! -d "\$oc_old" ] || mv "\$oc_old" "\$oc_final"
-  exit 1
-fi
-rm -rf "\$oc_old"
+$activation
 oc_version $version
 ''';
   }
```
