#!/bin/bash
# Second, fresh GitHub-hosted container: no source checkout, no network.
set -euo pipefail
test -f /.dockerenv
test "$(uname -m)" = aarch64
test ! -e /src
exec > >(tee /facts/bundle-test.log) 2>&1
mkdir -p /empty/bundle /trial/home /trial/agent /trial/project
tar -xzf /facts/unharnessed-arm64.tar.gz -C /empty/bundle
python=$(find /empty/bundle/python -path '*/bin/python3.11' | head -n 1)
test -n "$python"
start=$(date +%s)
env -i HOME=/trial/home PATH=/usr/bin:/bin PYTHONDONTWRITEBYTECODE=1 \
  "$python" /empty/bundle/bootstrap.py /trial/agent
printf '%s\n' "$(( $(date +%s) - start ))" > /facts/locked-bootstrap-seconds.txt
env -i HOME=/trial/home PATH=/usr/bin:/bin PYTHONDONTWRITEBYTECODE=1 \
  "$python" /probe/bundle_probe.py /empty/bundle /trial/agent /trial/project
du -sb /empty/bundle /trial/agent /trial/project > /facts/first-reply-disk-bytes.txt
