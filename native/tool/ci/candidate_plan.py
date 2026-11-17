#!/usr/bin/env python3
"""Publish the candidate's complete task inventory and actual Python self-test results."""
import json, os, re, subprocess, sys

lane, out, ci_log, release_log, tools_log = sys.argv[1:]
if lane != 'candidate':
    sys.exit(0)


def passed(path):
    text = open(path).read()
    count = re.search(r'^Ran (\d+) tests? in ', text, re.M)
    return {'tests': int(count[1]) if count else 0, 'passed': bool(count and re.search(r'^OK$', text, re.M))}


os.makedirs(out, exist_ok=True)
tools = re.search(r'^release tools: (\d+) passed, (\d+) failed$', open(tools_log).read(), re.M)
json.dump({'lane': lane, 'sha': os.environ['GITHUB_SHA'],
           'detekt_hook': 'tool/ci/detekt_lane.sh <base-ref> --all',
           'detekt_entry_present': os.path.isfile('tool/ci/detekt_lane.sh'),
           'desktop_tasks': subprocess.check_output(['bash', 'tool/ci/shard_tasks.sh', '0', '1'], text=True).split(),
           'android_tasks': subprocess.check_output(['bash', 'tool/ci/android_tasks.sh', lane], text=True).split(),
           'self_tests': {'ci': passed(ci_log), 'release': passed(release_log),
                          'release_tools': {'tests': int(tools[1]) if tools else 0,
                                            'passed': bool(tools and int(tools[1]) > 0 and int(tools[2]) == 0)}}},
          open(os.path.join(out, 'candidate-plan.json'), 'w'))
