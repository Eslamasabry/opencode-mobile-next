#!/usr/bin/env python3
"""AUDIT TOOLING ONLY (branch audit/style-coherence, 2026-10-21).

Files the raw renders of run_style_audit.sh as <root>/<screen>/<combo>.png,
one full phone screen per image.

    python3 tool/capture/audit/sort_renders.py [root]
"""
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else
            '/home/eslam/Storage2/launch-footage/audit-styles')

# (harness dir, file regex) -> screen name
MAP = [
    ('surfaces-review', r'home-', 'conversations'),
    ('surfaces-review', r'chat-', 'chat-question-card'),
    ('surfaces-review', r'sheet-', 'composer-tools-sheet'),
    ('vision-chat', r'after-dark', 'chat-working'),
    ('vision-chat', r'after-plus-dark', 'chat-working-plus'),
    ('vision-chat-approval', r'command-dark', 'chat-permission-card'),
    ('vision-chat-approval', r'question-dark', 'chat-question-answer'),
    ('vision-inbox', r'main-dark', 'inbox'),
    ('surfaces-review', r'settings-', 'settings-hub'),
    ('vision-settings-hub', r'main-dark', 'conversations-this-phone'),
    ('j1-settings-more', r'appearance-settings', 'appearance'),
    ('vision-server-switcher', r'main-dark', 'server-switcher-sheet'),
    ('vision-model-picker', r'oc2-costs-dark', 'model-picker-sheet'),
    ('vision-files', r'main-dark', 'files-list'),
    ('vision-files', r'reader-dark', 'files-reader'),
    ('after', r'1-changes-dark', 'diff-changes'),
    ('after', r'2-git-sheet-dark', 'git-sheet'),
    ('vision-terminal', r'main-dark', 'terminal'),
    ('vision-preview', r'main-dark', 'preview'),
    ('opus', r'four-agents-dark', 'new-conversation'),
    ('vision-welcome', r'welcome-dark', 'welcome'),
    ('vision-this-phone', r'running-dark', 'this-phone'),
    ('vision-scheduled', r'main-dark', 'scheduled'),
    ('vision-scheduled', r'empty-dark', 'scheduled-empty'),
    ('vision-report-problem', r'main-dark', 'report-problem'),
    ('renders', r'tips-page', 'help-tips'),
    ('vision-states', r'empty-dark', 'empty-state'),
    ('vision-states', r'offline-home-dark', 'offline-home'),
    ('vision-team-task', r'working-dark', 'ai-team-task'),
    ('vision-team-task', r'question-dark', 'ai-team-question'),
    ('audit-extras', r'menu', 'menu'),
    ('audit-extras', r'dialog', 'dialog'),
    ('audit-extras', r'undo', 'undo-snackbar'),
    ('audit-extras', r'confirm', 'confirm-sheet'),
    ('audit-s1', r'folder', 'folder-browser-sheet'),
    ('audit-s1', r'term', 'term-bubble'),
    ('audit-s1', r'tip', 'tip-bubble'),
    ('audit-s4', r'suggestions', 'composer-suggestions'),
    ('s6-transcript', r'transcript', 'transcript-technical'),
]


def main():
    raw = ROOT / 'raw'
    filed = 0
    for combo_dir in sorted(p for p in raw.iterdir() if p.is_dir()):
        combo = combo_dir.name
        for png in combo_dir.rglob('*.png'):
            harness = png.parent.name
            for h, pattern, screen in MAP:
                if h == harness and re.search(pattern, png.name):
                    dest = ROOT / screen / f'{combo}.png'
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(png, dest)
                    filed += 1
                    break
            else:
                print('unmapped', png.relative_to(raw))
    print('filed', filed)


if __name__ == '__main__':
    main()
