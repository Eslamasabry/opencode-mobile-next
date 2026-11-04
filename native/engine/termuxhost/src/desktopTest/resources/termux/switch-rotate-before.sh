install_runtime() {
  printf '%s\n' 'CURRENT_SWITCH_INSTALL'
  printf '%s' "$1" > "$OC_DIR/binary-$(managed_runtime)"
}
