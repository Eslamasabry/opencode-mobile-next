ubuntu_rootfs_exists() { [ "${EXISTING_ROOTFS:-0}" = 1 ]; }
[ "$(setup_runtime)" = opencode2 ] || exit 81
# Status and restart never adopt the fresh setup default.
[ "$(managed_runtime)" = opencode1 ] || exit 82
EXISTING_ROOTFS=1
[ "$(setup_runtime)" = opencode1 ] || exit 83
EXISTING_ROOTFS=0
printf 'version=1.19.0\n' > "$STATE"
[ "$(setup_runtime)" = opencode1 ] || exit 84
printf opencode2 > "$RUNTIME_FILE"
[ "$(setup_runtime)" = opencode2 ] || exit 85
