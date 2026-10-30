# Welcome is one question (2026-10-20)

Finish line: a phone with nothing saved opens on the aurora, a small wordmark,
"Where should your agent run?" and two large cards, "On this phone" (preselected,
with the setup engine's own time and download size) and "On my computer"; one
mint button at the bottom names the choice ("Set up on this phone" /
"Connect to my computer") and starts it. "Just show me" is a quiet text action.

Non-goals: the phone setup progress screen, the server editor's steps, the
saved-servers list, the live background shader.

Pick: concept A (`launch-footage/screen-concepts/b2-welcome/concept-a.png`), with
the vision page's Adopt/Reject/Remove lists: no step dots, no "You can change
this later", no invented cost.

## Technical-honesty audit (before editing)

| Element of the pick | Real source | Verdict |
| --- | --- | --- |
| Aurora behind | `KitScreen` paints `KitLiveBackground` on every page | Build: already there, nothing new. |
| Small wordmark | `KitTopBar(brand: true)` (`KitBrandMark` + product name) | Build: as today. The About icon moves into the bar's menu so the bar is the wordmark alone. |
| "Where should your agent run?" | Copy only | Build (replaces "Where does your coding agent run?"). |
| "On this phone" card | `platformCapabilities.supportsTermux` (Android) gates the phone path today | Build, same gate. Off Android only the computer card shows. |
| Its cost line ("about 6 min, 1.2 GB" in the image) | `PhoneSetup.engine.registry` → `defaultSetupSelection` → `expandSetupSelection` → `setupTotals` (`estimatedSeconds`, `downloadBytes`), the exact arithmetic of phone setup's own screen | Build with the engine's numbers: today 235 s → "About 4 minutes", 208 MB. The image's 6 min / 1.2 GB are invented and not used. The numbers are estimates, so the line says "About". |
| Can this phone run it? | `checkSetupPreflight` (ABI, RAM, free space) from `voiceDevicePlatform.getDeviceInfo` | Build: read on tap, before anything downloads. A phone that cannot (or may be slow) is sent to phone setup's own page, which already explains why and offers Open storage. |
| A setup that already started | `SetupEngine.restore` + `progress` (running / stopped / done-but-not-saved), read by `PhoneSetupWelcomeEntry` | Build: the phone card's line and the button say it ("Setup is 42% done" · "Continue setup"), instead of a separate block above the question. |
| "On my computer" card, "pair with a code" | Server editor: pairing code by QR (`supportsQrPairing`) or paste, or a typed address; Codex by address | Build: "Pair with a code, or type its address" (true on every platform; scanning is only offered where a camera path exists). |
| Preselected answer | Local UI state | Build: the phone where it exists, else the computer. |
| One main button naming the choice | `KitActionBlock` primary | Build: "Set up on this phone" runs the default setup (`SetupEngine.run`, first setup) and opens its progress; "Connect to my computer" opens the editor. |
| "Just show me" | `DemoScreen` (offline, simulated) | Build: quiet text action. |
| Termux, Customize, connect by address | Phone setup's own page (`PhoneSetupStartScreen`, "Other ways" + Customize) | Build: one quiet "More ways to set up" text action opens that page; nothing removed. |
| Move from Termux | `TermuxMigrationOffer` under a found Termux server | Kept: when OpenCode or Termux is found on the phone the welcome still leads with that server (unchanged state). |
| Report a bug, Setup guide, About | Top bar menu | Kept, all three in the menu. |
| Step dots ("1 of 3") | None: there is no fixed number of steps | Reject (vision page). |
| "You can change this later" | Settings → Servers says it | Remove (vision page). |
| Mascot/hero drawing, "Keep your work moving." paragraph | Current screen | Remove: the concept carries no hero or value paragraph; the question is the page. |

## What changed

| Before | After |
| --- | --- |
| Hero drawing, "Keep your work moving." and a value paragraph, then "Where does your coding agent run?" | One display-size question, "Where should your agent run?" (`KitHeroQuestion`) |
| Three chevron rows (phone "Recommended", then "Other ways": computer, demo); a tap went straight on | Two large answer cards (`KitChoiceCards`): this phone preselected with the engine's cost line, the computer with "Pair with a code, or type its address"; a tap only chooses |
| Phone row opened phone setup's page, whose button started setup (two screens, the promise said twice) | "Set up on this phone" reads the phone (CPU, memory, space) and, when nothing stands in the way, starts the default setup at once and opens its progress; a phone that cannot (or may be slow) goes to phone setup's page, which says why |
| A separate state block above the question for a started setup | The phone card's line says it ("Setting up · 42%", "Setup is 42% done", "Installed and ready") and the one button follows ("Show progress", "Continue setup", "Start on this phone") |
| About icon and menu in the bar, 44 dp brand mark | The wordmark alone (30 dp mark); About, Report a bug and Setup guide in the menu |
| — | "Just show me" and "More ways to set up" (phone setup's page: Termux, what to install, an address) as quiet text actions under the cards |

Found state (OpenCode or Termux already on the phone) keeps its layout: the found
server leads with Move from Termux under it, then the in-app setup, then the
computer and the demo, whose line is now one short line so both rows keep the
same height.

Unused copy removed (all seven locales and the core-keys list):
`onboardingValueTitle`, `onboardingValueBody`, `onboardingDemoNote`,
`firstRunOnComputerDetail`, `firstRunOnPhoneDetail`. New keys are translated in
all seven locales; `firstRunWhereQuestion` follows the new wording everywhere.

## Visual passes against concept A

1. `pass1/`: structure right; gaps: question too small and tight to the bar,
   quiet actions pinned under the button made the bottom block tall (at 320 dp
   2x it covered the cards), unselected card edge invisible, 66 sp question at
   2x filled the screen.
2. `pass2/`: quiet actions moved under the cards, primary alone at the thumb,
   more air above the question, visible hairline on the unselected card.
   (The question change did not apply in this pass.)
3. `pass3/`: question 48 sp (32 sp under 340 dp), text scale capped at 1.15, less
   top air at large text; Arabic rendered with the locale theme.
4. `pass4/`: the brand mark down from 44 to 30 dp so the wordmark is small as in
   the concept; final renders match pass 4 plus the found state.

Not taken from the concept: step dots, "You can change this later", the invented
"about 6 min, 1.2 GB" (the engine says about 4 minutes, 208 MB), a laptop glyph
(the app's Phosphor set has the desktop `computer` icon).

## Evidence

Final renders, 412x915 at 2x unless named, one screen each:
`/home/eslam/Storage2/launch-footage/vision-welcome/` — welcome-dark,
welcome-light, computer-chosen-dark, setup-running-dark, setup-stopped-dark,
setup-done-not-connected-dark, no-phone-path-dark, termux-found-dark,
small-320-text2-dark (320x693 at 2.0 text), arabic-rtl-dark. Capture:
`tool/capture/vision_welcome_test.dart`.

Tests run (all passed): first_run_welcome, phone_setup_welcome_entry,
first_run_computer_path, add_server_steps, add_server_flow,
server_profile_editor, termux_clarity, termux_running_server,
desktop_platform_gating, l10n_fg5_screens, l10n_coverage, l10n_new_locales,
coverage/servers_screens, coverage/servers_actions, phone_setup_start_screen,
oc2_server_discovery, kit_choice_cards, kit_hero_question, kit_manifest,
kit_draft_manifest, kit_ratchet, design_standard, ui_glossary, the six
text_scale_overflow matrix shards (Kit scenes), revamp/screen_servers_1; goldens
regenerated with TZ=UTC and viewed: kit_choice_cards, kit_hero_question,
kit_top_bar (brand), phone_setup (setup_welcome_entry), revamp screen_phone_1,
slice_r15, screen_servers_1. `flutter analyze`: no issues (after
`flutter pub get` in widgetbook/). Full suite not run (owner rule).

Already failing on the base (dd2d79bde), not touched here: `kit_motion_test`
"every kit.dart part has reduced-motion samples" and `text_scale_overflow_test`
"every exported kit part has a scene" both name only `KitPageHeading` (exported
by the Files slice without a still-motion sample or G6 scene).
