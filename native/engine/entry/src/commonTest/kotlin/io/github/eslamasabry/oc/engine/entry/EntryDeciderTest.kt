package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.entry.EntryDecision.Drop
import io.github.eslamasabry.oc.engine.entry.EntryDecision.Go
import io.github.eslamasabry.oc.engine.entry.EntryDecision.Wait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** 07 §7.4–§7.9 as tables over [EntryDecider.decide]. */
class EntryDeciderTest {
    private fun decide(request: EntryRequest, world: EntryWorld = readyWorld()) = EntryDecider.decide(request, world, T0)

    private fun launch(action: LaunchAction, origin: EntryOrigin = EntryOrigin.Untrusted) =
        EntryRequest.Launch(action, T0, origin)

    private val ref = ConversationRef(P1, S1)
    private val resolve = EntryEffect.ResolveRef(ref, Fallback(EntryTarget.Land(null), EntryNotice.ConversationNotFound))

    // ---- rule 3 ----

    @Test
    fun `B-ANDR-073 nothing is decided before bootstrap and the migration gate open`() {
        val requests =
            listOf(
                launch(LaunchAction.Connect),
                EntryRequest.Link("opencode-mobile://session?profile=p1&session=ses_1", T0),
                EntryRequest.SharedText("hello", T0),
                EntryRequest.TilePreferences(TileId.Pause, T0),
            )
        val notOpen =
            listOf(
                readyWorld().copy(bootstrap = BootstrapPhase.Starting),
                readyWorld().copy(gateOpen = false),
                readyWorld().copy(bootstrap = BootstrapPhase.SafeMode),
                readyWorld().copy(bootstrap = BootstrapPhase.Blocked),
            )
        for (world in notOpen) {
            for (request in requests) assertEquals(Wait(WaitReason.Bootstrap), decide(request, world), "$request in $world")
        }
    }

    // ---- 07 §7.4 ----

    @Test
    fun `B-ANDR-028 launcher actions map to their targets`() {
        val table =
            listOf(
                LaunchAction.Connect to Go(EntryTarget.Servers(null)),
                LaunchAction.Activity to Go(EntryTarget.NeedsYou),
                LaunchAction.Usage to Go(EntryTarget.Usage(UsageSectionTarget.Plans)),
                LaunchAction.UsageSettings to Go(EntryTarget.UsageWidgetSettings),
                LaunchAction.BackgroundSettings to Go(EntryTarget.Settings(SettingsSectionTarget.Background)),
                LaunchAction.Linux to Go(EntryTarget.ThisPhone),
                LaunchAction.NewTask to Go(EntryTarget.NewConversation(P1)),
            )
        for ((action, expected) in table) assertEquals(expected, decide(launch(action)), "$action")
    }

    @Test
    fun `B-ANDR-028 connect opens Servers even with no server and leaves the connection untouched`() {
        val none = readyWorld(links = emptyMap(), active = null, saved = emptySet())
        assertEquals(Go(EntryTarget.Servers(null)), decide(launch(LaunchAction.Connect), none))
    }

    @Test
    fun `B-ANDR-028 actions needing a server end on Servers with a plain notice when none is active`() {
        val empty = readyWorld(links = emptyMap(), active = null, saved = emptySet())
        val saved = readyWorld(links = emptyMap(), active = null)
        for (action in listOf(LaunchAction.Activity, LaunchAction.Usage, LaunchAction.NewTask)) {
            assertEquals(Go(EntryTarget.Servers(null), notice = EntryNotice.AddServerFirst), decide(launch(action), empty))
            assertEquals(Go(EntryTarget.Servers(null), notice = EntryNotice.ChooseServerFirst), decide(launch(action), saved))
        }
    }

    @Test
    fun `B-ANDR-028 linux is dropped off Android`() {
        assertEquals(Drop(), decide(launch(LaunchAction.Linux), readyWorld().copy(isAndroid = false)))
    }

    @Test
    fun `B-ANDR-028 phone setup notifications open progress or ready from setup state, else nothing`() {
        val table =
            mapOf(
                PhoneSetupStatus.Running to Go(EntryTarget.PhoneSetup(SetupStage.Progress)),
                PhoneSetupStatus.Stopped to Go(EntryTarget.PhoneSetup(SetupStage.Progress)),
                PhoneSetupStatus.DoneReadyNotShown to Go(EntryTarget.PhoneSetup(SetupStage.Ready)),
                PhoneSetupStatus.Done to Drop(),
                PhoneSetupStatus.None to Drop(),
            )
        for ((status, expected) in table) {
            // Never waits for a connection.
            val world = readyWorld(links = mapOf(P1 to LinkState.Connecting)).copy(phoneSetup = status)
            assertEquals(expected, decide(launch(LaunchAction.PhoneSetup), world))
            assertEquals(expected, decide(launch(LaunchAction.PhoneSetupDone), world))
        }
    }

    @Test
    fun `B-ANDR-028 internal new task waits for the active connection then creates one empty session`() {
        val internal = launch(LaunchAction.NewTask, EntryOrigin.Internal)
        assertEquals(Wait(WaitReason.Connection, P1), decide(internal, readyWorld(links = mapOf(P1 to LinkState.Connecting))))
        val go = assertIs<Go>(decide(internal))
        assertEquals(listOf(P1), go.effects.filterIsInstance<EntryEffect.CreateSession>().map { it.profileId })
        assertEquals(
            Go(EntryTarget.Servers(ServersRequestTarget.Edit(P1, focusPassword = true)), notice = EntryNotice.SignInAgain),
            decide(internal, readyWorld(links = mapOf(P1 to LinkState.NeedsReentry))),
        )
        assertEquals(
            Go(EntryTarget.Servers(null), notice = EntryNotice.ConnectionFailed),
            decide(internal, readyWorld(links = mapOf(P1 to LinkState.Failed))),
        )
    }

    @Test
    fun `B-ANDR-028 untrusted arrivals only navigate - no connect and no create for any action or state`() {
        val actions =
            EntryValidators.launchActions.values +
                UsageTargetKind.entries.map { LaunchAction.UsageTarget(it, "anthropic", P2) } +
                UsageTargetKind.entries.map { LaunchAction.UsageTarget(it, "anthropic", P1) }
        val worlds =
            LinkState.entries.flatMap { state ->
                listOf(
                    readyWorld(links = mapOf(P1 to state, P2 to state)),
                    readyWorld(links = mapOf(P1 to state, P2 to state), active = P2),
                )
            }.map { it.copy(usageProviders = setOf(UsageProvider(P1, "anthropic", false), UsageProvider(P2, "anthropic", false))) }
        for (world in worlds) {
            for (action in actions) {
                val decision = decide(launch(action, EntryOrigin.Untrusted), world)
                val effects = (decision as? Go)?.effects.orEmpty()
                assertTrue(
                    effects.none { it is EntryEffect.Connect || it is EntryEffect.CreateSession },
                    "$action in ${world.links} gave $effects",
                )
            }
        }
    }

    // ---- B-NAV-077 usage widget rows ----

    private val usageWorld =
        readyWorld(links = mapOf(P1 to LinkState.Ready)).copy(
            usageProviders =
                setOf(
                    UsageProvider(P1, "anthropic", isClaudeOnThisPhone = false),
                    UsageProvider(P1, "claude", isClaudeOnThisPhone = true),
                    UsageProvider(P1, "glm", isClaudeOnThisPhone = false),
                    UsageProvider(P2, "openai", isClaudeOnThisPhone = false),
                ),
        )

    private fun usage(kind: UsageTargetKind, provider: String, profile: ProfileId = P1) =
        launch(LaunchAction.UsageTarget(kind, provider, profile))

    @Test
    fun `B-NAV-077 usage rows open provider sign-in, agent sign-in for Claude on this phone, or Read usage from`() {
        assertEquals(Go(EntryTarget.ProviderSignIn(P1, "anthropic")), decide(usage(UsageTargetKind.SignIn, "anthropic"), usageWorld))
        assertEquals(Go(EntryTarget.AgentSignIn("claude")), decide(usage(UsageTargetKind.SignIn, "claude"), usageWorld))
        assertEquals(Go(EntryTarget.ProviderSignIn(P1, "glm")), decide(usage(UsageTargetKind.AddKey, "glm"), usageWorld))
        assertEquals(Go(EntryTarget.UsageReadFrom(P1, "anthropic")), decide(usage(UsageTargetKind.SetUp, "anthropic"), usageWorld))
    }

    @Test
    fun `B-NAV-077 a deleted server or unknown provider opens Plans with a notice, never the wrong page`() {
        val gone = Go(EntryTarget.Usage(UsageSectionTarget.Plans), notice = EntryNotice.UsageRowGone)
        assertEquals(gone, decide(usage(UsageTargetKind.SignIn, "anthropic", ProfileId("deleted")), usageWorld))
        assertEquals(gone, decide(usage(UsageTargetKind.SignIn, "unknown"), usageWorld))
    }

    @Test
    fun `B-NAV-077 another server's row asks to connect when untrusted and connects first when internal`() {
        val untrusted = decide(usage(UsageTargetKind.SignIn, "openai", P2), usageWorld)
        assertEquals(Go(EntryTarget.ConfirmConnect(P2, EntryTarget.ProviderSignIn(P2, "openai"))), untrusted)
        val internal = decide(launch(LaunchAction.UsageTarget(UsageTargetKind.SignIn, "openai", P2), EntryOrigin.Internal), usageWorld)
        val go = assertIs<Go>(internal)
        assertEquals(EntryTarget.ProviderSignIn(P2, "openai"), go.target)
        assertEquals(listOf(P2), go.effects.filterIsInstance<EntryEffect.Connect>().map { it.profileId })
    }

    // ---- 07 §7.5 ----

    @Test
    fun `B-ANDR-027 a pinned session opens on the active server only and never switches silently`() {
        assertEquals(Go(EntryTarget.Chat(ref), listOf(resolve)), decide(EntryRequest.PinnedSession(P1, S1, T0)))
        assertEquals(Drop(EntryNotice.OtherServer), decide(EntryRequest.PinnedSession(P2, S1, T0)))
        assertEquals(
            Wait(WaitReason.Connection, P1),
            decide(EntryRequest.PinnedSession(P1, S1, T0), readyWorld(links = mapOf(P1 to LinkState.Connecting))),
        )
    }

    // ---- 07 §7.6 ----

    private fun link(raw: String) = EntryRequest.Link(raw, T0)

    @Test
    fun `B-NAV-061 a plain session link opens the chat on the active server, asks to switch for another saved one`() {
        assertEquals(Go(EntryTarget.Chat(ref), listOf(resolve)), decide(link("opencode-mobile://session?profile=p1&session=ses_1")))
        assertEquals(
            Go(EntryTarget.SwitchServerThen(P2, SwitchReason.Link, EntryTarget.Chat(ConversationRef(P2, S1)))),
            decide(link("opencode-mobile://session?profile=p2&session=ses_1")),
        )
    }

    @Test
    fun `B-NAV-061 a link to a server not saved here keeps the link for the address sheet`() {
        assertEquals(
            Go(EntryTarget.SessionAddress(0), listOf(EntryEffect.ParkSessionLink(ProfileId("other"), S1))),
            decide(link("opencode-mobile://session?profile=other&session=ses_1")),
        )
    }

    @Test
    fun `B-NAV-061 re-entry needed sends the person to edit the server with the password focused`() {
        assertEquals(
            Go(EntryTarget.Servers(ServersRequestTarget.Edit(P1, focusPassword = true)), notice = EntryNotice.SignInAgain),
            decide(link("opencode-mobile://session?profile=p1&session=ses_1"), readyWorld(links = mapOf(P1 to LinkState.NeedsReentry))),
        )
    }

    @Test
    fun `B-NAV-025 team links open Chats with their filter until AI Team ships`() {
        assertEquals(Go(EntryTarget.NeedsYou), decide(link("opencode-mobile://team/gate/p1/bead:w-1")))
        assertEquals(Go(EntryTarget.Conversations(ConversationsFilter.Running)), decide(link("opencode-mobile://team/run/p1/run_1")))
    }

    @Test
    fun `B-NAV-064 malformed links are consumed and nothing happens`() {
        for (raw in listOf("opencode-mobile://session?profile=p1", "opencode-mobile://session/v2?o=x", "opencode-mobile://elsewhere")) {
            assertEquals(Drop(), decide(link(raw)), raw)
        }
    }

    // ---- 07 §7.7 ----

    @Test
    fun `B-ANDR-075 a share waits for a connected server then creates a session holding an unsent draft`() {
        val share = EntryRequest.SharedText("hello", T0)
        assertEquals(Wait(WaitReason.Connection, P1), decide(share, readyWorld(links = mapOf(P1 to LinkState.Connecting))))
        val go = assertIs<Go>(decide(share))
        assertEquals(listOf(EntryEffect.CreateSession::class, EntryEffect.SeedDraft::class), go.effects.map { it::class })
        assertEquals(
            Go(EntryTarget.Servers(null), notice = EntryNotice.ShareNeedsServer),
            decide(share, readyWorld(links = emptyMap(), active = null)),
        )
    }

    // ---- 07 §7.8 ----

    private fun alert(
        kind: AlertKind,
        profile: ProfileId? = P1,
        token: MonitorToken? = null,
        reply: Boolean = false,
        origin: EntryOrigin = EntryOrigin.Untrusted,
    ) = EntryRequest.AlertOpen(kind, profile, S1, token, reply, T0, origin)

    @Test
    fun `B-NAV-005 notification taps open the typed destination, Reply focuses the composer and Open does not`() {
        val table =
            listOf(
                alert(AlertKind.Permission) to EntryTarget.Chat(ref, landOn = LandOn.OldestRequest),
                alert(AlertKind.Question) to EntryTarget.Chat(ref, landOn = LandOn.OldestRequest),
                alert(AlertKind.Error) to EntryTarget.Chat(ref, landOn = LandOn.Failure),
                alert(AlertKind.Checkin) to EntryTarget.Chat(ref),
                alert(AlertKind.Complete, reply = true) to EntryTarget.Chat(ref, focusComposer = true),
                alert(AlertKind.Complete, reply = false) to EntryTarget.Chat(ref, focusComposer = false),
            )
        for ((request, target) in table) assertEquals(Go(target, listOf(resolve)), decide(request), "${request.kind}")
    }

    @Test
    fun `B-NAV-005 a widget row on another connection is resolved, and a missing one lands instead of a broken chat`() {
        val go = assertIs<Go>(decide(alert(AlertKind.Complete, profile = P2, reply = true, origin = EntryOrigin.Internal)))
        assertEquals(EntryTarget.Land(null), go.target)
        assertEquals(
            listOf(EntryEffect.ResolveWidgetConversation(P2, S1, true, Fallback(EntryTarget.Land(null), EntryNotice.ConversationNotFound))),
            go.effects,
        )
        // The same alert from outside the app asks before switching.
        assertEquals(
            Go(EntryTarget.SwitchServerThen(P2, SwitchReason.Alert, EntryTarget.Chat(ConversationRef(P2, S1), focusComposer = true))),
            decide(alert(AlertKind.Complete, profile = P2, reply = true)),
        )
    }

    @Test
    fun `B-NAV-005 a monitored alert is revalidated and asks to switch without waiting for transport`() {
        val world = readyWorld(links = mapOf(P1 to LinkState.Connecting))
        val go = assertIs<Go>(decide(alert(AlertKind.Permission, profile = P2, token = TOKEN), world))
        assertEquals(
            EntryTarget.SwitchServerThen(P2, SwitchReason.Alert, EntryTarget.Chat(ConversationRef(P2, S1), landOn = LandOn.OldestRequest)),
            go.target,
        )
        assertIs<EntryEffect.ResolveMonitoredRequest>(go.effects.single())
    }

    @Test
    fun `B-NAV-025 team alerts open Chats with the matching filter and quota alerts open Plans`() {
        assertEquals(Go(EntryTarget.NeedsYou), decide(alert(AlertKind.TeamDecision)))
        assertEquals(Go(EntryTarget.NeedsYou), decide(alert(AlertKind.TeamReview)))
        for (kind in listOf(AlertKind.TeamRunFailed, AlertKind.TeamCompleted, AlertKind.TeamProgress)) {
            assertEquals(Go(EntryTarget.Conversations(ConversationsFilter.None)), decide(alert(kind)))
        }
        val quota = assertIs<Go>(decide(alert(AlertKind.Quota)))
        assertEquals(EntryTarget.Usage(UsageSectionTarget.Plans), quota.target)
        assertIs<EntryEffect.VerifyQuotaToken>(quota.effects.single())
        assertEquals(Drop(), decide(alert(AlertKind.Test)))
    }

    // ---- 07 §7.9 ----

    private val schedules =
        mapOf(
            "sch_1" to ScheduleInfo(P1, ProjectDir("/w/app"), morningSummary = false),
            "morning" to ScheduleInfo(P1, ProjectDir("/w/app"), morningSummary = true),
        )

    private fun scheduled(id: String, profile: ProfileId = P1, session: SessionId? = S1) =
        EntryRequest.ScheduledOpen(profile, id, session, T0)

    @Test
    fun `B-NAV-054 a scheduled run opens its conversation when reached, else the history`() {
        val world = readyWorld().copy(schedules = schedules)
        val list = EntryTarget.ScheduledPrompts(P1, ProjectDir("/w/app"), "sch_1")
        assertEquals(Go(EntryTarget.Chat(ref), listOf(EntryEffect.ResolveRef(ref, Fallback(list, null)))), decide(scheduled("sch_1"), world))
        assertEquals(Go(list), decide(scheduled("sch_1", session = null), world))
        assertEquals(Go(EntryTarget.ScheduledPrompts(P1, ProjectDir("/w/app"), "morning")), decide(scheduled("morning"), world))
    }

    @Test
    fun `B-NAV-054 a deleted schedule or a changed server opens nothing`() {
        val world = readyWorld().copy(schedules = schedules)
        assertEquals(Drop(), decide(scheduled("gone"), world))
        assertEquals(Drop(), decide(scheduled("sch_1", profile = P2), world))
    }

    @Test
    fun `B-NAV-054 the tap is held up to 30 seconds while the start-up connection settles`() {
        val world = readyWorld(links = mapOf(P1 to LinkState.Connecting)).copy(schedules = schedules)
        val request = scheduled("sch_1")
        assertEquals(Wait(WaitReason.ScheduleSettling, P1), EntryDecider.decide(request, world, T0 + 29.seconds))
        assertEquals(
            Go(EntryTarget.ScheduledPrompts(P1, ProjectDir("/w/app"), "sch_1")),
            EntryDecider.decide(request, world, T0 + 30.seconds),
        )
    }

    // ---- tiles and internal ----

    @Test
    fun `B-ANDR-028 tile long-press opens the tile's settings page`() {
        assertEquals(Go(EntryTarget.Settings(SettingsSectionTarget.Notifications)), decide(EntryRequest.TilePreferences(TileId.Attention, T0)))
        assertEquals(Go(EntryTarget.Settings(SettingsSectionTarget.Background)), decide(EntryRequest.TilePreferences(TileId.Pause, T0)))
    }
}
