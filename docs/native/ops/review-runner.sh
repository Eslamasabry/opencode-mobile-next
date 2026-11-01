#!/bin/bash
# review-runner.sh: works through review-queue.txt one Codex review at a time, until the queue is empty
S=/home/eslam/Storage/tmp/claude-tmp/claude-1000/-home-eslam-Storage-Code-oc-app/87a8d964-900c-48f3-a841-cd593d87ac4c/scratchpad
while true; do
  line=$(while read -r n r rest; do grep -qx "$n" $S/reviewed.txt || { echo "$n|$r|$rest"; break; }; done < $S/review-queue.txt)
  [ -z "$line" ] && { echo "queue empty"; exit 0; }
  IFS='|' read -r n r rest <<< "$line"; echo "$n" >> $S/reviewed.txt
  $S/codex-review.sh "$n" "$r" "$rest"
done
