# oc-migration-command
export LC_ALL=C
 timeout -k 2 90 bash -s 2>/dev/null <<'OC_MIGRATION_SCRIPT'
set -euo pipefail
umask 077
@@SCRIPT@@
OC_MIGRATION_SCRIPT
