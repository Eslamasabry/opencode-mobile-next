#!/usr/bin/env python3
"""Builds the native-vs-Flutter pair images for the UI-SERVERS-SETTINGS parity review.

Usage: python3 -I make_pairs.py <repo root>
Reads native/<screen>-<variant>.png (written by the ParityCapture desktop tests) and the Flutter goldens named in
PAIRS, and writes pairs/<screen>-<variant>.png: native on the left, Flutter on the right, each labelled.
"""
import os, sys
from PIL import Image, ImageDraw

root = sys.argv[1]
here = os.path.join(root, 'docs/native/qa/ui-parity/ui-servers-settings')
R, G = 'test/revamp/goldens/', 'test/goldens/'
# (native capture, Flutter golden)
PAIRS = [
    ('settings-hub-light-en', R + 'settings_hub_loaded_light.png'),
    ('settings-hub-dark-en', R + 'settings_hub_loaded_dark.png'),
    ('settings-hub-light-en-text2x', R + 'p310_settings_hub_text200_dark.png'),
    ('settings-hub-light-en-tablet', R + 'settings_hub_loaded_1280x800_light.png'),
    ('settings-group-server-dark-en', R + 'p310_settings_group_connections_dark.png'),
    ('settings-group-agent-dark-en', R + 'p310_settings_group_ai_dark.png'),
    ('settings-group-conversations-dark-en', R + 'p310_settings_group_chat_dark.png'),
    ('settings-group-this-app-dark-en', R + 'p310_settings_group_this_app_dark.png'),
    ('settings-group-help-dark-en', R + 'p310_settings_group_help_dark.png'),
    ('settings-appearance-light-en', R + 'settings_appearance_loaded_light.png'),
    ('settings-appearance-dark-en', R + 'settings_appearance_loaded_dark.png'),
    ('settings-language-light-en', R + 'settings_language_sheet_loaded_light.png'),
    ('settings-notifications-light-en', R + 'settings_notifications_loaded_light.png'),
    ('settings-privacy-light-en', R + 'settings_privacy_loaded_light.png'),
    ('settings-about-light-en', R + 'system_about_loaded_light.png'),
    ('servers-list-light-en', R + 'servers_servers_loaded_light.png'),
    ('servers-list-dark-en', R + 'servers_servers_loaded_dark.png'),
    ('servers-list-light-en-tablet', R + 'servers_servers_loaded_1280x800_light.png'),
    ('servers-remove-light-en', R + 'servers_servers_remove_server_sheet_confirm_light.png'),
    ('servers-add-light-en', R + 'add_server_manual_light.png' if False else 'test/goldens/add_server_manual_light.png'),
    ('servers-add-failed-light-en', 'test/goldens/add_server_failed_light.png'),
    ('onboarding-welcome-light-en', R + 'servers_servers_welcome_first_run_light.png'),
    ('onboarding-welcome-dark-en', R + 'servers_servers_welcome_first_run_dark.png'),
    ('conversations-list-light-en', G + 'chats_home_mixed_light.png'),
    ('conversations-list-dark-en', G + 'chats_home_mixed_dark.png'),
    ('conversations-empty-dark-en', G + 'chats_home_empty_dark.png'),
    ('conversations-list-light-en-tablet', G + 'chats_home_running_1280x800_dark.png'),
]

def label(img, text):
    band = Image.new('RGB', (img.width, 28), (32, 32, 32))
    ImageDraw.Draw(band).text((8, 7), text, fill=(240, 240, 240))
    out = Image.new('RGB', (img.width, img.height + 28), (32, 32, 32))
    out.paste(band, (0, 0)); out.paste(img.convert('RGB'), (0, 28))
    return out

os.makedirs(os.path.join(here, 'pairs'), exist_ok=True)
made = 0
for native, flutter in PAIRS:
    n = os.path.join(here, 'native', native + '.png')
    f = os.path.join(root, flutter)
    if not (os.path.isfile(n) and os.path.isfile(f)):
        print('skip', native, '(missing', 'native' if not os.path.isfile(n) else flutter, ')')
        continue
    a = label(Image.open(n), 'NATIVE  ' + native)
    b = label(Image.open(f), 'FLUTTER  ' + os.path.basename(flutter))
    h = max(a.height, b.height)
    pair = Image.new('RGB', (a.width + b.width + 12, h), (90, 90, 90))
    pair.paste(a, (0, 0)); pair.paste(b, (a.width + 12, 0))
    pair.save(os.path.join(here, 'pairs', native + '.png'), optimize=True)
    made += 1
print('pairs written:', made)
