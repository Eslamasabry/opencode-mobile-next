#!/usr/bin/env python3
"""Cut the golden-test fixture from the real ARB files.

Copies the keys below, verbatim and in ARB order, from lib/l10n/app_<lang>.arb into
src/test/resources/fixture/lib/l10n/. The template keeps its @key metadata.
FixtureDriftTest checks the fixture still matches the real files.

  python3 -I native/tools/strings/fixture-tools/cut_fixture.py   (from the repo root)
"""
import json, os, sys

KEYS = [
    "servicesIntro",                # apostrophe
    "paseoPasswordHelp",            # double quotes + apostrophe
    "chatUiReferenceAlreadyAdded",  # leading @, ar starts with U+200E
    "agentAccountPercentUsed",      # literal % after a placeholder
    "libraryModelsAgentsTitle",     # &
    "monitorRequestSummary",        # newline, 4 placeholders
    "aiteamBringInDone",            # ar reorders {project}/{commit}
    "chatUiRateLimitCountdown",     # zh reorders {attempt}/{time}
    "terminalShowEarlier",          # decimalPattern int
    "chatUiDraftsQueued",           # whole plural in all 7 languages, ar six categories
    "chatUiQuestionsSummary",       # embedded plural with prefix text
    "kitNeedsYouWaitingSpoken",     # =0 and =1 in every language
    "chatTurnAllFilesMore",         # plain in en, plural in ar (promotion)
    "appExitServerStopped",         # routed to strings_os (Android-safe escaping)
    "teamUiGateAnswerRunRetry",     # routed by the team key prefix
    "servicesTitle",                # read by no fixture Dart file: unused
    "runningPhoneCpu",              # ICU select: not convertible
    "e7SettingsStorageSummary",     # two plurals: not convertible
    "setupCancelConnection",        # Arabic orphan (absent from en)
]

root = os.getcwd()
src = os.path.join(root, "lib", "l10n")
dst = os.path.join(root, "native", "tools", "strings", "src", "test", "resources", "fixture", "lib", "l10n")
os.makedirs(dst, exist_ok=True)
for name in sorted(os.listdir(src)):
    if not (name.startswith("app_") and name.endswith(".arb")):
        continue
    with open(os.path.join(src, name), encoding="utf-8") as f:
        arb = json.load(f)
    out = {"@@locale": arb["@@locale"]}
    for k, v in arb.items():
        if k in KEYS or (k.startswith("@") and not k.startswith("@@") and k[1:] in KEYS):
            out[k] = v
    with open(os.path.join(dst, name), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(name, sum(1 for k in out if not k.startswith("@")), file=sys.stderr)
