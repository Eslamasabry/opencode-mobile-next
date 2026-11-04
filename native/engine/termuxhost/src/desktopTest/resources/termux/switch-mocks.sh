claim_direct_lock() { return 0; }
process_group() { printf '%s' "$$"; }
process_start() { printf 123; }
ubuntu_usable() { return 0; }
require_setup_space() { return 0; }
termux-wake-lock() { :; }
termux-wake-unlock() { :; }
release_setup_lock() { :; }
runtime_version() { cat "$OC_DIR/binary-$(managed_runtime)" 2>/dev/null; }
install_runtime() {
  printf '%s\n' "$(managed_runtime)" >> "$OC_DIR/installed"
  [ "${FAIL_INSTALL:-0}" != 1 ] || fail_setup 'Test install failed'
  printf '%s' "$1" > "$OC_DIR/binary-$(managed_runtime)"
}
# Runs the real history admission script, with the container's OpenCode 2 data
# folder redirected into the fixture home.
proot-distro() {
  printf x >> "$OC_DIR/proot-calls"
  sed "s#/root/.oc-opencode2#$HOME/oc2-root#g" | sh -s
}
stop_server() {
  [ "${REFUSE_STOP:-0}" != 1 ] || return 75
  printf stopped >> "$OC_DIR/stops"
}
start_server() {
  [ "${FAIL_START:-0}" != 1 ] || fail_setup 'Test authentication failed'
  write_state ready 'Authenticated fixture ready' "$CURRENT_PORT" proot "$1" '' completed
  SETUP_SUCCEEDED=1
}
