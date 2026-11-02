# :feature:updates

The app's own update, as a person meets it. Port of `lib/ui/screens/settings/updates_screen.dart`,
`lib/update/app_update_notice.dart` and `lib/ui/kit/kit_update_sheet.dart`. The engine is `:engine:updates`
(`AppUpdates`).

| File | What |
|---|---|
| `presentation/UpdatesUi.kt` | `UpdatesUi` and the pure `updatesUi(state, now)` mapping for the Updates page |
| `presentation/UpdateNoticeUi.kt` | `NoticeUi` / `noticeUi(state)` (the one status line), `OfferUi` / `offerUi(state)` (the gear's sheet), `hasUpdateBadge(state)` |
| `presentation/UpdatesActions.kt` | `UpdatesActions` and the `UpdateLinks` port (release page through the link policy) |
| `presentation/UpdatesViewModel.kt` | Metro ViewModel: maps `AppUpdates.state`, marks the page open (the notice steps aside), runs commands in the app scope |
| `ui/UpdatesScreen.kt` | `UpdatesScreen(state, actions)` |
| `ui/UpdatePrompts.kt` | `UpdateNotice` / `updateStatus` (status line) and `UpdateOfferSheet` (Update now or Open Settings) |
| `UpdatesEntries.kt` | `entry<UpdatesRoute.Home>` |

## What :androidApp must provide

`UpdatesViewModel` injects `AppUpdates`, `UpdateLinks`, `AppCoroutineScope`, `kotlin.time.Clock` and `AppNavigator`.

```kotlin
@Provides @SingleIn(AppScope::class)
fun appUpdates(feed: UpdateFeed, platform: UpdatePlatform, store: UpdateStore, clock: Clock): AppUpdates =
    AppUpdates(feed, platform, store, clock, supported = true /* false where the app cannot install itself */)

@Provides
fun updateLinks(policy: ExternalLinkPolicy, opener: ExternalLinkOpener): UpdateLinks = UpdateLinks { url ->
    when (val d = policy.check(url, LinkOrigin.ServerContent)) {
        is LinkDecision.Refuse -> Outcome.Failed(Failure.Refused(/* a link refusal reason */))
        is LinkDecision.Open -> opener.open(d.link)
        is LinkDecision.AskFirst -> /* the app's ask-first confirmation, then */ opener.open(d.link)
    }
}
```

`AppUpdates` must be one app-wide instance: the page, the notice and the gear read the same state. Someone app-wide
calls `start()` once after launch and `onResumed()` on every resume (Flutter `AppUpdateNotice`); this module does not.
The shell (or :androidApp) shows `UpdateNotice(noticeUi(state)!!, …)` or passes `updateStatus(…)` into
`KitScreen(status = …)`, puts the badge on the gear from `hasUpdateBadge(state)`, and opens `UpdateOfferSheet` from
`offerUi(state)`; "Open Settings" goes to `UpdatesRoute.Home`.
