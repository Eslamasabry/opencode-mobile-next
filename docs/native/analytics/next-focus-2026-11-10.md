# Analytics for the next focus, 2026-11-10

Scope: the native app. Data: GA4 property for Flutter 2.1.5 only (no native build has reported yet), 8 to 10 Nov 2026,
88 users, 17,983 events. Read-only reports. Small numbers: read the shape, not the decimals.

## 1. What the 2.1.5 data says

| Question | Number | How sure |
|---|---|---|
| Install to first reply | 88 first opens, 61 started setup (69%), 52 finished it (59%), 47 sent a message (53%), 43 saw a first reply (49%) | per-user counts of the events; "finished" counts users with a `setup_done`, not a nested funnel |
| Who is lost | 27 of 88 never started setup; 9 more never finished; 5 more never sent; 4 more got no reply | the biggest single loss is before setup starts |
| Uninstalls | 18 `app_remove` for 88 installs in 3 days | GA event, not a funnel |
| Day-1 return | 8 Nov cohort: 14 of 26 came back the next day, 4 of 26 on day 2. The 9 Nov cohort (52) has 7 on day 1, but its day is not over | tiny cohorts, 3 days of data |
| What people do | `screen_view`: chat 7,997, conversations 2,259, setup 1,463, new chat 1,041, agents 986, settings 817, servers 440, review 340 | solid |
| Features | `feature_used`: model switch 207 events (34 users), agent switch 100 (23), server add 40 (40), file attach 29 (15), voice 16 (4), review 8 (6) | solid for the top two |
| Sessions | 171 sessions for 88 users; 150 engaged; average session 30 to 40 minutes by GA's own clock | `session_length` has no value in 2.1.5 (see section 2) |
| Where | UAE 16, Indonesia 13, Brazil 8, India 7, Iran 6, Russia 5 | first replies came mostly from Indonesia, India, Brazil, Russia |
| Smoothness (Flutter) | `jank`: 54% of events fall in `>15` percent janky, 24% in `5-15`; chat is the worst page (723 of 1,108 chat events `>15`); 120 Hz phones are 57% of events | the top bucket is saturated, so "how bad" cannot be read |
| Setup failures | 25 `setup_step_failed` (11 users), mostly `essentials`; 5 `connect_computer_chosen` | folded into `linux_setup_step` in native |
| Connection | `conn_attempt` fail `refused`: 23 events, 12 users | the phone's server not answering |

Native versus Flutter: every row above is Flutter 2.1.5 (one app version, one stream). Native has sent nothing yet, so
nothing in this document compares the two. Do not read "first replies" as the native rate.

## 2. Can today's events answer the next-focus questions

| Question | Before this change | Gap |
|---|---|---|
| Install to first reply | Seven separate events, joined by hand in a GA exploration | no single ordered funnel; no time between steps; leaving before Start is only visible as a missing event; a step with a blank parameter cannot be placed |
| First run versus returning | GA `newVsReturning` only | no way to split any report by "has had a reply yet" |
| Session depth | `session_length` (time in front) | nothing about what happened in the visit |
| Smoothness | `jank` per page kind, 4 buckets | `>15` hides everything past 15 percent; no frozen-frame signal |
| Feature appetite | `feature_used` with 8 words | native never sent model switch, agent switch, camera, git or review; only voice and file attach were wired |
| Use case (regular people) | none | the one-question survey has no event |
| Phone bridge, presentations | none | no design decision needs analytics before the features exist |

## 3. What this change adds

- `journey_milestone` (`step`, `after_range`): the funnel `opened`, `setup_opened`, `setup_started`, `setup_done`,
  `server_ready`, `chat_opened`, `first_prompt`, `first_reply`, each once per install, with the time since the person
  first agreed to usage (`under_5m`, `5_to_30m`, `30m_to_2h`, `2h_to_1d`, `1_to_3d`, `over_3d`). Derived from events the
  app already sends (`JourneyMilestones`), so no call site changed. The clock starts when usage is first on, never earlier.
- User property `journey_stage`: the furthest milestone. Splits any report into "still new" and "has had a reply".
- `session_length.turns_range`: messages answered during the visit (`0`, `1`, `2-5`, `6-20`, `>20`).
- `jank.janky_pct_range` now `<1`, `1-5`, `5-15`, `15-30`, `>30` (Flutter's `>15` is the sum of the last two) and a new
  `jank.frozen_range` (`0`, `1-2`, `3-10`, `>10` frames of 700 ms or more).
- `use_for.kind` (`everyday`, `coding`, `both`, `looking`) and `ProductUseFor.of(everyday, coding)`: the survey's answer
  as one word, sent first time and on change. The question's screen is not built, so nothing sends it yet.
- `feature_used` `server_add` is now sent when a server is added.

## 4. GA custom dimensions to register (event scope unless noted)

| Parameter | Display name | Scope | Used by |
|---|---|---|---|
| `after_range` | After range | Event | `journey_milestone` |
| `turns_range` | Turns range | Event | `session_length` |
| `frozen_range` | Frozen range | Event | `jank` |
| `journey_stage` | Journey stage | User | all events |

Already registered and reused: `step` (also carries the milestone words on `journey_milestone`), `kind` (carries
`use_for` values), `screen`, `janky_pct_range`, `feature`. Register the four before the native build ships: GA fills a
dimension only from the day it exists. The earlier proposal of five (`attempt`, `outcome`, `input`, `tools_range`,
`follow_up`) was already done on the property.
