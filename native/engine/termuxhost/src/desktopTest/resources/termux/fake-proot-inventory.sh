#!/bin/bash
printf '%s\n' "$1" "$2" "$3" "$4" "$5" > "$PROOT_CALLS"
if [ "$PROBE_EXIT" != 0 ]; then exit "$PROBE_EXIT"; fi
printf 'ubuntu=installed\nversion=1.18.29\n'
