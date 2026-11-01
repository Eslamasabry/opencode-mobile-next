# Linux manager, part 3 of 3: UI (2026-10-31)

This part draws the jobs in [1-use-cases.md](1-use-cases.md) on the architecture in [2-architecture.md](2-architecture.md).

**Rules applied:**
- simplify and natural;
- obvious options first, with no ⋯ menu for a main action;
- no new UI levels;
- actions name their target;
- no raw errors;
- teach what is hidden;
- kit only;
- component max widths and the 1.3× chrome text cap;
- Android-native feel;
- micro motion of 150–250 ms.

Built from `lib/ui/kit/` per `docs/design/design-standard.md`.

## 1. Information architecture

### 1.1 Page map today

```
This phone
├─ (answer) · Restart · Stop
├─ Update OpenCode
├─ Running on this phone ─────────────► Running on this phone (all app processes, 5 s)
├─ Add tools
├─ Agents on this phone
├─ Storage (fact)
├─ Linux ─────────────────────────────► Linux on this phone
│                                        ├─ Running ─► Linux › Running (Linux processes, 10 s)   ← duplicate
│                                        ├─ Ports ───► Ports
│                                        ├─ Storage ─► Storage
│                                        ├─ Packages ► Packages (installed + install by exact name)
│                                        ├─ Repair (question)
│                                        └─ Reset (typed question)
├─ Export projects ───────────────────► Export projects
├─ Keep running in the background
├─ Terminal
└─ Remove …
```

### 1.2 Page map after

```
This phone                                   (rows unchanged except ★)
├─ Running on this phone ────────────────────► Running on this phone  (one page; ★ adds a Services group)
├─ Linux ★ "3 services running · Cool" ──────► Linux
│                                              ├─ Vitals strip (page head, not a bar)
│                                              ├─ Services ★ ───────► Services
│                                              │                       ├─ Add a service ► Add a service
│                                              │                       │                  ├─ SSH / Syncthing / Forgejo / code-server ► app sheet
│                                              │                       │                  ├─ A command of your own ► form
│                                              │                       │                  └─ Docker, Pi-hole and others ► explainer sheet
│                                              │                       └─ <service> ► Service (status, acts, log)
│                                              ├─ Running now ──────► Running on this phone (same page as above)
│                                              ├─ Ports
│                                              ├─ Packages ★ ───────► Packages (one search: installed, then available)
│                                              ├─ Storage
│                                              ├─ Backups ★ ────────► Backups ─► backup sheet (Restore)
│                                              ├─ Repair Linux
│                                              └─ Reset Linux ★ (question offers "Back up first")
├─ Export projects ✕ moved into Backups as "Projects only" (Q6)
└─ … unchanged
```

| Change | Why | Rule |
|---|---|---|
| Linux › Running is removed. Its row opens Running on this phone. | Two pages listed the same processes, with different refresh rates and stop rules. | simplify |
| The vitals strip is the Linux page's head, and the This phone Linux row's supporting line carries the summary | Status goes into a part that already exists, not a new bar | no new UI levels |
| Services is the first row on Linux | It's the daily job (J1–J4) | obvious options first |
| "Add a service" is the first row on Services, and the catalog lives under it | No hub row for something you use once. Adding happens where services are listed. | simplify, obvious options first |
| Package search goes into the Packages search field, and the "Install by name" dialog goes | One field answers both "do I have it" and "can I get it" | simplify |
| Backups is a Linux row, and Reset offers it | Backups are needed right before risky acts | actions live on their thing's page |
| Export projects moves into Backups (Q6) | One place for "keep a copy" | simplify |

There are no ⋯ menus. Every main act is a visible row or chip. Long-press on a service row opens the same acts as its page, as a shortcut, and is taught (§5).

## 2. Screens and states

Every screen uses `KitScreen(width: reading)`. Loading is `KitSkeletonRows`. A missing Linux shows `KitStateView.missing` ("Linux isn't set up on this phone" with **Set up Linux**). After 8 s without an answer the screen says so (design standard). Errors are a `KitNotice` in words, with **Try again** and the technical text under **Details** (redacted).

### 2.1 Linux (hub) with the vitals strip
Render: `1-linux-hub.png`

| State | What shows |
|---|---|
| Loading | Strip cells show `—` with their words. The rows show at once (they're static). |
| Normal | Four cells: heat word with the battery temperature, battery, phone memory free, Linux CPU. One line under them in words: "Linux uses 1.4 GB of memory. Nothing is heating the phone." |
| Warm or hot | The word changes ("Warm", "Hot", "Too hot"). The line names the heaviest program: "Hot. node (vite) is using 180% CPU." The line is tappable and opens Running now. No cell turns amber: heat is a degraded state, not "needs you" (LOOK-4, gate G17); the word carries it. As built in S2a (2026-11-01). |
| Battery or phone memory not available (before S2b) | Those cells drop out, never `0`. S2a as built: heat word (Android's thermal status), phone memory free (the app reads the live `/proc/meminfo` `MemAvailable` on Android's side), Linux CPU (the native process list). S2b adds battery level, charging and temperature. |
| Row supporting lines | Services: "3 running: SSH, Syncthing, my-bot", or "my-bot stopped after crashing" when something failed. Storage: "Linux takes 4.2 GB · 610 MB can be freed". Backups: "Last backup 3 days ago", or "No backup yet". |
| Package job or backup job running | The existing `LinuxJobPanel` sits above the rows (`linux_manager_common.dart:255`), extended to backup and restore jobs |

### 2.2 Services
Render: `2-services.png`

| State | What shows |
|---|---|
| Empty | The "Add a service" row, then a short calm text: "Nothing runs on its own yet. Add SSH, Syncthing or your own command and it keeps running, even after a crash or a restart." |
| List | One list ordered by urgency (owner rule: no state sections): failed, port busy, retrying, running, off. Each row: icon, name, the state in words in supporting text, and one chip (**Start** or **Stop**, read out as "Start my-bot"). |
| Starting or stopping | The chip shows its working state. The supporting text reads "Starting…" or "Stopping…". |
| Retrying | "Crashed (exit 1). Trying again in 8 s". The countdown updates every second while shown. |
| Failed | Leading `KitStatusMark` in the needs-you tone, then "Stopped after crashing 5 times · 10:42". The chip is **Start**. |
| Port busy | "Port 8384 is in use by node (vite)". The row opens the service page. |
| Near the process limit (≥ 28) | A `KitNotice`: "Linux is running 29 programs. Android may close the app above 32." Action: **Open Running now** |
| Supervisor didn't start | A `KitNotice` failure: "Services couldn't start." Actions: **Try again**, **Report a problem**. Details hold the log tail. |
| Screen-off note (until S5 or Q2) | Caption: "While the screen is off, services may pause unless the phone is charging." |

Long-press a row for **Restart**, **Stop** or **Start**, and **Remove** (a sheet of `KitRow`s, the same acts as the page).

### 2.3 Service page with its log
Render: `3-service-detail.png`

| Part | Content |
|---|---|
| Status row | "Running for 3 h 12 min" / "Came back once today after a crash, at 07:15" |
| Acts (rows, in this order) | **Open Syncthing** (only with a port; opens the dev preview), **Start Syncthing** or **Stop Syncthing** ("It stays stopped until you start it again"), **Restart Syncthing** (only while running), **Edit command** (own commands only), **Remove Syncthing** (last; asks first, and says the log and settings go and the program stays installed) |
| Log | `KitLogPanel(searchable: true, live: running)` with redaction, folded at about 12 lines. It opens unfolded when the service failed. Marker lines become words: "Started", "Crashed (exit 1)", "Trying again in 4 s", "Gave up after 5 crashes". |
| States | Off: the status reads "Off" and **Start Syncthing** is the first act. Failed: the status is "Stopped after crashing 5 times. It last ended with exit 1 at 10:42." and the log is open. Port busy: the status names the program holding the port, plus **Open Running now**. Removing: the rows are disabled and the status says "Removing…". |

### 2.4 Add a service (catalog) and app sheet
Render: `4-add-service.png`

| Part | Content |
|---|---|
| App rows | SSH, Syncthing, Forgejo, code-server. Each has one plain line on what it's for, and a fit tag at the end: **Works** (done tone), **Heavy** (plain), **Not on this phone** (plain, row disabled with the reason) or **Added** (already installed). |
| A command of your own | Opens a form (§2.5) |
| Docker, Pi-hole and others | Opens a sheet. One line each: "Docker and k3s need parts of Linux that Android keeps from apps." "Pi-hole needs port 53. Apps can only use ports from 1024 up." "Home Assistant can't reach Bluetooth or USB from here." |
| App sheet | Title, two sentences on what it is, facts as `KitKeyValue` (Memory, Space, Port, Who can reach it), one primary **Install Syncthing**. Progress shows in the sheet (job panel). When done: "Syncthing is installed. Start it now?" with **Start Syncthing**. |
| SSH sheet extras | A `KitField(kind: mono, multiline)` "Your laptop's public key". The address appears as `KitTechnicalValue` ("ssh -p 8022 root@100.101.7.12", with copy). Without Tailscale: "Turn on Tailscale on this phone first. SSH only listens there." with **Open Tailscale**. |
| States | Loading fit (RAM and ABI): the tags show nothing until they're known. Install failed: a notice in words plus Details. Offline: "Installing needs the internet." |

### 2.5 A command of your own (form page)

The fields are KitFields:
- **Name** (`text`). The helper text gives the rule.
- **Command** (`mono`).
- **Folder** (`path`). It defaults to the open project, with a picker row.
- **Port, optional** (`decimal`), helper "Lets you open it and stops two programs fighting over one port".
- `KitSwitchRow` **Restart when it crashes**, on by default.

The primary button is **Save and start**. Validation shows inline, per field: "That name is used by Syncthing", "Ports below 1024 aren't allowed on Android", "Port 4097 belongs to OpenCode".

### 2.6 Backups
Render: `5-backups.png`

| Part | Content |
|---|---|
| What to back up | `KitChoiceList.single`: **Setup** (packages, tools, services and settings files, with an estimate), **Setup and projects** (the default), **Projects only** (moved from Export projects), **Full copy of Linux** (from S7; "stops services while it copies") |
| Where | Before S7: each backup asks with Android's "Save to". From S7: one folder row, "Saved in Download › OpenCode backups", with **Change folder**. |
| In this folder (S7) | Rows with kind, date and size. Tap one for a sheet with **Restore from this backup** and what it brings back. |
| Restore from a file | A row, visible before S7 too. It opens Android's file picker. |
| Primary | **Back up setup and projects**. The label follows the choice. |
| States | Estimating: sizes show "Measuring…". Running: the job panel with the percentage, "Keeps going if you leave this page", and **Stop backing up**. Done: "Backup saved: 1.0 GB in Download › OpenCode backups". Failed: words plus Details. No space: "There isn't room for this backup on the place you chose." |
| Restore question | `showKitConfirm` with consequences: kept (projects that exist, your sign-ins), replaced (listed dotfiles get a `.before-restore` copy), added (packages, services). After a full copy: "Linux will be replaced by the copy from 28 Oct. Services stop while it restores." |
| Reset question | Adds a first choice, **Back up first, then reset**. Typing the word is still required to reset. |

### 2.7 Packages with search
Render: `6-package-search.png`

| State | What shows |
|---|---|
| No query | Today's installed list, unchanged |
| Typing (under 2 characters) | Only the installed list is filtered |
| Searching | Installed matches first. Below them, "Available to install" with skeleton rows. After 8 s: "Still searching…" |
| Results | "Installed" appears only when something matches. "Available to install" rows show name, what it does and size, with an **Install** chip ("Install ripgrep"). |
| No match | `KitSearchNoMatch`: "No package called ripgrep2." with **Search descriptions too** |
| Too slow (20 s) | A notice: "Search took too long." **Try again** |
| List old (over 14 days) | Caption with **Update package list** (the existing Update system job) |
| Job running | The existing job panel. Install chips are disabled with the reason "Wait for the install of htop to finish". |

### 2.8 Running on this phone (merged)
The existing page gains a **Services** group after OpenCode. Each service's processes nest under its name. **Stop** on one of them asks "Stop SSH? It stays stopped until you start it again." Stopping the process alone would only bring it back. "Left behind" stays first.

## 3. Copy

Every string goes into `lib/l10n/app_en.arb` with an Arabic entry in `app_ar.arb`.

| Key (proposed) | English |
|---|---|
| `linuxSvcTitle` | Services |
| `linuxSvcAdd` / `linuxSvcAddDetail` | Add a service / SSH, Syncthing, Forgejo, or a command of your own |
| `linuxSvcEmpty` | Nothing runs on its own yet. Add SSH, Syncthing or your own command and it keeps running, even after a crash or a restart. |
| `linuxSvcRunningFor` | Running for {duration} |
| `linuxSvcRetrying` | Crashed (exit {code}). Trying again in {seconds} s |
| `linuxSvcGaveUp` | Stopped after crashing {count} times · {time} |
| `linuxSvcPortBusy` | Port {port} is in use by {program} |
| `linuxSvcStart` / `linuxSvcStop` / `linuxSvcRestart` / `linuxSvcRemove` | Start {name} / Stop {name} / Restart {name} / Remove {name} |
| `linuxSvcStopDetail` | It stays stopped until you start it again |
| `linuxSvcKeepsRunning` | A service you start keeps running: it comes back after a crash and when the app or the phone restarts, until you stop it. |
| `linuxSvcScreenOff` | While the screen is off, services may pause unless the phone is charging. |
| `linuxSvcNearLimit` | Linux is running {count} programs. Android may close the app above 32. |
| `linuxSvcCouldNotStart` | Services couldn't start. |
| `linuxFitWorks` / `linuxFitHeavy` / `linuxFitNotHere` / `linuxFitAdded` | Works / Heavy / Not on this phone / Added |
| `linuxVitalCool` / `Warm` / `Hot` / `TooHot` | Cool / Warm / Hot / Too hot |
| `linuxVitalsSummary` | Linux uses {memory} of memory. Nothing is heating the phone. |
| `linuxVitalsHeavy` | {word}. {program} is using {percent}% CPU. |
| `linuxBackupTitle` / `linuxBackupNow` | Backups / Back up {what} |
| `linuxBackupSaved` | Backup saved: {size} in {place} |
| `linuxBackupNoRoom` | There isn't room for this backup on the place you chose. |
| `linuxRestoreFromFile` | Restore from a file |
| `linuxResetBackupFirst` | Back up first, then reset |
| `linuxPkgSearch` / `linuxPkgAvailable` / `linuxPkgSlow` | Search packages / Available to install / Search took too long. |
| `linuxWhyNotTitle` | Docker, Pi-hole and others |

**Error vocabulary.** Every `LinuxServiceProblem` gets one sentence plus a way forward:
- `portTaken`: "Port 8384 is used by Syncthing. Pick another port."
- `reservedPort`: "Port 4097 belongs to OpenCode. Pick another port."
- `tooMany`: "You can keep up to 16 services. Remove one first."
- `supervisorDown`: "Services couldn't start." with Try again
- `writeFailed`: "Couldn't save that. The phone may be out of space." with Open Storage

The exit code and log tail go under Details only.

**Arabic notes:**
- Program names, commands, ports, paths and log lines stay left-to-right inside RTL rows. Use `KitLtr` and the mono field kinds, which already force LTR.
- Durations and times go through `intl` with Arabic digits as the locale gives them. Don't splice numbers into strings.
- `{name}` placeholders sit where Arabic grammar wants them, since actions put the object first. Translators get the full sentence with the placeholder, never fragments.
- "SSH", "Syncthing", "Forgejo", "code-server", "Docker" and "Tailscale" stay in Latin script.
- The fit tags need short Arabic words (one word each). The kit's chip cap of 320 dp and ellipsis rule apply. Check them at 2× text.

## 4. Kit parts

### 4.1 Existing parts used

| Part | Where |
|---|---|
| `KitScreen`, `KitTopBar`, `KitRowGroup`, `KitRow` (+ `chip`, `onLongPress`), `KitChevron`, `KitSectionLabel` | every page |
| `KitStatusMark` | the failed or needs-you service row |
| `KitStatusTag` (done / failed tones) | fit tags. Done = Works; the plain "failed" tone = Heavy or Not on this phone. That matches its "nothing to act on" meaning, so no new part. |
| `KitLogPanel` (`searchable`, `live`, `ended`) | service log |
| `KitNotice`, `KitStateView` (`.missing`, `.error`, calm), `KitSkeletonRows`, `KitSearchNoMatch` | states |
| `KitSearchField` | Packages |
| `KitChoiceList.single` | Backups "What to back up" |
| `KitField` (`text`, `mono`, `path`, `decimal`), `KitSwitchRow` | own-command form, SSH key |
| `KitSheet`, `showKitConfirm` + `KitConsequence` | app sheet, explainer, restore, remove |
| `KitKeyValue`, `KitTechnicalValue` | app facts, SSH address |
| `KitProgressView`, `LinuxJobPanel` pattern | jobs |
| `KitTip` | long-press teaching |

### 4.2 New part: `KitVitalsStrip` (lib/ui/kit/kit_vitals_strip.dart)

```dart
class KitVital {
  const KitVital({required this.value, required this.word,
    required this.semanticsLabel});
  final String value;          // "34 °C", "71%", "2.1 GB"
  final String word;           // "Cool", "Charging", "Memory free"
  final String semanticsLabel; // "Heat: cool, 34 degrees"
}

class KitVitalsStrip extends StatelessWidget {
  const KitVitalsStrip({super.key, required this.vitals, this.summary,
    this.onSummaryTap, this.loading = false, this.stripKey});
  final List<KitVital> vitals;   // 1–4; unknown readings are left out, never "0"
  final String? summary;         // one sentence under the cells
  final VoidCallback? onSummaryTap; // "open Running now" when it names a program
  final bool loading;            // cells show "—" with their words
}
```

| Contract | Value |
|---|---|
| Max width | reading (720 dp, `KitMaxWidth.reading`). Cells share the width equally. |
| Capacity | 1–4 cells. Value: one line, no ellipsis (values are short by construction). Word: one line, ellipsized. Summary: up to 3 lines, then ellipsized. |
| Text scale | Values and words are chrome, capped at 1.3× (`KitText.appScaler(max: 1.3)`). The summary is reading text and follows the system size. At 2× system text, or under 340 dp wide, the cells wrap into a 2 × 2 grid. |
| States | loading, normal, partial (fewer cells), summary tappable. No amber tone (dropped in S2a: G17 keeps attention for "needs you"). At most four cells is an assert. |
| Semantics | One node per cell with its `semanticsLabel`. The summary is a button when `onSummaryTap` is set. Not a live region (it updates every 5 s). |
| RTL | The cell order mirrors. Values with units keep their internal order. |
| Motion | A value change cross-fades in 150 ms (`AnimatedSwitcher`). A tone change animates colour over 200 ms. Under `KitMotion.reduced` both are instant. |

### 4.3 Kit parts not needed

No new row, tag or chip. Services, catalog, backups and search are all `KitRow` with `chip` or `trailing` (render check below). That keeps `test/kit_ratchet_test.dart` (G16) from growing.

**Render finding to fix in the kit, not on screens.** In `2-services.png` and `6-package-search.png` the `KitRow.chip` ink touches the screen's end edge at 412 dp. Check `_KitRowChipSlot`'s end inset against `tokens.gutter` before S1b.

## 5. Motion and teaching

| Moment | Motion (150–250 ms, framework APIs, `KitMotion.reduced` respected) |
|---|---|
| A service changes phase | The supporting text cross-fades (`AnimatedSwitcher`, 150 ms). The chip swaps Start ⇄ Stop with a 150 ms fade and scale from 0.96. |
| A row moves (failed rises to the top) | `KitAnimatedRows` moves it over 200 ms. Under reduced motion it jumps. |
| A service is added or removed | The row grows or shrinks in height over 200 ms with a fade |
| Job progress | `KitProgressView`, as today |
| Vitals change | See 4.2 |
| Press | Android ripple, `InkSparkle` where the kit uses it. Stop and Remove give `KitHaptics.commit` on confirm. Predictive back on every page. |

| Hidden thing | Teaching (registered in the tip registry, in en and ar, listed under Help › Tips) |
|---|---|
| Long-press a service for Restart and Remove | `KitTip` on the first service row, shown the second time Services opens with at least one service. At most twice. "Touch and hold a service to restart or remove it." The visible path is the service page. |
| Services stop when the screen is off (until S5) | Not a tip. It's the caption on Services (§2.2), because it changes behaviour. |
| The vitals line opens Running now | No tip. It's styled as a link when tappable. |

## 6. Concept renders

These are throwaway widget renders (`tool/capture/zz_linuxplan_scratch_test.dart`, never committed) of existing kit parts with fake data, light Solid, 412 × 915 dp at 2×, rendered on the build server. The vitals strip is a stand-in for `KitVitalsStrip`. The top bars show no Back button because each render is a lone route.

| File | Look at |
|---|---|
| `/home/eslam/Storage2/wt/shots-2026-10-31/linux-plan/1-linux-hub.png` | The vitals strip as the page head; Services first; Backups row; Repair and Reset apart |
| `/home/eslam/Storage2/wt/shots-2026-10-31/linux-plan/2-services.png` | One list by urgency (failed my-bot first); named Start/Stop chips; the keep-running sentence |
| `/home/eslam/Storage2/wt/shots-2026-10-31/linux-plan/3-service-detail.png` | Status in words; acts that name Syncthing; live log with a warning line |
| `/home/eslam/Storage2/wt/shots-2026-10-31/linux-plan/4-add-service.png` | Fit tags (Works, Heavy); your own command; the "why not" row |
| `/home/eslam/Storage2/wt/shots-2026-10-31/linux-plan/5-backups.png` | Choice of what to back up; folder row and list (S7 state); primary at the thumb. Not drawn: "Projects only" (Q6) and the "Restore from a file" row. |
| `/home/eslam/Storage2/wt/shots-2026-10-31/linux-plan/6-package-search.png` | Available packages with size and named Install chips; "Installed" is left out when nothing installed matches; list freshness line |

## 7. Owner decisions for the owner

**Decided 2026-10-31 (owner: "all as recommended"):** one state per service; keep awake only while charging; the heat guard stops services at CRITICAL and restarts them when cool; no private SSH keys in backups; opt-in SSH, Tailscale only; Export projects moves into Backups as "Projects only"; code-server shown, tagged Heavy.

| # | Question | Decision (recommended, approved) |
|---|---|---|
| Q1 | One state per service (Start means "keep it running, also after restarts"; Stop means "off until I start it"), or a separate "Start with Linux" switch? | One state, as rendered |
| Q2 | "Keep the phone awake while services run": off, on, or only while charging? | Only while charging |
| Q3 | Should the thermal guard stop services at CRITICAL and start them again when cool, like AI Team? | Yes |
| Q4 | Private SSH keys in setup backups? | No, for now |
| Q5 | An SSH listener (Tailscale only, opt-in) while the FGS says "on this device only": OK? | Yes. Revisit if we ship on Play. |
| Q6 | Move Export projects from This phone into Backups as "Projects only"? | Yes |
| Q7 | Show code-server at all (Heavy, 64-bit only)? | Yes, with its honest tag |
