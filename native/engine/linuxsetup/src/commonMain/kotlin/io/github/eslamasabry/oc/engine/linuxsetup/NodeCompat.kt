package io.github.eslamasabry.oc.engine.linuxsetup

/**
 * Node.js on phones whose system crashes it (issues #117/#119; twin of the Flutter fix/117-adapt
 * `lib/builtin/setup/node_compat.dart`). The Node install runs [EARLY_CHECK] before the download and [START_CHECK]
 * after unpacking: Node must print its version, run a script that uses OpenSSL and the thread pool, and run npm. When
 * that crashes, the safe modes are tried in [NodeCompatMode.order]; the first that passes is saved in [STATE_FILE]
 * (its mode, and `proot-compat` on a second line when it only passed in compatibility mode) and [ENV_FILE], and
 * `/usr/local/bin/node` becomes a wrapper that applies it. Nothing passing ends with exit 139 (the runner tries the step
 * again in compatibility mode) or, already there, with [FAILURE_LINE].
 *
 * The same text is in :engine:runtime (`NodeCompatScripts`, checked equal by :feature:phonesetup) and its server part in
 * :android:linux (`NodeCompatState`): a phone moved from Flutter and a new install keep the same files inside Linux.
 */
public object NodeCompat {
    public const val STATE_FILE: String = "/etc/oc-setup/node-compat"
    public const val ENV_FILE: String = "/etc/oc-setup/node-compat.env"

    /** The second line of [STATE_FILE] when the server proot must also start in compatibility mode. */
    public const val PROOT_COMPAT: String = "proot-compat"

    /** The last line of a Node install that found no way to run Node.js (mapped to plain words by the app). */
    public const val FAILURE_LINE: String = "[oc] Node.js cannot run on this phone's system, also in a safe mode"

    /** Exit status that makes the setup runner run the step again in compatibility mode (128 + SIGSEGV). */
    public const val RETRY_EXIT: Int = 139

    /** The only variables [ENV_FILE] may set; anything else in it is ignored. */
    public val ENV_NAMES: List<String> = listOf("OPENSSL_armcap", "UV_THREADPOOL_SIZE")

    /**
     * The shell functions every Node step uses. Paths can be moved for tests with `oc_compat_dir`, `oc_node_bin`
     * and `oc_node_link`; nothing else is read from the environment except `GLIBC_TUNABLES`, which tells a run in
     * compatibility mode.
     */
    public const val FUNCTIONS: String =
        $$"""
oc_compat_dir=${oc_compat_dir:-/etc/oc-setup}
oc_node_bin=${oc_node_bin:-/opt/node/bin/node}
oc_node_link=${oc_node_link:-/usr/local/bin/node}
oc_node_script="const c=require('crypto');c.createHash('sha256').update('oc').digest('hex');c.pbkdf2('oc','oc',1000,32,'sha256',e=>{if(e)process.exit(3);require('fs').stat('/',f=>process.exit(f?4:0))})"
# One probe: at most 30 s, its output dropped, only the exit code printed.
oc_probe() {
  oc_probe_id=$1; oc_probe_show=$2; shift 2
  oc_probe_rc=0
  if command -v timeout >/dev/null 2>&1; then
    ( timeout 30 "$@"; exit $? ) </dev/null >/dev/null 2>&1 || oc_probe_rc=$?
  else
    ( "$@"; exit $? ) </dev/null >/dev/null 2>&1 || oc_probe_rc=$?
  fi
  echo "[oc] probe $oc_probe_id $oc_probe_rc # $oc_probe_show"
  return "$oc_probe_rc"
}
oc_in_compat() {
  case "${GLIBC_TUNABLES:-}" in *rseq=0*) return 0 ;; esac
  return 1
}
oc_node_mode_env() {
  case "$1" in armcap|both) echo OPENSSL_armcap=0 ;; esac
  case "$1" in single|both) echo UV_THREADPOOL_SIZE=1 ;; esac
  return 0
}
# Node 24 refuses these in NODE_OPTIONS, so they go on its command line.
oc_node_mode_args() {
  case "$1" in single|both) echo '--single-threaded --v8-pool-size=1' ;; esac
  return 0
}
oc_node_try() {
  oc_try_env=$(oc_node_mode_env "$1" | tr '\n' ' ')
  oc_try_args=$(oc_node_mode_args "$1")
  oc_try_show=$(printf '%s' "$oc_try_env$oc_node_bin $oc_try_args" | tr -s ' ' | sed 's/ $//')
  oc_npm_cli="$(dirname "$oc_node_bin")/../lib/node_modules/npm/bin/npm-cli.js"
  oc_probe "$1-version" "$oc_try_show --version" env $oc_try_env "$oc_node_bin" $oc_try_args --version &&
    oc_probe "$1-start" "$oc_try_show -e <start check>" \
      env $oc_try_env "$oc_node_bin" $oc_try_args -e "$oc_node_script" &&
    oc_probe "$1-npm" "$oc_try_show npm --version" env $oc_try_env "$oc_node_bin" $oc_try_args "$oc_npm_cli" --version
}
# Makes the node command apply the mode: a plain link without one, a wrapper with one.
oc_node_save() {
  mkdir -p "$oc_compat_dir" "$(dirname "$oc_node_link")"
  if [ "$1" = none ] && ! oc_in_compat; then
    rm -f "$oc_compat_dir/node-compat" "$oc_compat_dir/node-compat.env"
    ln -sfn "$oc_node_bin" "$oc_node_link"
    return 0
  fi
  oc_node_mode_env "$1" > "$oc_compat_dir/node-compat.env.new"
  echo "$1" > "$oc_compat_dir/node-compat.new"
  if oc_in_compat; then echo proot-compat >> "$oc_compat_dir/node-compat.new"; fi
  mv -f "$oc_compat_dir/node-compat.env.new" "$oc_compat_dir/node-compat.env"
  mv -f "$oc_compat_dir/node-compat.new" "$oc_compat_dir/node-compat"
  if [ "$1" = none ]; then
    ln -sfn "$oc_node_bin" "$oc_node_link"
    return 0
  fi
  {
    echo '#!/bin/sh'
    echo '# OpenCode Mobile setup: Node.js in a safe mode for this phone.'
    oc_node_mode_env "$1" | sed 's/^/export /'
    echo "exec '$oc_node_bin' $(oc_node_mode_args "$1") \"\$@\""
  } > "$oc_node_link.new"
  chmod 755 "$oc_node_link.new"
  rm -f "$oc_node_link"
  mv -f "$oc_node_link.new" "$oc_node_link"
}
# The first way Node.js runs here: as it is, then each safe mode.
oc_node_choose() {
  for oc_mode in none armcap single both; do
    if oc_node_try "$oc_mode"; then
      oc_node_save "$oc_mode"
      [ "$oc_mode" = none ] || echo "[oc] Node.js runs here in a safe mode ($oc_mode)"
      return 0
    fi
  done
  return 1
}
oc_node_start_check() {
  oc_stage 'Checking that Node.js starts'
  oc_node_choose && return 0
  if oc_in_compat; then
    echo "[oc] Node.js cannot run on this phone's system, also in a safe mode" >&2
    exit 1
  fi
  echo '[oc] Node.js crashed on this phone; trying again in compatibility mode' >&2
  exit 139
}
# Before the download: threads and OpenSSL, which crash Node.js on the phones of #117.
oc_node_early_check() {
  oc_early_rc=0
  oc_probe early-sort 'sort --parallel=4' sh -c 'seq 1 20000 | sort --parallel=4 -n' || oc_early_rc=$?
  if command -v openssl >/dev/null 2>&1; then
    oc_probe early-openssl 'openssl sha256' sh -c 'echo oc | openssl sha256' || true
    oc_probe early-openssl-armcap 'OPENSSL_armcap=0 openssl sha256' \
      sh -c 'echo oc | OPENSSL_armcap=0 openssl sha256' || true
  fi
  if [ "$oc_early_rc" -ge 128 ] && ! oc_in_compat; then
    echo '[oc] This phone crashes programs that use threads; trying again in compatibility mode' >&2
    exit 139
  fi
  return 0
}
"""

    /** Exports the saved safe mode before a program that may start Node.js (the server, agents). */
    public const val SERVER_ENV: String =
        $$"""if [ -r /etc/oc-setup/node-compat.env ]; then
  while IFS='=' read -r oc_compat_key oc_compat_value; do
    case "$oc_compat_key" in
      OPENSSL_armcap|UV_THREADPOOL_SIZE) export "$oc_compat_key=$oc_compat_value" ;;
    esac
  done < /etc/oc-setup/node-compat.env
fi
"""

    /** Called before the Node download. */
    public const val EARLY_CHECK: String = "oc_node_early_check\n"

    /** Called after unpacking, in place of the old `npm config set prefix` (which now runs after it). */
    public const val START_CHECK: String = "oc_node_start_check\n"

    /** [STATE_FILE]'s text as a value; null when there is no saved mode or it is not one this app wrote. */
    public fun parse(text: String?): NodeCompatState? {
        val lines = text?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        val mode = NodeCompatMode.entries.firstOrNull { it.id == lines.firstOrNull() } ?: return null
        return NodeCompatState(mode, prootCompat = lines.drop(1).contains(PROOT_COMPAT))
    }

    /**
     * The decision [FUNCTIONS] makes, as a value: the first mode in [NodeCompatMode.order] that [passes], or a give-up
     * that asks for compatibility mode first ([NodeCompatDecision.RetryInCompat]) unless the run is already there.
     */
    public fun decide(inCompat: Boolean, passes: (NodeCompatMode) -> Boolean): NodeCompatDecision {
        val mode = NodeCompatMode.order.firstOrNull(passes)
        return when {
            mode != null -> NodeCompatDecision.Runs(NodeCompatState(mode, prootCompat = inCompat))
            inCompat -> NodeCompatDecision.CannotRun
            else -> NodeCompatDecision.RetryInCompat
        }
    }

    /** Whether a setup failure is this one: its reason or the end of its log carries [FAILURE_LINE]. */
    public fun cannotRun(reason: String?, logTail: String = ""): Boolean =
        reason?.contains(FAILURE_LINE) == true || logTail.contains(FAILURE_LINE)
}

/** How Node.js is started on this phone ([id] is the first line of [NodeCompat.STATE_FILE]). */
public enum class NodeCompatMode(public val id: String) {
    None("none"),
    Armcap("armcap"),
    Single("single"),
    Both("both");

    public companion object {
        public val order: List<NodeCompatMode> = listOf(None, Armcap, Single, Both)
    }
}

/** A saved mode, and whether it only worked with the proot in compatibility mode. */
public data class NodeCompatState(val mode: NodeCompatMode, val prootCompat: Boolean = false)

public sealed interface NodeCompatDecision {
    public data class Runs(val state: NodeCompatState) : NodeCompatDecision

    public data object RetryInCompat : NodeCompatDecision

    public data object CannotRun : NodeCompatDecision
}
