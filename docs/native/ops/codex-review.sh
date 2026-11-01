#!/bin/bash
# codex-review.sh <name> <git-range> <paths...> : async read-only Codex Sol review of native code
name=$1; range=$2; shift 2
R=/home/eslam/Storage/Code/wt/native-reviews; W=$R/src-$name
rm -rf "$W"; git clone -q --no-checkout --depth 50 file:///home/eslam/Storage/Code/cloud-launch "$W" && git -C "$W" sparse-checkout set --no-cone /native/ /docs/native/ /docs/design/native-rewrite-2026-11-01/plan/ && git -C "$W" checkout -q native/m0-2026-11-01
cd "$W" || exit 1
codex exec -m gpt-6.1-sol -c model_reasoning_effort=high -c mcp_oauth_credentials_store=file -s read-only -C "$W" --skip-git-repo-check -o "$R/$name.md" \
"You are a second-eye code reviewer (a different model from the authors). READ-ONLY. Repo: $W (native Kotlin Multiplatform rewrite of OpenCode Mobile; plan in docs/design/native-rewrite-2026-11-01/, plan/01 binding). Review the code changed in git range $range, focusing on: $*.
Find real bugs: wrong behaviour vs the plan section and the catalog rows it claims, concurrency and cancellation bugs, crash-safety and data-loss risks, security and privacy leaks (credentials, paths, addresses in logs or analytics), API misuse, tests that pass without proving anything (check at least 3 tests would fail if the code were wrong), and places that will not compile together with native/core contracts.
Output markdown: verdict line, then findings BLOCKER/MAJOR/MINOR with file:line, problem, evidence, concrete fix. No praise." > "$R/$name.log" 2>&1 < /dev/null
rm -rf "$W"; echo "done $name: $(head -1 $R/$name.md 2>/dev/null)"
