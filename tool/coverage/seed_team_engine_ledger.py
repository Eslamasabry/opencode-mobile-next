#!/usr/bin/env python3
"""Writes test/fixtures/coverage/team_engine_ledger.json: one decision per field
path of the team engine's workspace. Paths come from build/coverage/engine_paths.json
(written by the coverage test run); decisions are made here."""
import json, re

OUT = 'test/fixtures/coverage/team_engine_ledger.json'
paths = json.load(open('build/coverage/engine_paths.json'))

ID = 'ignored: an internal id the engine uses to match records; nothing a person reads'
COUNTER = 'ignored: a version counter the engine uses to refuse a stale edit; nothing a person reads'
PINNED = 'ignored: kept so Merge and Promote name the exact commits the person reviewed; the page shows the change, not the hashes'
BOOKKEEPING = 'ignored: the engine\'s own bookkeeping; it only decides when a control appears'
SAME = 'ignored: a copy of the spec draft kept for its history; the draft\'s own fields are the ones drawn'

D = {}
def put(path, decision):
    D[path] = decision

settings = {
    'keepWorkingScreenOff': 'shown: Keep working with the screen off',
    'mode': 'shown: Parallel agents',
    'reviewLevel': 'shown: Milestones and risky points',
    'chargingOnly': 'shown: Only while charging',
    'autoFix': 'shown: Fix findings automatically',
    'budget.chosen': 'shown: Set limits',
    'budget.unlimited': 'shown: No limit',
}
for p in paths:
    for prefix in ('defaultSettings.', 'projects[].settings.'):
        if p.startswith(prefix) and p[len(prefix):] in settings:
            put(p, settings[p[len(prefix):]])

exact = {
    'revision': COUNTER, 'projects[].revision': COUNTER,
    'schemaVersion': 'ignored: the engine\'s protocol version; the app refuses a version it does not know, and nothing is shown',
    'simulated': 'ignored: tells a real engine from the demo; the demo shows a Demo label instead',
    'projects[].simulated': 'ignored: tells a real engine from the demo; the demo shows a Demo label instead',
    'projects[].budgetWarning': 'shown: Approaching your budget',
    'projects[].id': ID, 'servers[].id': ID, 'roles[].id': ID,
    'projects[].repos[].id': ID, 'projects[].repos[].serverId': ID,
    'projects[].repos[].path': 'ignored: the project folder on that computer; a long technical path, the repo\'s name says which it is',
    'projects[].repos[].devCommit': PINNED, 'projects[].repos[].mainCommit': PINNED,
    'projects[].repos[].checkCommand': 'ignored: the command the engine runs to check work; it is set up on the computer',
    'projects[].repos[].sharedRemote': 'ignored: whether the repo has a remote the engine pushes to; the page shows merges and promotions, not remotes',
    'projects[].specDraft.milestones[].id': ID, 'projects[].specVersions[].milestones[].id': ID,
    'projects[].specDraft.milestones[].accepted': 'shown: Done',
    'projects[].specVersions[].milestones[].accepted': SAME,
    'projects[].specDraft.approvedBy': 'ignored: the page says the spec is approved and when, not by whom (always the person)',
    'projects[].specVersions[].approvedBy': SAME,
    'projects[].specDraft.approvedAt': 'shown: Spec approved',
    'projects[].specVersions[].approvedAt': SAME,
    'projects[].phases[].id': ID, 'projects[].phases[].milestoneId': ID,
    'projects[].phases[].risky': 'shown: risky',
    'projects[].phases[].accepted': 'ignored: shown only by the Accept phase button being absent',
    'projects[].tasks[].criterionResults[].status': 'shown: Met',
    'projects[].tasks[].id': ID, 'projects[].tasks[].phaseId': ID, 'projects[].tasks[].roleId': ID,
    'projects[].tasks[].repoId': ID, 'projects[].tasks[].serverId': ID,
    'projects[].tasks[].status': 'shown: Review',
    'projects[].tasks[].dependsOn[]': 'ignored: ids of the tasks it waits for; the board draws dependencies as links between cards',
    'projects[].tasks[].branch': 'ignored: the working branch name, a technical detail that sits in the task\'s Details fold',
    'projects[].tasks[].reason': 'ignored: why the engine parked the task; the page says the task is waiting and what to do',
    'projects[].tasks[].changedAt': 'ignored: when the task last changed; the page shows its messages\' own times',
    'projects[].tasks[].steps': 'ignored: a step count the page does not draw',
    'projects[].tasks[].tokens': 'ignored: tokens spent on the task; the page shows the project\'s cost, not each task\'s',
    'projects[].tasks[].fixRounds': 'ignored: how many fix rounds ran; the Settings page shows the limit',
    'projects[].tasks[].affected': BOOKKEEPING,
    'projects[].tasks[].findings[].id': ID,
    'projects[].tasks[].findings[].status': 'shown: Open findings',
    'projects[].tasks[].messages[].id': ID,
    'projects[].tasks[].messages[].actor': 'ignored: who wrote the message; the page draws the person\'s messages on one side and the team\'s on the other',
    'projects[].tasks[].messages[].at': 'shown: 8:10',
    'projects[].tasks[].diff': 'ignored: the raw change text; View changes turns it into a list of changed files',
    'projects[].requests[].id': ID, 'projects[].requests[].taskId': ID, 'projects[].requests[].phaseId': ID,
    'projects[].requests[].kind': 'ignored: question, failure or other kind decides which controls the card has',
    'projects[].requests[].title': 'ignored: an answered question leaves the Needs you list; it is shown while open',
    'projects[].requests[].createdAt': 'ignored: an answered question leaves the Needs you list',
    'projects[].requests[].answered': 'ignored: an answered question leaves the Needs you list',
    'projects[].requests[].answer': 'ignored: an answered question leaves the Needs you list; the answer stays in the timeline',
    'projects[].mergeQueue[].id': ID, 'projects[].mergeQueue[].taskId': ID, 'projects[].mergeQueue[].repoId': ID,
    'projects[].mergeQueue[].status': 'shown: Waiting',
    'projects[].mergeQueue[].checksPassed': 'ignored: decides whether Merge is offered; the row says what it waits for',
    'projects[].receipts[].id': ID, 'projects[].receipts[].repoId': ID,
    'projects[].receipts[].kind': 'shown: Promoted to main',
    'projects[].receipts[].at': 'shown: 10h ago',
    'projects[].receipts[].actor': 'ignored: who promoted; always the person on this phone',
    'projects[].timeline[].id': ID, 'projects[].timeline[].taskId': ID,
    'projects[].timeline[].actor': 'shown: You',
    'projects[].timeline[].at': 'shown: 10h ago',
    'projects[].planningState.jobId': ID,
    'projects[].planningState.stage': 'ignored: the planner\'s stage decides whether the plan is ready to review; the page says so in its own words',
    'projects[].planningState.reason': 'ignored: why the planner stopped; shown only when it failed, as the failure notice',
    'projects[].planningState.updatedAt': 'ignored: when the planner last moved; the plan page shows nothing about it',
    'projects[].timelineTruncated': 'ignored: the timeline keeps only the latest events; the page does not say older ones were dropped',
    'projects[].spent': 'ignored: the project\'s total cost; shown on the Cost row when usage is reported',
    'projects[].spentToday': 'ignored: today\'s cost; shown on the Cost row when usage is reported',
    'projects[].spendDay': 'ignored: the day today\'s cost counts for; the engine resets it at midnight',
    'projects[].digestReadAt': 'ignored: when the digest was marked read; decides whether Since you were away shows',
    'projects[].updatedAt': 'shown: 10h ago',
    'projects[].planApproved': 'ignored: decides whether the plan editor says Approve or Apply; nothing is drawn from the flag itself',
    'projects[].quickTask': 'ignored: tells a quick task from a project; the Give a quick task page differs, nothing drawn from the flag',
    'projects[].usageReported': 'ignored: decides whether the Cost row is drawn',
    'servers[].phone': 'shown: This phone',
    'servers[].online': 'shown: Reachable',
    'servers[].chatWaiting': 'ignored: a chat is waiting on that computer; the lanes line says how many are busy',
    'servers[].reason': 'ignored: why a computer is not reachable; the page says it is not reachable',
    'servers[].memoryMb': 'shown: 1024 MB',
    'roles[].instructions': 'ignored: the role\'s instructions are edited inside the role, not listed',
    'roles[].fallbackModel': 'ignored: the fallback model is edited inside the role, not listed',
    'roles[].readOnly': 'shown: Read-only',
}
for p, v in exact.items():
    assert p in paths, p
    put(p, v)

missing = [p for p in paths if p not in D]
print('default shown:', len(missing))
for p in missing:
    D[p] = 'shown'
json.dump(D, open(OUT, 'w'), indent=2, sort_keys=True, ensure_ascii=False)
