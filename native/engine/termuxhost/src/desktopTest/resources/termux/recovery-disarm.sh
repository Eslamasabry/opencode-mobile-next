{{functions}}
CURRENT_OPERATION=attempt-1
CURRENT_RECOVERY=permit
write_state restarting synthetic 4096
printf '%s' permit > "$RECOVERY_PERMIT"
stop_setup() { [ ! -e "$RECOVERY_PERMIT" ]; return 75; }
if recovery_disarm permit; then exit 99; fi
[ ! -e "$RECOVERY_PERMIT" ]
stop_setup() { [ ! -e "$RECOVERY_PERMIT" ]; printf 'setup\n'; }
stop_server() { printf 'server\n'; }
recovery_disarm permit
[ "$(read_state_value phase)" = stopped ]
