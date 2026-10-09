# APK 2203 final-pass scope

Coordinator GO: candidate `ec778efa3de1730402f096fc126e16fd2e83264e`,
APK `/home/eslam/Storage/tmp/oc-apk-share/oc-2203.apk`, SHA256
`82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`,
local signer `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`.
The full suite at the candidate is the coordinator's evidence in
[full-suite-2026-10-09](../full-suite-2026-10-09/README.md); this device run does
not repeat or extend that full-suite claim.

The one batch reservation starts with installed baseline 2202 and ends by
restoring normal 2203. It uses the runner's exclusive flock with a 3600-second
acquisition timeout. No owner-phone access, APK build, release or push.

The BA install/removal rows select fx, one target at a time. Fresh-install
preparation may remove that selected target's idle partial payload through the
product UI; it cannot delete payloads with shell commands or remove an active
agent. The stale QA90001 storage-floor artifact is intentionally skipped.
BB5 requires QA-enabled2203 plus instrumentation, not the normal release or
older QA2202. The initially available artifacts do not meet that requirement.

FQ9 uses BC's reviewed canonical noReply fixture plan, on the actual installed
baseline, and retains its private ownership receipt outside the checkout.
FQ3 writes current-build evidence and its separate protocol matrix. Background
preparation sends only the fixed 45-minute foreground-tool prompt through the
app composer; success still requires both real 5/30-minute checkpoints and
terminal evidence before cleanup. There is no shortened dwell or heartbeat.

FB1 may provision a fresh named AVD under Storage only with at least 6 GiB
available RAM, 16 GiB free on Storage, 2 GiB free on root, available ports and
KVM. It uses the existing API35 image, 3 GiB guest RAM, two cores, no snapshot
reuse, and stops only its own emulator process. It never imports accounts.
The app's own offline simulated demo is the only recording surface; the GIF
remains private pending visual review and is not exported or published.

[Driver source hashes](driver-sources.json) freeze the actual device attempt.
[Dry-run](dry-run.json) includes all ten rows and both full background checkpoints.

Executed once from this checkout (private stdout/stderr retained outside Git):

```bash
python3 tool/qa/final_pass.py --execute \
  --candidate-apk /home/eslam/Storage/tmp/oc-apk-share/oc-2203.apk \
  --candidate-build 2203 \
  --normal-apk /home/eslam/Storage/tmp/oc-apk-share/oc-2203.apk \
  --inputs /home/eslam/Storage/tmp/fq9-final-inputs-2203/rows-merged.json \
  --output docs/qa/final-pass-2026-10-09
```

Runner PID 3916990 held the single reservation through normal restoration.
It exited 1. Its owned fresh emulator PID 3925310 also exited; both were absent
at the final host check. No second reservation or device retry was performed.
