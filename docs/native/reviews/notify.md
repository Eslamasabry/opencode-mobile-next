Verdict: **NO-GO** — credential exposure, request-replacement races, and incorrect reply-watch lifecycle behavior.

1. **BLOCKER — Credentials survive notification redaction.** [NotificationText.kt:22](/home/eslam/Storage/Code/wt/native-reviews/src-notify/native/android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/NotificationText.kt:22)  
   **Problem:** Prefixed environment-variable names evade the secret matcher; single-quoted flag values containing spaces are only partially masked.  
   **Evidence:** Replaying all ten source regexes with synthetic inputs produces:
   - `DB_PASSWORD=shortpass deploy` → unchanged.
   - `OPENAI_API_KEY=shortvalue deploy` → unchanged.
   - `login --password 'red blue'` → `login --password ••• blue'`.

   These strings can reach permission bodies and finished-reply previews, violating plan 16 §1.8.6/B-SEC-106. The existing `OPENAI_API_KEY` test uses an independently recognizable `sk-` token, concealing the environment-name defect.  
   **Fix:** Match sensitive environment-name suffixes and consume complete single- and double-quoted values. Add fixtures whose secrets match no provider-token pattern, plus rendered-notification assertions.

2. **MAJOR — Completing an older answer can delete or overwrite a newer request.** [NotificationActionBody.kt:59](/home/eslam/Storage/Code/wt/native-reviews/src-notify/native/android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/NotificationActionBody.kt:59)  
   **Problem:** Answer completion dismisses by chat-level notification key, without checking which request currently occupies that key. Failure similarly reposts the old request unconditionally.  
   **Evidence:** Begin answering request A; request B appears in the same chat and replaces `input:<profile>:<session>`; A completes. Lines 60/64 cancel B’s notification. If A fails, `repost(tap)` replaces B with an Open intent targeting A. Exact network request IDs do not protect the notification afterward.  
   **Fix:** Make dismissal and fallback replacement conditional on the displayed request identity/generation. Test both interleavings with a suspended answer and the real shade.

3. **MAJOR — Reply-watch shutdown removes another owner’s status row.** [ReplyWatch.kt:261](/home/eslam/Storage/Code/wt/native-reviews/src-notify/native/android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/ReplyWatch.kt:261)  
   **Problem:** Every service-backed finish uses `STOP_FOREGROUND_REMOVE`, regardless of other owners of notification 4747.  
   **Evidence:** Start reply watch, then start the phone server while the reply runs. When the app becomes visible or the idle grace expires, `finish()` removes their shared row. The original `joined` flag only describes startup; it cannot handle an owner arriving later. Plan 11 §9.2 explicitly requires owner arbitration and `STOP_FOREGROUND_DETACH` when another owner remains.  
   **Fix:** Acquire/release the `reply` owner through `StatusOwners`; remove only for the last owner, otherwise detach and render the remaining owners. Test server/live/sign-in ownership changes during an active watch.

4. **MAJOR — Dismissal is scoped to an entire chat and stops unrelated watches.** [ReplyWatch.kt:184](/home/eslam/Storage/Code/wt/native-reviews/src-notify/native/android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/ReplyWatch.kt:184)  
   **Problem:** `dismissedRuns` stores session IDs, while any dismissal immediately stops the whole watch without validating the current target or ownership.  
   **Evidence:** A later turn in the same chat remains unpromotable at line 257. A delayed dismissal for another session also calls `finish()`, terminating alert collection for currently progressing chats. The S11-9 test changes from `ses_1` to `ses_2` to represent “another run”; it never tests two turns in one chat and explicitly dismisses unrelated `ses_9` without checking that watching continues. This misses plan 11 §§9.3/10.3’s “for this run” requirement.  
   **Fix:** Carry a profile/conversation/turn identity through content and delete intents. Apply dismissal only to that run, preserve other runs, and check status ownership before ending the service.

5. **MAJOR — Status actions lose the connection and navigation target.** [ShadeActions.kt:109](/home/eslam/Storage/Code/wt/native-reviews/src-notify/native/android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/ShadeActions.kt:109), [StatusRenderer.kt:64](/home/eslam/Storage/Code/wt/native-reviews/src-notify/native/android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/StatusRenderer.kt:64)  
   **Problem:** `ReplyStopper` receives only `SessionId`; status content and stop intents carry no profile. The content/Open intents carry neither conversation identifier.  
   **Evidence:** Core represents a conversation as `ConversationRef(profileId, sessionId)`, and `ConnectionRegistry.graphFor` selects by profile. Two profiles can expose the same session ID; this seam cannot unambiguously select the live connection or `oneShot(profile, …)`. Separately, after Stop fails, the comment promises that tapping the row opens that chat, but `openIntent()` supplies only an optional launch action.  
   **Fix:** Carry `ConversationRef` through status content, stop commands, and activity intents. Reject unresolvable legacy targets. Test duplicate session IDs across profiles and decode the failed-stop fallback intent.

6. **MAJOR — Finished notices can disappear before the collector subscribes.** [ReplyWatch.kt:200](/home/eslam/Storage/Code/wt/native-reviews/src-notify/native/android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/ReplyWatch.kt:200)  
   **Problem:** Starting a watch schedules a coroutine that subsequently schedules the alert collector. There is no subscription acknowledgement or reconciliation of unshown alerts.  
   **Evidence:** The supplied `ReplyRunSource` contract permits the tests’ `MutableSharedFlow(extraBufferCapacity = 8)` with zero replay. Emit `finished` immediately after `onStartCommand()` and before `runCurrent()`: there are no subscribers, so the event is discarded. The finished-notice tests always drain the scheduler before emitting, hiding this window. The ten-second grace cannot recover the discarded event.  
   **Fix:** Establish subscription before acknowledging watch readiness and reconcile pending, unshown moments across service startup. Add an emission-before-subscription regression test.

Static test-sensitivity checks covered three existing tests: removing duplicate admission breaks `NotificationActionBodyTest`’s B-CHAT-074 assertions; removing the finished timeout breaks `AlertShadeTest`’s B-ANDR-040 timeout assertion; retaining an older pending status breaks `StatusRowRulesTest`’s B-ANDR-032 trailing-content assertion. These checks were analytical, not executed mutations.

Read-only review; no Gradle builds or Kotlin tests ran. The regex replay used Python. Direct notification imports were checked against the range-end core contracts; compilation remains unverified.