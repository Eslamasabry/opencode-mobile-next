package io.github.eslamasabry.oc.engine.runtime.history

/**
 * Port of lib/domain/opencode_history_copy.dart (the OpenCode 1 → OpenCode 2 chat copy), the engine's source of truth.
 *
 * OpenCode 2 converts OpenCode 1 history by itself on every start, but only from the `opencode.db` it finds in its own
 * `$XDG_DATA_HOME/opencode/`. The switch gives OpenCode 2 an isolated data folder, so without this copy the old chats
 * stay behind. Every script here is the Flutter text byte for byte (POSIX `sh`, run inside Ubuntu):
 *
 * - [COPY] runs only when OpenCode 2 has no `opencode.db` yet, so it never overwrites and a second switch is a no-op;
 *   it copies (never moves) `opencode.db`, `-wal` and `-shm` through a scratch folder, commits a provenance receipt
 *   ([RECEIPT_NAME]) before the database and places `opencode.db` last; any failure exits [FAILED_EXIT].
 * - [SWITCH_CHECK] is the read-only admission for an explicit move, run before the old server stops.
 */
public object HistoryCopyScripts {
    /** The exit code of every refusal (EX_IOERR). Launchers wrap the copy with `|| exit 74`. */
    public const val FAILED_EXIT: Int = 74

    /** OpenCode 2's isolated data folder inside Ubuntu. */
    public const val OC2_DATA_DIR: String = "/root/.oc-opencode2/data/opencode"

    /** OpenCode 1's data folder, relative to `$HOME`. */
    public const val OC1_DATA_DIR_IN_HOME: String = ".local/share/opencode"

    /** The provenance receipt the copy leaves next to the copied database. */
    public const val RECEIPT_NAME: String = ".oc1-import-source.sha256"

    /** The three files of one SQLite history, the database last. */
    public val HISTORY_FILES: List<String> = listOf("opencode.db", "opencode.db-wal", "opencode.db-shm")

    /** The stderr marker of a failed copy (`[oc-history-copy-failed]`). */
    public const val COPY_FAILED_MARKER: String = "[oc-history-copy-failed]"

    /** The stderr marker of a refused explicit move (`[oc-history-switch-blocked]`). */
    public const val SWITCH_BLOCKED_MARKER: String = "[oc-history-switch-blocked]"

    /**
     * Flutter `openCode1HistorySwitchBlockedMessage`: the exact text the switch check prints when OpenCode 2 already has a
     * separate history. It is script data (the admission matches it), never shown by the engine.
     */
    public const val SWITCH_BLOCKED_TEXT: String =
        "Cannot move your conversations because OpenCode 2 already has a separate " +
            "history. The automatic move stopped to protect your conversations. " +
            "Both histories are safe; neither was changed."

    /** Flutter `_historyCopyProvenance`: the source signature and receipt match helpers both scripts share. */
    public const val PROVENANCE: String = """history_source_signature() (
  cd "${'$'}1" || exit 1
  for history_file in opencode.db opencode.db-wal opencode.db-shm; do
    [ ! -L "${'$'}history_file" ] || exit 1
    if [ -e "${'$'}history_file" ]; then
      [ -f "${'$'}history_file" ] || exit 1
      sha256sum "${'$'}history_file" || exit 1
    else
      printf 'absent %s\n' "${'$'}history_file"
    fi
  done
)
history_copy_receipt_matches() {
  [ -f "${'$'}oc2_dir/.oc1-import-source.sha256" ] &&
  [ ! -L "${'$'}oc2_dir/.oc1-import-source.sha256" ] || return 1
  history_signature=${'$'}(history_source_signature "${'$'}oc1_dir") || return 1
  [ "${'$'}(cat "${'$'}oc2_dir/.oc1-import-source.sha256")" = "${'$'}history_signature" ]
}
"""

    private const val COPY_BODY: String = """oc1_dir="${'$'}{HOME:-/root}/.local/share/opencode"
oc2_dir=/root/.oc-opencode2/data/opencode
if [ ! -e "${'$'}oc2_dir/opencode.db" ] && [ -f "${'$'}oc1_dir/opencode.db" ]; then
  history_copy_failed() {
    echo '[oc-history-copy-failed] Your earlier conversations could not be copied. The original data is still safe on this phone. Free some space and try again.' >&2
    exit 74
  }
  [ ! -L "${'$'}oc1_dir" ] && [ ! -L "${'$'}oc2_dir" ] || history_copy_failed
  original_signature=${'$'}(history_source_signature "${'$'}oc1_dir") || history_copy_failed
  # Only our own verified interrupted copy may leave target sidecars.
  if [ -e "${'$'}oc2_dir/opencode.db-wal" ] || [ -e "${'$'}oc2_dir/opencode.db-shm" ]; then
    history_copy_receipt_matches || history_copy_failed
    for history_file in opencode.db-wal opencode.db-shm; do
      if [ -e "${'$'}oc2_dir/${'$'}history_file" ]; then
        [ ! -L "${'$'}oc2_dir/${'$'}history_file" ] &&
        cmp -s "${'$'}oc1_dir/${'$'}history_file" "${'$'}oc2_dir/${'$'}history_file" || history_copy_failed
      fi
    done
    rm -f "${'$'}oc2_dir/opencode.db-wal" "${'$'}oc2_dir/opencode.db-shm" || history_copy_failed
  fi
  mkdir -p "${'$'}oc2_dir" || history_copy_failed
  stage="${'$'}oc2_dir/.oc1-copy.${'$'}${'$'}"
  # Do not reuse an interrupted staging folder or follow a stale symlink.
  mkdir "${'$'}stage" || history_copy_failed
  if ! { cp "${'$'}oc1_dir/opencode.db" "${'$'}stage/opencode.db" \
    && { [ ! -f "${'$'}oc1_dir/opencode.db-wal" ] || cp "${'$'}oc1_dir/opencode.db-wal" "${'$'}stage/opencode.db-wal"; } \
    && { [ ! -f "${'$'}oc1_dir/opencode.db-shm" ] || cp "${'$'}oc1_dir/opencode.db-shm" "${'$'}stage/opencode.db-shm"; }; }; then
    rm -rf "${'$'}stage"
    history_copy_failed
  fi
  # Commit provenance before the database. A crash after the database rename
  # can then safely retry admission, including after OC2 has converted it.
  copied_signature=${'$'}(history_source_signature "${'$'}stage") || history_copy_failed
  [ "${'$'}copied_signature" = "${'$'}original_signature" ] || history_copy_failed
  [ "${'$'}(history_source_signature "${'$'}oc1_dir")" = "${'$'}original_signature" ] || history_copy_failed
  printf '%s\n' "${'$'}copied_signature" > "${'$'}stage/.oc1-import-source.sha256" || history_copy_failed
  mv "${'$'}stage/.oc1-import-source.sha256" "${'$'}oc2_dir/.oc1-import-source.sha256" || history_copy_failed
  if ! { { [ ! -f "${'$'}stage/opencode.db-wal" ] || mv "${'$'}stage/opencode.db-wal" "${'$'}oc2_dir/opencode.db-wal"; } \
    && { [ ! -f "${'$'}stage/opencode.db-shm" ] || mv "${'$'}stage/opencode.db-shm" "${'$'}oc2_dir/opencode.db-shm"; } \
    && mv "${'$'}stage/opencode.db" "${'$'}oc2_dir/opencode.db"; }; then
    rm -rf "${'$'}stage"
    history_copy_failed
  fi
  rm -rf "${'$'}stage"
fi
"""

    private const val SWITCH_BODY: String = """oc1_dir="${'$'}{HOME:-/root}/.local/share/opencode"
oc2_dir=/root/.oc-opencode2/data/opencode
# Unknown locations must not be mistaken for an absent history.
for history_dir in "${'$'}oc1_dir" "${'$'}oc2_dir"; do
  if [ -L "${'$'}history_dir" ] || { [ -e "${'$'}history_dir" ] && { [ ! -d "${'$'}history_dir" ] || [ ! -r "${'$'}history_dir" ] || [ ! -x "${'$'}history_dir" ]; }; } \
    || [ -L "${'$'}history_dir/opencode.db" ]; then
    echo '[oc-history-switch-blocked] Cannot check your earlier conversations safely. The automatic move stopped. Neither history was changed.' >&2
    exit 74
  fi
done
if [ -e "${'$'}oc1_dir/opencode.db" ] && [ -e "${'$'}oc2_dir/opencode.db" ] && ! history_copy_receipt_matches; then
  echo '[oc-history-switch-blocked] Cannot move your conversations because OpenCode 2 already has a separate history. The automatic move stopped to protect your conversations. Both histories are safe; neither was changed.' >&2
  exit 74
fi
"""

    /** Flutter `openCode1HistoryCopyScript`: provenance + the guarded copy. */
    public const val COPY: String = PROVENANCE + COPY_BODY

    /** Flutter `openCode1HistorySwitchCheckScript`: provenance + the read-only explicit-move admission. */
    public const val SWITCH_CHECK: String = PROVENANCE + SWITCH_BODY

    /**
     * The in-app server's isolated-data prelude (Flutter `BuiltinLinux.serverScript(runtime = opencode2)`): the isolated
     * folders exist first, then the copy runs in a subshell whose failure stops the start (`|| exit 74`), so a copy
     * failure can never be swallowed and OpenCode 2 never starts without its history.
     */
    public val builtinServerPrelude: String =
        "mkdir -p $OC2_DATA_DIR /root/.oc-opencode2/cache " +
            "/root/.oc-opencode2/state /root/.oc-opencode2/config/opencode\n" +
            "unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT\n" +
            "export XDG_DATA_HOME=/root/.oc-opencode2/data\n" +
            "export XDG_CACHE_HOME=/root/.oc-opencode2/cache\n" +
            "export XDG_STATE_HOME=/root/.oc-opencode2/state\n" +
            "export XDG_CONFIG_HOME=/root/.oc-opencode2/config\n" +
            "export OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode\n" +
            "export OPENCODE_DB=$OC2_DATA_DIR/opencode.db\n" +
            "(\n$COPY) || exit $FAILED_EXIT\n"

    /**
     * The Termux runner's step (lib/termux/scripts/manager_script.dart): inside Ubuntu, after the isolated folders and
     * before the server's `proot-distro login --work-dir /root/projects`, its log piped to the manager; `|| exit 74`.
     */
    public val termuxRunnerStep: String =
        "  proot-distro login opencode-ubuntu -- mkdir -p $OC2_DATA_DIR \\\n" +
            "    /root/.oc-opencode2/cache /root/.oc-opencode2/state /root/.oc-opencode2/config/opencode || exit 74\n" +
            "  # Bring the OpenCode 1 chats along before OpenCode 2's first start.\n" +
            "  proot-distro login opencode-ubuntu -- sh -s 2>&1 <<'OC_HISTORY_COPY' | \"${'$'}manager\" write-log server || exit 74\n" +
            COPY +
            "OC_HISTORY_COPY\n"

    /**
     * The admission as either host runs it (Flutter `_checkHistorySwitch` in lib/state/phone_host.dart): a subshell so
     * its refusal exit cannot escape before the transport captures stderr, with stderr folded into the output.
     */
    public val admissionScript: String = "(\n$SWITCH_CHECK\n) 2>&1"
}
