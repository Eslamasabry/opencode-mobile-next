#!/usr/bin/env bash
# gradle_retry.sh <log-file> <gradlew args...>   (run from native/)
# Runs ./gradlew, tees the output to <log-file>, and runs it again (up to 4 tries, 30/90/180 s apart) ONLY when the failure
# was a download problem (HTTP 5xx, timeouts, connection resets while resolving or fetching). A real compile or test
# failure is returned at once. Why: GitHub release and Maven redirects answered 504 for hours on 2026-11-09 and Gradle
# reported it as "Could not isolate parameters ... ClasspathEntrySnapshotTransform", which looks like a build bug.
log=$1; shift
rm -f "$log"
for wait in 0 30 90 180; do
  [ "$wait" != 0 ] && { echo "::warning::Gradle download failed, retrying in ${wait}s"; sleep "$wait"; }
  set +e
  ./gradlew "$@" 2>&1 | tee -a "$log"
  rc=${PIPESTATUS[0]}
  set -e
  [ "$rc" = 0 ] && exit 0
  # only the part of the log written by this try
  if tail -n 400 "$log" | grep -Eq 'Received status code 5[0-9][0-9]|Could not GET|Could not HEAD|Read timed out|Connection (reset|timed out)|Premature end of Content-Length|Could not download|Could not resolve (all|org\.|com\.|io\.|androidx)|SSLHandshakeException|UnknownHostException|Gateway Time-out'; then
    continue
  fi
  exit "$rc"
done
exit "$rc"
