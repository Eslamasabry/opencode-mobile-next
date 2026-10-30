# Finora showcase: what our app can learn

Source: 79 s video, about 19 distinct screens. Finora is a finance app with an AI orb
(a blue-white sphere) as its mascot, a pale blue-to-white gradient with a dot-grid
texture at the top of most screens, one dark "hero" card per page, and a floating
dark pill tab bar. Crops sit next to this file as `screen-<name>.png`.

Reading rule used throughout: our rules are no bottom nav, no glass, flat lists not
boxes, simplify, plain words, native Android, 150-250 ms motion, no new bar or row
level, main actions as visible rows, styles Solid/Soft/Brutal/Paper, light/dark, RTL.
Finora breaks several of them (bottom pill bar, frosted round buttons, card stacks),
so most of what we take is *behaviour*, not look.

---

## 1. Splash (`screen-splash.jpg`)
On it: the orb centred, soft rings around it, wordmark and tagline under it, "Bank-level
encryption" at the foot. Small floating cards (balance, spending, a tip) drift around the phone.
Good: one idea per screen; the lock line states trust in six words.
Idea: our first-run screen could carry one trust line in plain words ("Your keys stay on this phone")
under the logo. Changes: onboarding/setup first page only.
Skip: floating cards and the glow. Decoration with no job.

## 2. Onboarding (`screen-onboarding.jpg`)
On it: orb with three chat-bubble-like chips ("Where did my money go?", "Saved this month +$640",
"Food spending -12%"), headline "Meet Finora, your AI money companion", three topic chips, dots, one
Get Started button and a text link "I already have an account". Skip at top right.
Good: shows what you can ask, with real example questions, before any form. Primary and
returning-user paths are both visible, with no menu.
Idea: on our setup first page, replace the feature blurb with 2-3 real example prompts the user
could send ("Fix the failing test", "What changed today?"). Same chips we already draw above the input. Also a visible
"I already have a server" row beside the main button. Removes a paragraph of explanation.
Skip: the animated orb and floating notes.

## 3. Sign in (`screen-signin.jpg`)
On it: back, orb, "Welcome back", email and password fields, show/hide eye, Forgot link, big Sign In
next to a small biometric button, "or continue with" Google/Apple, sign-up link at foot.
Good: biometric button sits beside the main button, not on another screen; focused field gets a
clear ring; the button shows a spinner in place while working (frames after it).
Idea: servers/connections add-form: put the primary action and a "scan QR / paste link" shortcut
side by side; show progress inside the button, not a dialog. Removes the separate "connecting" screen.
Skip: social login (not relevant); decorative header.

## 4. Home / dashboard (`screen-home.jpg`)
On it: dark balance card with a hide-eye, income and expense tiles, week bar chart where today is the
dark bar, recent transactions with "See all".
Good: one big number first; hide-eye for privacy in public; today's bar highlighted; every tile is also a link.
Idea: Status/Usage and the Status 2x2 widget: lead with one big figure ("62% of plan used"), a small
7-day bar strip with today darker, one line "12% less than last week". The eye toggle fits our
Usage page for screenshots. Removes: the table of numbers.
Skip: the dark card box (flat lists rule), floating tab bar.

## 5. Transactions list (`screen-transactions.jpg`)
On it: summary card collapses to a compact one, filter chips (All/Income/Expense/Bills/Shopping), rows
grouped under TODAY / YESTERDAY, amounts right-aligned, income in green.
Good: date group headers with small caps; two-line rows (title, then place and time); chips scroll sideways and the active one is filled.
Idea: Conversations list: group under Today / Yesterday / Earlier with quiet headers, and a single row of
filter chips (All, Running, Needs you, Done). A row of chips replaces any filter menu. It does not add
a bar level if it replaces the current search/filter row; otherwise skip.
Skip: card background behind the list (flat lists).

## 6. Details (`screen-details.jpg`)
On it: icon, title, big amount, a "Completed" pill, key/value table (category, date, payment, id, note),
a dark AI line ("You spent $186 on groceries..."), delete (small, red) and Edit (wide) at the foot.
Good: key/value rows in a quiet two-column list; destructive action small, primary wide; the AI remark
is one sentence, tied to this item.
Idea: conversation details / server details: label-value rows, one plain-words status pill, one wide
primary at the foot with a small red delete beside it. A single AI-written line of "what happened" at the top
of a finished task. Removes the overflow menu for delete.
Skip: dark inset box for the AI line; use plain text.

## 7. Insights (`screen-insights.jpg`)
On it: Expense/Income segmented switch, total for the period, a dotted-fill area chart, range chips (7D 1M 3M 1Y ALL),
"Spending by category" with a bar and percent per row.
Good: range chips below the chart that redraw it in place; category rows are bar + number, easy to scan; the dotted fill gives texture without gradients.
Idea: Usage page: range chips (Day / Week / Month) under one chart; rows per model/server with a thin bar and
percent. Replaces tables. The dotted fill is a flat fill, so it is allowed in Solid and Paper.
Skip: nothing major; keep chart colours inside our style tokens.

## 8. Add transaction (`screen-add-transaction.jpg`, empty sheet and filled)
On it: full-screen modal with close X, a two-way switch, a huge amount with a blinking cursor, an
"auto-detected category" hint, a 4x2 grid of category icons (one selected), date row, card row, note row, Save.
Good: the one thing that matters (amount) is huge and focused at open; everything else has a sensible default
so Save is one tap away; the app tells you it guessed ("Finora detected Food").
Idea: new-chat / new-task: open with the keyboard up and the prompt field huge; defaults for server, folder and
model shown as three plain rows underneath ("Server: Phone", "Folder: oc_app", "Model: Sonnet") that you
tap only to change. Removes the setup form. The "we guessed X" line fits our model/approval chips.
Skip: icon grid (we have no categories).

## 9. Success (`screen-success.jpg`)
On it: check in a circle, a few confetti dots, "Transaction Added!", a summary list (amount, category, date,
budget left), Back to home and a text link "Add another".
Good: confirms with facts, not just a tick; the follow-on action ("Add another") is right there.
Idea: after a permission is allowed or a task is queued: a short line in place ("Allowed: edit lib/main.dart")
with Undo, not a dialog. Removes a confirmation tap.
Skip: confetti. Our users do not need to be rewarded for approving a shell command. One calm check at most.

## 10. Scan receipt (`screen-scan-receipt.jpg`)
On it: dark camera view, corner brackets, a bright scan line sweeping, a floating pill "Whole Foods - $45.20
detected" with a small orb, mode switch Receipt / Bill / Card, gallery, shutter, confirm.
Good: live feedback in a pill that names what was found before you commit; modes as quiet text, not tabs.
Idea: our camera/attach flow and voice: a single pill above the shutter saying what was read ("Error text, 14 lines
found") before it is sent into the chat. Fits the existing chips row; no new bar.
Skip: scan-line animation unless the camera is in use anyway.

## 11. Notifications (`screen-notifications.jpg`)
On it: filter chips (All, Alerts, AI Insights, Payments), a mark-all-read tick at top right, groups TODAY / EARLIER,
rows with icon, title, time, unread dot, body text. One AI row is inverted (dark) with two inline buttons "Show me" and "Later".
Good: actions live on the row itself; unread dot; pastel tinted icons tell the type at a glance.
Idea: permission requests and "needs you" items: Allow/Reject as visible buttons on the row (we already do this in chat), and a
"Later" option that snoozes. A top-level "Needs you" list in Conversations built the same way. Mark-all-read becomes an
obvious row rather than a tick icon.
Skip: inverted dark row (box); use bold title and a dot.

## 12. Profile (`screen-profile.jpg`)
On it: avatar with initials and edit badge, name, email, two stats, then a flat list with leading icon,
label, current value on the right ("On", "Visa 4821") and a chevron; Log out in red at the foot.
Good: current value shown in the row, so you rarely need to open it; log-out is visible, not hidden.
Idea: Settings (already flagged as a mess): flat rows with the current value on the right ("Theme: Solid",
"Language: Arabic", "Servers: 2 connected"), grouped by five. Removes dozens of open-and-check taps.
In RTL the value goes to the left and the chevron flips.
Skip: stat tiles in a dark pill.

## 13. AI chat: empty, input, thinking (`screen-chat-empty.jpg`, `screen-chat-input.jpg`, `screen-chat-thinking.jpg`)
On it: empty state is the orb and "Hi Alex, let's..." typing out. Input is a rounded field with "+" at left and a round
up-arrow Send at right, with three starter chips above it ("Budget Planner", "Spending Insights", "Goal Tracker").
After send: your bubble right with edit and copy icons under it, a date divider "TODAY - Your personal finance conversation",
the agent's avatar and "Thinking..." with a shimmer sweeping over the word.
Good: starter chips disappear and are replaced by *follow-up* chips after each answer; the shimmer on "Thinking..." is one line, quiet.
Idea: we already have phrase chips and a live "Running ..." line. Take: (a) chips swap to what comes next after each
turn (our learned chips already do this); (b) a text shimmer on "Running..." instead of a spinner; (c) edit and copy under the user's
message, always visible. Removes the long-press menu discovery step.
Skip: the animated greeting, the orb avatar. Our agents have brand marks.

## 14. AI chat: answer, follow-up (`screen-chat-answer.jpg`, `screen-chat-followup.jpg`)
On it: agent answer appears in a light bubble with a rich inline card (monthly total, three category bars), then text, then copy / thumbs-up /
thumbs-down. Follow-up chips change ("Create a budget", "How can I save more?", "Analyze..."). A numbered answer fades in line by line.
Good: structured result inside the message (a mini chart, not prose); feedback icons under the message; new chips per answer.
Idea: tool results in our turns: when a step is a table or test summary, show a small inline summary
(3 bars, "12 passed, 2 failed") under the folded "work" line. Folded line stays one line; the summary replaces opening the fold.
Skip: bubble background for the agent (we use flat turns); thumbs unless we have somewhere to send them.

## 15. Budget planner (`screen-budget.jpg`)
On it: dark hero with total, spent/remaining, a "4 days to go" line; Category Budgets with Edit, each row a bar that turns red with
"Almost over limit" / blue "On track"; at the foot one AI tip with an Ask button.
Good: status in words *and* colour ("Almost over limit"); the AI tip is a single line with one button, at the bottom.
Idea: Usage (plan limits): each limit as a row, bar coloured by state, words under it ("Almost at your limit - resets in 4 days").
Colour never alone, which helps accessibility. A single advice line at the foot
("Switch this chat to a smaller model to last until reset") with one button. Removes a help page.
Skip: hero card.

## 16. Savings goals (`screen-goals.jpg`)
On it: ring showing total saved of target, three small stats (active goals, overall %, this month), goal rows
with icon, name, target date, percent on the right, bar, "saved / to go" under it, a wide Create button.
Good: one ring for the whole, bars for each part; two numbers per row, one left one right.
Idea: Status page and Status widget (2x2): one ring for "plan used" with the number in the middle. Rows below it:
server name, bar, "running / idle". Ring is native-looking and draws well at 2x2.
Skip: three stat tiles.

## 17. Closing loop (profile fade, splash returns)
The video ends by dissolving the last screen into the splash. Nothing to take.

---

## Top 8 ideas worth mocking

| # | Idea | Screen | What it removes or simplifies |
|---|------|--------|------------------------------|
| 1 | Settings rows show their current value on the right, grouped in five | Profile | Opening a page just to check a setting; fewer screens |
| 2 | Usage: one big number, range chips under one chart, rows with bar plus state in words | Insights, Budget | Tables and a help page; colour-only state |
| 3 | New chat opens with the prompt big and three default rows (server, folder, model) tappable to change | Add transaction | The new-chat form and any "setup" step |
| 4 | Setup first page shows 2-3 real example prompts and a visible "I already have a server" row | Onboarding | The explainer paragraph; a hidden returning-user path |
| 5 | Conversations grouped Today/Yesterday/Earlier with one chip row (All, Running, Needs you, Done) | Transactions | Filter menu; hunting for unfinished chats (only if it replaces the search row, no new level) |
| 6 | Status page and 2x2 widget: one ring plus one line per server | Savings goals | A list of numbers; works at widget size |
| 7 | Inline result summary under the folded work line (counts, small bars) | Chat answer | Opening the fold to learn the outcome |
| 8 | Allow/Reject rows with a "Later" snooze, and a one-line in-place confirmation with Undo | Notifications, Success | Confirmation dialogs; a separate Needs-you screen |

Next in line: text shimmer on "Running ..." and always-visible edit/copy under user turns (chat), live "what was read" pill before attaching a photo.

## Motion (inferred from consecutive 1 s frames, so durations are estimates)

- **Splash to onboarding:** floating cards drift out first, the orb moves up and shrinks, the headline and chips fade up from below (about 400-600 ms in Finora; we should use 200 ms and one move).
- **Cards and rows entering:** staggered. Each transaction row slides in from the right with its text fading in just after the amount; we saw rows caught half-way with price at the edge. Takeaway: a 30-40 ms stagger per row, first 5 rows only, 200 ms each, fade plus 12 dp slide. Skip when reduced motion is on.
- **Number changes:** balance and amounts count into place; cents are drawn in a lighter grey than dollars ("45" bold, ".20" grey). The lighter-cents style is free and works in all four styles.
- **Bar chart:** bars grow upward from the baseline, today's bar darker.
- **Sign in:** button turns to a spinner inside itself and the rest of the form fades to half; no overlay.
- **Sheet presentation:** Add Transaction rises from the bottom as a full-height modal over a blurred, dimmed page (we skip the blur; keep the scrim, a standard Android bottom-sheet rise, 250 ms).
- **Orb:** idles by slowly rotating its inner colours; when AI is working it flies to the floating pill's right end (the dock) and shows a number badge. Skip; an equivalent for us is the existing running dot on the row.
- **Success:** the ring scales in with a small overshoot, then the check draws; confetti dots burst outward and fade within ~1 s; summary rows fade up one by one. For us: just the ring and check, 200 ms, no confetti.
- **Scan:** a bright line sweeps top to bottom in a loop; a pill pops in from below when text is recognised.
- **Notifications and chips:** filter chip highlight slides to the tapped chip and the list cross-fades (about 150 ms). Tap feedback is a small grey circle ripple under the finger (we saw it on chips and the quick-reply chips), which is native Android.
- **Chat:** your bubble pops in at the right; "Thinking..." has a light sweep across the letters; the answer is revealed progressively (text fades in line by line, the inline chart card fills bars last); quick-reply chips fade out and new ones fade in.
- **Page transition to a new section:** the top gradient and hero card stay and the content cross-fades, so it feels like one surface. We have no bottom nav, so the equivalent is shared-axis transitions between a list and its detail (Android's default Material shared axis, 200 ms).
- **Ending:** the screen dissolves into the splash. Nothing to take.

Reduced motion: all of the above must collapse to a plain fade when the system asks for it.
