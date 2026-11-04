CURRENT_OPERATION=targetOperation
CURRENT_RUNTIME=opencode2
write_switch opencode1 opencode2 starting
write_state stopped 'Fixture' 4096
# Inspect journal directly: no ready state may erase the previous runtime.
[ "$(switch_value switch_operation)" = targetOperation ] || exit 84
[ -f "$SWITCH_FILE" ] || exit 85
printf '%s' opencode2 > "$RUNTIME_FILE"
# ready status is only authoritative while its tracked server identity exists.
# No PID means it must become failed and preserve the unresolved journal.
write_state ready 'Fixture' 4096 proot 18600
status
[ -f "$SWITCH_FILE" ] || exit 86
