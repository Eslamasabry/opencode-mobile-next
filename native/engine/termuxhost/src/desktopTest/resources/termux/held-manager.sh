#!/usr/bin/env bash
case "$1" in
  restart)
    for _ in {1..160}; do
      [ ! -f "$HOME/.oc/release-dispatch-fixture" ] || break
      sleep 0.05
    done
    touch "$HOME/.oc/dispatch-fixture-finished"
    ;;
  write-log) cat >/dev/null ;;
esac
