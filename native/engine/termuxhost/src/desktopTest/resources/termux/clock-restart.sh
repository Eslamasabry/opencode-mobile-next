printf 'phase=stopped\nstarted_at=100\n' > "$STATE"
claim_direct_lock() { [ "${REJECT_LOCK:-0}" = 0 ]; }
recovery_preflight() { return 1; }
release_setup_lock() { :; }
cleanup_setup() { :; }
process_start() { printf '1'; }
process_group() { printf '%s' "$$"; }
ubuntu_usable() { return 1; }
date() { printf '%s' "$CLOCK"; }
CLOCK=200
if (restart 4096 'invalid!'); then exit 82; fi
[ "$(read_state_value started_at)" = 100 ] || exit 83
REJECT_LOCK=1
if (restart 4096 duplicate); then exit 84; fi
[ "$(read_state_value started_at)" = 100 ] || exit 85
REJECT_LOCK=0
if (restart 4096 revoked expected token); then exit 86; fi
[ "$(read_state_value started_at)" = 100 ] || exit 87
# Accepted operations stop at stubbed Ubuntu preflight, after writing the clock.
if (restart 4096 first); then exit 88; fi
[ "$(read_state_value started_at)" = 200 ] || exit 89
CLOCK=300
if (restart 4096 second); then exit 90; fi
[ "$(read_state_value started_at)" = 300 ] || exit 91
